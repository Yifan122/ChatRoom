package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.SendPacket;

import java.io.IOException;

public class StringSendPacket extends SendPacket {
    private final byte[] bytes;

    public StringSendPacket(String msg) {
        bytes = msg.getBytes();
    }

    @Override
    public byte[] bytes() {
        return bytes;
    }

    @Override
    public void close() throws IOException {

    }
}
