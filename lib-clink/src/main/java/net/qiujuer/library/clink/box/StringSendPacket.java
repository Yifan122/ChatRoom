package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.SendPacket;

public class StringSendPacket extends SendPacket {
    protected final byte[] bytes;

    public StringSendPacket(String msg) {
        bytes = msg.getBytes();
    }

    @Override
    public byte[] bytes() {
        return bytes;
    }

}
