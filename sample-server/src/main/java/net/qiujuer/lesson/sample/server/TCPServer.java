package net.qiujuer.lesson.sample.server;

import net.qiujuer.lesson.sample.server.handle.ClientHandler;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPServer implements ClientHandler.ClientHandlerCallback {
    private final int port;
    private ClientListener listener;
    private final ExecutorService msgForwardThreadPool;
    // multi-thread need synchronized
    private List<ClientHandler> clientHandlerList = new ArrayList<>();
    // NIO 选择器
    private Selector selector;
    private ServerSocketChannel server;

    public TCPServer(int port) {
        this.port = port;
        this.msgForwardThreadPool = Executors.newSingleThreadExecutor();
    }

    public boolean start() {
        try {
            // 打开选择器
            this.selector = Selector.open();
            this.server = ServerSocketChannel.open();
            // 设置为非堵塞
            server.configureBlocking(false);
            // 绑定本地端口
            server.socket().bind(new InetSocketAddress(port));
            // 注册客户端到达的监听
            server.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("服务器信息：" + server.getLocalAddress().toString());


            ClientListener listener = this.listener = new ClientListener();
            listener.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void stop() {
        if (listener != null) {
            listener.exit();
        }

        CloseUtils.close(server);
        CloseUtils.close(selector);

        synchronized (TCPServer.this) {
            for (ClientHandler clientHandler : clientHandlerList) {
                clientHandler.exit();
            }
        }

        clientHandlerList.clear();
        msgForwardThreadPool.shutdownNow();
    }

    public synchronized void broadcast(String str) {
        for (ClientHandler clientHandler : clientHandlerList) {
            clientHandler.send(str);
        }
    }

    @Override
    public synchronized void onSelfClosed(ClientHandler handler) {
        clientHandlerList.remove(handler);
    }

    @Override
    public synchronized void onNewMessageArrive(ClientHandler clientHandler, String msg) {
        // forward msg
        msgForwardThreadPool.execute(() -> {
            for (ClientHandler handler : clientHandlerList) {
                if (handler == clientHandler)
                    continue;

                handler.send(msg);
            }
        });
    }

    private class ClientListener extends Thread {
        private boolean done = false;

        @Override
        public void run() {
            super.run();

            System.out.println("服务器准备就绪～");
            Selector selector = TCPServer.this.selector;
            // 等待客户端连接
            do {
                try {
                    // select() 是一个堵塞状态
                    if (selector.select() == 0) {
                        if (done) {
                            break;
                        }
                        continue;
                    }

                    // 轮询得到selectedKey
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();

                        // 检查一下Key的状态是否是acceptable 的
                        if (key.isAcceptable()) {
                            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                            // 非堵塞状态下拿到socket
                            SocketChannel socketChannel = serverSocketChannel.accept();

                            try {
                                // 客户端构建异步线程
                                ClientHandler clientHandler = new ClientHandler(socketChannel,
                                        TCPServer.this);
                                clientHandlerList.add(clientHandler);
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.out.println("客户端连接异常：" + e.getMessage());
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } while (!done);

            System.out.println("服务器已关闭！");
        }

        void exit() {
            done = true;
            // 唤醒当前的堵塞状态
            selector.wakeup();
        }
    }
}
