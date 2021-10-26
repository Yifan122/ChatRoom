package net.qiujuer.lesson.sample.server;

import net.qiujuer.lesson.sample.server.handle.ClientHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPServer implements ClientHandler.ClientHandlerCallBack {
    private final int port;
    private ClientListener listener;
    private List<ClientHandler> clientHandlerList = new ArrayList<>();
    private final ExecutorService forwardingMsgThreadPool;
    private Selector selector;
    private ServerSocketChannel server;

    public TCPServer(int port) {
        this.port = port;
        forwardingMsgThreadPool = Executors.newSingleThreadExecutor();
    }

    public boolean start() {
        try {
            this.selector = Selector.open();
            this.server = ServerSocketChannel.open();
            // 设为非堵塞
            server.configureBlocking(false);
            // 绑定本地
            server.bind(new InetSocketAddress(port));
            // 注册到selector上
            server.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("服务器信息：" + server.getLocalAddress().toString());


            ClientListener listener = new ClientListener();
            this.listener = listener;
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

        synchronized (TCPServer.this) {
            for (ClientHandler clientHandler : clientHandlerList) {
                try {
                    clientHandler.exit();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        clientHandlerList.clear();
        forwardingMsgThreadPool.shutdown();
    }

    public synchronized void broadcast(String str) {
        for (ClientHandler clientHandler : clientHandlerList) {
            clientHandler.send(str);
        }
    }

    @Override
    public synchronized void onSelfClosed(ClientHandler handler) throws IOException {
        handler.exit();
        clientHandlerList.remove(handler);
    }

    @Override
    public void onMsgArrived(final String str, final ClientHandler handler) {
        forwardingMsgThreadPool.submit(() -> {
            synchronized (TCPServer.this) {
                for (ClientHandler clientHandler : clientHandlerList) {
                    if (clientHandler != handler) {
                        clientHandler.send(str);
                    }
                }
            }
        });

    }

    private class ClientListener extends Thread {
        private boolean done = false;

        @Override
        public void run() {
            super.run();

            System.out.println("服务器准备就绪～");
            // 等待客户端连接
            do {
                // 得到客户端
                Socket client;
                try {
                    if (selector.select() == 0) {
                        if (done) {
                            break;
                        }
                        continue;
                    }

                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        if(done) {
                            break;
                        }

                        SelectionKey key = iterator.next();
                        iterator.remove();

                        // 检查key是否是需要关注的状态
                        if(key.isAcceptable()) {
                            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                            // 非堵塞状态下拿到客户端连接
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
            selector.wakeup();
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
