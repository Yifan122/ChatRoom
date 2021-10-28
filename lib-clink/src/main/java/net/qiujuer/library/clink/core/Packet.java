package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;

public abstract class Packet<T extends Closeable> implements Closeable {
    protected byte type;
    protected long length;
    private T stream;

    public byte type() {return type;}

    public long length() {return length;}

    public final T open() {
        if (stream == null) {
            stream = createStream();
        }

        return stream;
    }

    @Override
    public final void close() throws IOException {
        if (stream != null) {
            closeStream(stream);
            stream = null;
        }
    }

    protected abstract T createStream();

    protected void closeStream(T stream) throws IOException {
        stream.close();
    }
}
