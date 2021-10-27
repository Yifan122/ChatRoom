package net.qiujuer.lesson.sample.server.handle;


import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.*;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler extends Connector{
    private final ClientHandlerCallBack clientHandlerCallBack;

    public ClientHandler(SocketChannel socketChannel, ClientHandlerCallBack clientHandlerCallBack) throws IOException {
        this.clientHandlerCallBack = clientHandlerCallBack;
        System.out.println("新客户端连接：" + socketChannel.getRemoteAddress());

        setup(socketChannel);
    }

    public void exit() throws IOException {
        CloseUtils.close(this);
        System.out.println("客户端已退出：" + channel.getRemoteAddress());

    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        exitBySelf();
    }

    @Override
    protected void onReceiveNewMessage(String str) {
        super.onReceiveNewMessage(str);
        clientHandlerCallBack.onMsgArrived(str, this);
    }


    private void exitBySelf() {
        try {
            exit();
            clientHandlerCallBack.onSelfClosed(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public interface ClientHandlerCallBack {
        void onSelfClosed(ClientHandler handler) throws IOException;
        void onMsgArrived(String str, ClientHandler handler);
    }

}
