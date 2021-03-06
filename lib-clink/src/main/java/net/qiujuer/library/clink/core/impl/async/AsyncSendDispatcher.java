package net.qiujuer.library.clink.core.impl.async;

import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendDispatcher;
import net.qiujuer.library.clink.core.SendPacket;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSendDispatcher implements SendDispatcher, IoArgs.IoArgsEventProcessor {
    private final Sender sender;
    private final Queue<SendPacket> packageQueue = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean isSending = new AtomicBoolean();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private IoArgs ioArgs = new IoArgs();
    private SendPacket<?> packetTemp;
    private ReadableByteChannel packetChannel;

    private long total;
    private long position;

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
        sender.setSendListener(this);
    }

    @Override
    public void send(SendPacket packet) {
        packageQueue.offer(packet);
        if (isSending.compareAndSet(false, true)) {
            sendNextPacket();
        }

    }

    @Override
    public void cancel(SendPacket packet) {

    }

    private SendPacket takePacket() {
        SendPacket packet = packageQueue.poll();
        if (packet != null && !isSending.get()) {
            // 已经被取消了
            return takePacket();
        }
        return packet;
    }

    private void sendNextPacket() {
        SendPacket tmp = packetTemp;
        if (tmp != null) {
            // 以免内存泄漏
            CloseUtils.close(tmp);
        }
        SendPacket packet = packetTemp = takePacket();
        if (packet == null) {
            // 队列为空， 取消状态发送
            isSending.set(false);
            return;
        }

        total = packet.length();
        position = 0;
        sendCurrentPacket();

    }

    private void sendCurrentPacket() {
        if (position >= total) {
            completePacket(position == total);
            sendNextPacket();
            return;
        }
        try {
            sender.postSendAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void completePacket(boolean isSucceed) {
        SendPacket packet = this.packetTemp;
        if (packet == null) {
            return;
        }

        CloseUtils.close(packet, packetChannel);

        packetTemp = null;
        packetChannel = null;
        total = 0;
        position = 0;
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            isSending.set(false);
            //异常关闭
            completePacket(false);
        }
    }

    @Override
    public IoArgs provideIoArgs() {
        IoArgs args = ioArgs;
        if (packetChannel == null) {
            packetChannel = Channels.newChannel(packetTemp.open());
            args.limit(4);
            args.writeLength((int) packetTemp.length());
        } else {
            args.limit((int) Math.min(args.capacity(), total - position));

            try {
                int count = args.readFrom(packetChannel);
                position += count;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        return ioArgs;
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        // 继续发送当前包
        sendCurrentPacket();
    }
}
