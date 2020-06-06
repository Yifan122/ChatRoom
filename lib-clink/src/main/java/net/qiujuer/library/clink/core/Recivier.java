package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;

public interface Recivier extends Closeable {
    boolean receiveAsync(IoArgs.IoArgsEventListener listener) throws IOException;
}
