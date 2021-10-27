package net.qiujuer.lesson.sample.client;


import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutionException;

public class TCPClient extends Connector {

    public TCPClient(SocketChannel socket) throws IOException {
        setup(socket);
    }

    public void exit() {
       CloseUtils.close(this);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        System.out.println("连接已关闭");
    }

    public static TCPClient startWith(ServerInfo info) throws IOException {
        SocketChannel channel = SocketChannel.open();
//        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(Inet4Address.getByName(info.getAddress()), info.getPort()));


        System.out.println("已发起服务器连接，并进入后续流程～");
        System.out.println("客户端信息：" + channel.getRemoteAddress());
        System.out.println("服务器信息：" + channel.getRemoteAddress());

        try {
            return new TCPClient(channel);
        } catch (Exception e) {
            System.out.println("连接异常");
            CloseUtils.close(channel);
        }

        return null;
    }
}
