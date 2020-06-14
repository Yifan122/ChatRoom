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
        private Object attach;

        @Override
        public final void run() {
            canProviderOutput(attach);
        }

        public final void setAttach(Object attach) {
            this.attach = attach;
        }

        public final <T> T getAttach() {
            T attach = (T) this.attach;
            return attach;
        }

        public abstract void canProviderOutput(Object attach);
    }
}
