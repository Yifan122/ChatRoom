package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.SendPacket;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class FileSendPacket extends SendPacket<FileInputStream> {
    private InputStream stream;

    public FileSendPacket(File file) {
        this.length = file.length();
    }

    @Override
    protected FileInputStream createStream() {
        return null;
    }
}
