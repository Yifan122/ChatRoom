package net.qiujuer.library.clink.core.impl.async;

import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IoArgsEventListener;
import net.qiujuer.library.clink.core.ReceiveDispatcher;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncReceiveDispatcher implements ReceiveDispatcher {
    private final Receiver receiver;
    private final ReceivePacketCallback callback;
    private AtomicBoolean isClosed = new AtomicBoolean(false);

    private IoArgs args = new IoArgs();
    private ReceivePacket packetTemp;
    private byte[] buffer;
    private int total;
    private int position;
    private final IoArgsEventListener ioArgsEventListener = new IoArgsEventListener() {

        @Override
        public void onStarted(IoArgs args) {
            int receiverSize;
            if (packetTemp == null) {
                receiverSize = 4;
            } else {
                receiverSize = Math.min(total - position, args.capacity());
            }
            // 设置本次读取的大小
            args.limit(receiverSize);
        }

        @Override
        public void onCompleted(IoArgs args) {
            assemblePacker(args);
            registerReceive();
        }
    };

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback) {
        this.receiver = receiver;
        this.receiver.setReceiveListener(ioArgsEventListener);
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
            ReceivePacket packet = this.packetTemp;
            if (packet != null) {
                packet = null;
                CloseUtils.close(packet);
            }
        }

    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    private void registerReceive() {

        try {
            receiver.receiveAsync(args);
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void completePacket() {
        ReceivePacket receivePacket = this.packetTemp;
        CloseUtils.close(receivePacket);
        callback.onReceivePacketCompleted(receivePacket);
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
            buffer = new byte[length];
            total = length;
            position = 0;
        }

        int count = args.writeTo(buffer, 0);
        if (count > 0) {
            packetTemp.save(buffer, count);
            position += count;

            // 检查是否完成一份Packet接收
            if (position == total) {
                completePacket();
                packetTemp = null;
            }
        }
    }


}

