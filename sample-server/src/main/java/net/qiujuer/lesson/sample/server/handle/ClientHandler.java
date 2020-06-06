package net.qiujuer.lesson.sample.server.handle;

import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler {
    private final SocketChannel socketChannel;
    private final ClientReadHandler readHandler;
    private final ClientWriteHandler writeHandler;
    private final ClientHandlerCallback clientHandlerCallback;
    private final String clientInfo;
    private final Selector readSelector;
    private final Selector writeSelector;

    public ClientHandler(SocketChannel socketChannel, ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.socketChannel = socketChannel;
        socketChannel.configureBlocking(false);

        this.readSelector = Selector.open();
        socketChannel.register(readSelector, SelectionKey.OP_READ);
        this.readHandler = new ClientReadHandler(readSelector);

        this.writeSelector = Selector.open();
        socketChannel.register(writeSelector, SelectionKey.OP_WRITE);
        this.writeHandler = new ClientWriteHandler(writeSelector);

        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = socketChannel.getRemoteAddress().toString();
        System.out.println("新客户端连接：" + clientInfo);
    }

    public String getClientInfo() {
        return clientInfo;
    }

    public void exit() {
        readHandler.exit();
        writeHandler.exit();
        CloseUtils.close(socketChannel);
        System.out.println("客户端已退出：" + clientInfo);
    }

    public void send(String str) {
        writeHandler.send(str);
    }

    public void readToPrint() {
        readHandler.start();
    }

    private void exitBySelf() {
        exit();
        clientHandlerCallback.onSelfClosed(this);
    }

    public interface ClientHandlerCallback {
        // 自身关闭通知
        void onSelfClosed(ClientHandler handler);

        // 收到消息时通知
        void onNewMessageArrive(ClientHandler clientHandler, String msg);
    }

    class ClientReadHandler extends Thread {
        private boolean done = false;
        private final Selector readSelector;
        private final ByteBuffer byteBuffer;

        ClientReadHandler(Selector selector) {
            this.readSelector = selector;
            this.byteBuffer = ByteBuffer.allocate(256);
        }

        @Override
        public void run() {
            super.run();
            try {
                do {
                    // 客户端拿到一条数据
                    if (readSelector.select() == 0) {
                        if (done)
                            break;
                        continue;
                    }

                    Iterator<SelectionKey> iterator = readSelector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        if (done)
                            break;

                        SelectionKey key = iterator.next();
                        iterator.remove();

                        if (key.isReadable()) {
                            SocketChannel client = (SocketChannel) key.channel();
                            byteBuffer.clear();

                            int read = client.read(byteBuffer);
                            if (read > 0) {
                                String msg = new String(byteBuffer.array(), 0, byteBuffer.position() - 1);
                                // notify TCPsever I have received msg
                                clientHandlerCallback.onNewMessageArrive(ClientHandler.this, msg);
                            } else {
                                System.out.println("客户端已无法读取数据！");
                                // 退出当前客户端
                                ClientHandler.this.exitBySelf();
                                break;
                            }

                        }
                    }

                } while (!done);
            } catch (Exception e) {
                if (!done) {
                    System.out.println("连接异常断开");
                    ClientHandler.this.exitBySelf();
                }
            } finally {
                // 连接关闭
                CloseUtils.close(readSelector);
            }
        }

        void exit() {
            done = true;
            readSelector.wakeup();
            CloseUtils.close(readSelector);
        }
    }

    class ClientWriteHandler {
        private boolean done = false;
        private Selector writeSelector;
        private ByteBuffer byteBuffer;
        private final ExecutorService executorService;

        ClientWriteHandler(Selector writeSelector) {
            this.writeSelector = writeSelector;
            this.byteBuffer = ByteBuffer.allocate(256);
            this.executorService = Executors.newSingleThreadExecutor();
        }

        void exit() {
            done = true;
            CloseUtils.close(writeSelector);
            executorService.shutdownNow();
        }

        void send(String str) {
            if (done)
                return;

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

                byteBuffer.clear();
                byteBuffer.put(msg.getBytes());
                // 反转
                byteBuffer.flip();

                while (!done && byteBuffer.hasRemaining()) {
                    try {
                        int len = socketChannel.write(byteBuffer);
                        if (len < 0) {
                            System.out.println("客户端无法发送数据");
                            ClientHandler.this.exitBySelf();
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
