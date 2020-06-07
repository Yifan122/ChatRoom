package net.qiujuer.library.clink.core;

import java.io.IOException;

public class IoContext {
    private static IoContext INSTANCE;
    // ioProvider 针对所有连接
    private final IoProvider ioProvider;

    private IoContext(IoProvider ioProvider) {
        this.ioProvider = ioProvider;
    }

    public static IoContext get() {
        return INSTANCE;
    }

    public static void close() throws IOException {
        if (INSTANCE != null) {
            INSTANCE.closeBySelf();
        }
    }

    public static StartedBoot setup() {
        return new StartedBoot();
    }

    public IoProvider getIoProvider() {
        return ioProvider;
    }

    public void closeBySelf() throws IOException {
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
