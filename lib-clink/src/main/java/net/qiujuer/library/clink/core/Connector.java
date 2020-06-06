package net.qiujuer.library.clink.core;

import java.nio.channels.SocketChannel;
import java.util.UUID;

public class Connector {
    private UUID key = UUID.randomUUID();
    private SocketChannel channel;
    private Sender sender;
    private Recivier recivier;
}
