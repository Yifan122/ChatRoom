package net.qiujuer.library.clink.core.impl;

import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketChannelAdapter implements Sender, Receiver, Closeable {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final OnChannelStatusChangedListener listener;

    private final IoProvider.HandleOutputCallBack outputCallBack = new IoProvider.HandleOutputCallBack() {
        @Override
        public void canProviderOutput(Object attach) {
            if (isClosed.get()) {
                return;
            }

            IoArgs args = getAttach();
            IoArgs.IoArgsEventListener listener = sendIoEventListener;

            listener.onStarted(args);

            try {
                if (args.writeTo(channel) > 0) {
                    listener.onCompleted(args);
                } else {
                    throw new IOException("Cannot writeTo any data");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            sendIoEventListener.onCompleted(null);
        }
    };

    private IoArgs.IoArgsEventListener receiveIoEventListener;
    private IoArgs receiveArgsTemp;

    private IoArgs.IoArgsEventListener sendIoEventListener;
    private final IoProvider.HandleInputCallBack inputCallBack = new IoProvider.HandleInputCallBack() {
        @Override
        protected void canProviderInput() {
            if (isClosed.get()) {
                return;
            }
            IoArgs.IoArgsEventListener listener = SocketChannelAdapter.this.receiveIoEventListener;
            IoArgs args = receiveArgsTemp;

            listener.onStarted(args);

            // Start reading data
            try {
                if (args.readFrom(channel) > 0) {
                    listener.onCompleted(args);
                } else {
                    throw new IOException("Cannot read any data");
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider, OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        channel.configureBlocking(false);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventListener listener) {
        receiveIoEventListener = listener;
    }

    @Override
    public boolean receiveAsync(IoArgs args) throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel is closed!");
        }

        receiveArgsTemp = args;
        return ioProvider.registerInput(channel, inputCallBack);
    }

    @Override
    public boolean sendAsync(IoArgs args, IoArgs.IoArgsEventListener listener) throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel is closed!");
        }

        sendIoEventListener = listener;
        outputCallBack.setAttach(args);
        return ioProvider.registerOutput(channel, outputCallBack);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            ioProvider.unRegisterInput(channel);
            ioProvider.unRegisterOutput(channel);
        }
    }


    public interface OnChannelStatusChangedListener {
        void onChannelClosed(SocketChannel channel);
    }
}
