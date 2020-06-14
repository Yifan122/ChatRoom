package net.qiujuer.library.clink.core.impl.async;

import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendDispatcher;
import net.qiujuer.library.clink.core.SendPacket;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSendDispatcher implements SendDispatcher {
    private final Sender sender;
    private final Queue<SendPacket> packageQueue = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean isSending = new AtomicBoolean();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private IoArgs ioArgs = new IoArgs();
    private SendPacket packetTemp;

    private int total;
    private int position;
    private final IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {

        }

        @Override
        public void onCompleted(IoArgs args) {
            // 继续发送当前包
            sendCurrentPacket();
        }
    };

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
    }

    @Override
    public void send(SendPacket packet) {
        boolean offer = packageQueue.offer(packet);
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
        IoArgs args = ioArgs;

        args.startWriting();

        if (position >= total) {
            sendNextPacket();
            return;
        } else if (position == 0) {
            // 首包，需要携带长度信息
            args.writeLength(total);
        }

        byte[] bytes = packetTemp.bytes();
        // 写入数据到IoArgs 中
        int count = args.readFrom(bytes, position);
        position += count;

        // 完成封装
        args.finishWriting();

        try {
            sender.sendAsync(args, ioArgsEventListener);
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            isSending.set(false);
            SendPacket packet = this.packetTemp;
            if (packet != null) {
                packetTemp = null;
                CloseUtils.close(packet);
            }
        }
    }
}
