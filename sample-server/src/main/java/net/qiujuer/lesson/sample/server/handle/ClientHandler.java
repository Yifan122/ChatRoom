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

public class ClientHandler {
    private final Connector connector;
    private final SocketChannel socketChannel;
    private final ClientWriteHandler writeHandler;
    private final ClientHandlerCallBack clientHandlerCallBack;

    public ClientHandler(SocketChannel socketChannel, ClientHandlerCallBack clientHandlerCallBack) throws IOException {
        this.socketChannel = socketChannel;

        connector = new Connector() {
            @Override
            public void onChannelClosed(SocketChannel channel) {
                super.onChannelClosed(channel);
                try {
                    exitBySelf();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            protected void onReceiveNewMessage(String str) {
                super.onReceiveNewMessage(str);
                clientHandlerCallBack.onMsgArrived(str, ClientHandler.this);
            }
        };
        connector.setup(socketChannel);

        Selector writeSelector = Selector.open();
        socketChannel.register(writeSelector, SelectionKey.OP_WRITE);
        this.writeHandler = new ClientWriteHandler(writeSelector);
        this.clientHandlerCallBack = clientHandlerCallBack;
        System.out.println("新客户端连接：" + socketChannel.getRemoteAddress());
    }

    public void exit() throws IOException {
        writeHandler.exit();
        System.out.println("客户端已退出：" + socketChannel.getRemoteAddress());
        CloseUtils.close(socketChannel);

    }

    public void send(String str) {
        writeHandler.send(str);
    }


    private void exitBySelf() throws IOException {
        exit();
        clientHandlerCallBack.onSelfClosed(this);
    }

    public interface ClientHandlerCallBack {
        void onSelfClosed(ClientHandler handler) throws IOException;
        void onMsgArrived(String str, ClientHandler handler);
    }

    class ClientWriteHandler {
        private boolean done = false;
        private final Selector selector;
        private final ByteBuffer buffer;
        private final ExecutorService executorService;

        ClientWriteHandler(Selector selector) {
            this.selector = selector;
            this.buffer = ByteBuffer.allocate(256);
            this.executorService = Executors.newSingleThreadExecutor();
        }

        void exit() {
            done = true;
            CloseUtils.close(selector);
            executorService.shutdownNow();
        }

        void send(String str) {
            executorService.execute(new WriteRunnable(str));
        }

        class WriteRunnable implements Runnable {
            private final String msg;

            WriteRunnable(String msg) {
                this.msg = msg + "\n";
            }

            @Override
            public void run() {
                if (ClientWriteHandler.this.done) {
                    return;
                }

                buffer.clear();
                buffer.put(msg.getBytes());
                // 反转
                buffer.flip();

                while (!done && buffer.hasRemaining()) {
                    try {
                        int write = socketChannel.write(buffer);
                        if (write < 0) {
                            System.out.println("无法发送数据");
                            exitBySelf();
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
