package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.nio.channels.SocketChannel;

// 观察者模式
public interface IoProvider extends Closeable {
    boolean registerInput(SocketChannel channel, HandleInputCallBack callBack);

    boolean registerOutput(SocketChannel channel, HandleOutputCallBack callBack);

    void unRegisterInput(SocketChannel channel);

    void unRegisterOutput(SocketChannel channel);

    abstract class HandleInputCallBack implements Runnable {
        @Override
        public final void run() {
            canProviderInput();
        }

        protected abstract void canProviderInput();
    }

    abstract class HandleOutputCallBack implements Runnable {

        @Override
        public final void run() {
            canProviderOutput();
        }

        public abstract void canProviderOutput();
    }
}
