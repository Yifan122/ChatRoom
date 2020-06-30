package net.qiujuer.lesson.sample.client;


import net.qiujuer.common.util.FileUtil;
import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class TCPClient extends Connector {
    private final File cachePath;

    public TCPClient(SocketChannel socketChannel, File cachePath) throws IOException {
        this.cachePath = cachePath;
        this.setUp(socketChannel);
    }

    public static TCPClient startWith(ServerInfo info, File cachePath) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();

        // 连接本地，端口2000；超时时间3000ms
        socketChannel.connect(new InetSocketAddress(Inet4Address.getByName(info.getAddress()), info.getPort()));

        System.out.println("已发起服务器连接，并进入后续流程～");
        System.out.println("客户端信息：" + socketChannel.getLocalAddress());
        System.out.println("服务器信息：" + socketChannel.getRemoteAddress());

        try {
            return new TCPClient(socketChannel, cachePath);
        } catch (Exception e) {
            System.out.println("连接异常");
            CloseUtils.close(socketChannel);
        }
        return null;
    }

    @Override
    protected File createNewReceiveFile() {
        return FileUtil.createRandomTemp(cachePath);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        System.out.println("连接已关闭，无法读取数据！");
    }

    @Override
    protected void onReceivedPacket(ReceivePacket packet) {
        super.onReceivedPacket(packet);
        if (packet.type() == Packet.TYPE_MEMORY_STRING) {
            String string = (String) packet.entity();
            System.out.println(key.toString() + ":" + string);
        }
    }

    public void exit() {
        CloseUtils.close(this);
    }
}
