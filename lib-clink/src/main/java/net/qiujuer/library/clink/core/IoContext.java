package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;

public class IoContext implements Closeable {
    private static IoContext INSTANCE;
    // ioProvider 针对所有连接
    private final IoProvider ioProvider;

    private IoContext(IoProvider ioProvider) {
        this.ioProvider = ioProvider;
    }

    public static IoContext get() {
        return INSTANCE;
    }

    public static StartedBoot setup() {
        return new StartedBoot();
    }

    @Override
    public void close() throws IOException {
        ioProvider.close();
    }

    public static class StartedBoot {
        private IoProvider ioProvider;

        private StartedBoot() {

        }

        public StartedBoot ioProvider(IoProvider ioProvider) {
            this.ioProvider = ioProvider;
            return this;
        }

        public IoContext start() {
            INSTANCE = new IoContext(ioProvider);
            return INSTANCE;
        }
    }
}
