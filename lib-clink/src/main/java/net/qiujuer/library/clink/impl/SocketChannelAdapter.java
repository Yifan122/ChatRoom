package net.qiujuer.library.clink.impl;

import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketChannelAdapter implements Sender, Receiver, Cloneable {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final OnChannelStatusChangedListener listener;

    private IoArgs.IoArgsEventProcessor receiveIoEventProcessor;
    private IoArgs.IoArgsEventProcessor sendIoEventProcessor;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider,
                                OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        channel.configureBlocking(false);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventProcessor processor) {
        receiveIoEventProcessor = processor;
    }

    @Override
    public boolean postReceiveAsync(IoArgs args) throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel is closed!");
        }

        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public void setSendListener(IoArgs.IoArgsEventProcessor processor) {
        sendIoEventProcessor = processor;
    }

    @Override
    public boolean postSendAsync() throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel is closed!");
        }

        // 当前发送的数据附加到回调中
        return ioProvider.registerOutput(channel, outputCallback);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            // 解除注册回调
            ioProvider.unRegisterInput(channel);
            ioProvider.unRegisterOutput(channel);
            // 关闭
            CloseUtils.close(channel);
            // 回调当前Channel已关闭
            listener.onChannelClosed(channel);
        }
    }

    private final IoProvider.HandleInputCallback inputCallback = new IoProvider.HandleInputCallback() {
        @Override
        protected void canProviderInput() {
            if (isClosed.get()) {
                return;
            }

            IoArgs.IoArgsEventProcessor processor = receiveIoEventProcessor;
            IoArgs args = processor.provideIoArgs();


            try {
                // 具体的读取操作
                if (args.readFrom(channel) > 0) {
                    // 读取完成回调
                    processor.onConsumeCompleted(args);
                } else {
                    processor.onConsumeFailed(args, new IOException("Cannot readFrom any data!");
                }
            } catch (IOException ignored) {
                CloseUtils.close(SocketChannelAdapter.this);
            }


        }
    };


    private final IoProvider.HandleOutputCallback outputCallback = new IoProvider.HandleOutputCallback() {
        @Override
        protected void canProviderOutput() {
            if (isClosed.get()) {
                return;
            }

            IoArgs.IoArgsEventProcessor processor = sendIoEventProcessor;
            IoArgs args = processor.provideIoArgs();

            try {
                // 具体的读取操作
                if (args.writeTo(channel) > 0) {
                    // 读取完成回调
                    processor.onConsumeCompleted(args);
                } else {
                    processor.onConsumeFailed(args, new IOException("Cannot write any data!"));
                }
            } catch (IOException ignored) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };



    public interface OnChannelStatusChangedListener {
        void onChannelClosed(SocketChannel channel);
    }
}
