package net.qiujuer.library.clink.core.impl.async;

import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.ReceiveDispatcher;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncReceiveDispatcher implements ReceiveDispatcher, IoArgs.IoArgsEventProcessor {
    private final Receiver receiver;
    private final ReceivePacketCallback callback;
    private AtomicBoolean isClosed = new AtomicBoolean(false);

    private IoArgs ioArgs = new IoArgs();
    private ReceivePacket<ByteArrayOutputStream> packetTemp;

    private WritableByteChannel packetChannel;
    private long total;
    private long position;

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback) {
        this.receiver = receiver;
        this.receiver.setReceiveListener(this);
        this.callback = callback;
    }

    @Override
    public void start() {
        registerReceive();
    }

    @Override
    public void stop() {

    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            completePacket(false);
        }

    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    private void registerReceive() {

        try {
            receiver.postReceiveAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void completePacket(boolean isSucceed) {
        ReceivePacket packet = this.packetTemp;
        CloseUtils.close(packet);
        packetTemp = null;

        WritableByteChannel channel = this.packetChannel;
        CloseUtils.close(channel);
        packetChannel = null;

        if (packet != null) {
            callback.onReceivePacketCompleted(packet);
        }
    }

    /**
     * 解析数据到packet
     *
     * @param args
     */
    private void assemblePacker(IoArgs args) {
        if (packetTemp == null) {
            int length = args.readLength();
            packetTemp = new StringReceivePacket(length);
            packetChannel = Channels.newChannel(packetTemp.open());

            total = length;
            position = 0;
        }

        try {
            int count = args.writeTo(packetChannel);
            position += count;

            // 检查是否完成一份Packet接收
            if (position == total) {
                completePacket(true);
                packetTemp = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            completePacket(false);
        }
    }


    @Override
    public IoArgs provideIoArgs() {
        IoArgs args = ioArgs;
        int receiverSize;
        if (packetTemp == null) {
            receiverSize = 4;
        } else {
            receiverSize = (int) Math.min(total - position, args.capacity());
        }
        // 设置本次读取的大小
        args.limit(receiverSize);
        return args;
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        assemblePacker(args);
        registerReceive();
    }
}

