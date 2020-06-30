package net.qiujuer.library.clink.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public class IoArgs {
    private int limit = 256;
    private ByteBuffer buffer = ByteBuffer.allocate(limit);

    public int readFrom(ReadableByteChannel channel) throws IOException {
        startWriting();

        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.read(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        finishWriting();

        return bytesProduced;
    }

    public int writeTo(WritableByteChannel channel) throws IOException {
        buffer.limit(limit);
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.write(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        return bytesProduced;
    }

    public int readFrom(SocketChannel channel) throws IOException {
        startWriting();

        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.read(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        finishWriting();

        return bytesProduced;
    }

    public int writeTo(SocketChannel channel) throws IOException {
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.write(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        return bytesProduced;
    }

    public void writeLength(int total) {
        startWriting();
        buffer.putInt(total);
        finishWriting();
    }

    public int readLength() {
        return buffer.getInt();
    }

    public int capacity() {
        return buffer.capacity();
    }

    public void startWriting() {
        buffer.clear();
        buffer.limit(limit);
    }

    /**
     * 设置单次写操作的最大区间
     *
     * @param limit 区间大小
     */
    public void limit(int limit) {
        this.limit = limit;
        // set limit
        this.buffer.limit(limit);
    }

    public void finishWriting() {
        // enable reading
        buffer.flip();
        buffer.limit(limit);
    }

    public interface IoArgsEventProcessor {
        IoArgs provideIoArgs();

        void onConsumeFailed(IoArgs args, Exception e);

        void onConsumeCompleted(IoArgs args);
    }
}
