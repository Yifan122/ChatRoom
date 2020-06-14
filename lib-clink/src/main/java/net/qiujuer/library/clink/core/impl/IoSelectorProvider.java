package net.qiujuer.library.clink.core.impl;

import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IoSelectorProvider implements IoProvider {
    private final Selector readSelector;
    private final Selector writeSelector;
    private final HashMap<SelectionKey, Runnable> inputCallbackMap = new HashMap<>();
    private final HashMap<SelectionKey, Runnable> outputCallbackMap = new HashMap<>();
    private final ExecutorService inputHandlePool;
    private final ExecutorService outputHandlePool;
    private AtomicBoolean isClosed = new AtomicBoolean(false);
    private AtomicBoolean inRegInput = new AtomicBoolean(false);
    private AtomicBoolean inRegOutput = new AtomicBoolean(false);

    public IoSelectorProvider() throws IOException {
        this.readSelector = Selector.open();
        this.writeSelector = Selector.open();

        this.inputHandlePool = Executors.newFixedThreadPool(4, new IoProviderThreadFactory("IoProvider-input-"));
        this.outputHandlePool = Executors.newFixedThreadPool(4, new IoProviderThreadFactory("IoProvider-output-"));

        // 开始监听
        startRead();
        startWrite();
    }


    private void startRead() {
        Thread thread = new Thread("Clink IoSelectorProvider ReadSelector Thread") {
            @Override
            public void run() {
                while (!isClosed.get()) {
                    try {
                        if (readSelector.select() == 0) {
                            waitSelection(inRegInput);
                            continue;
                        }

                        Set<SelectionKey> selectionKeys = readSelector.selectedKeys();
                        for (SelectionKey key : selectionKeys) {
                            if (key.isValid()) {
                                handleSelection(key, SelectionKey.OP_READ, inputCallbackMap, inputHandlePool);
                            }
                        }

                        selectionKeys.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
        };

        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    private void startWrite() {
        Thread thread = new Thread("Clink IoSelectorProvider WriteSelector Thread") {
            @Override
            public void run() {
                while (!isClosed.get()) {
                    while (!isClosed.get()) {
                        try {
                            if (writeSelector.select() == 0) {
                                waitSelection(inRegOutput);
                                continue;
                            }

                            Set<SelectionKey> selectionKeys = writeSelector.selectedKeys();
                            for (SelectionKey key : selectionKeys) {
                                if (key.isValid()) {
                                    handleSelection(key, SelectionKey.OP_WRITE, outputCallbackMap, outputHandlePool);
                                }
                            }

                            selectionKeys.clear();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
        };

        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleInputCallBack callBack) {
        SelectionKey selectionKey = registerSelection(channel, readSelector, SelectionKey.OP_READ, inRegInput, inputCallbackMap, callBack);
        return selectionKey != null;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleOutputCallBack callBack) {
        SelectionKey selectionKey = registerSelection(channel, writeSelector, SelectionKey.OP_WRITE, inRegOutput, outputCallbackMap, callBack);
        return selectionKey != null;
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        unRegisterSelection(channel, readSelector, inputCallbackMap);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel, writeSelector, outputCallbackMap);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            inputHandlePool.shutdown();
            outputHandlePool.shutdown();

            inputCallbackMap.clear();
            outputCallbackMap.clear();

            readSelector.wakeup();
            writeSelector.wakeup();

            CloseUtils.close(readSelector, writeSelector);
        }

    }

    static class IoProviderThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        IoProviderThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    private static void waitSelection(AtomicBoolean locker) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (locker) {
            if (locker.get()) {
                try {
                    locker.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void handleSelection(SelectionKey key, int keyOps, HashMap<SelectionKey, Runnable> map, ExecutorService handlePool) {
        // 取消对keyOps 的继续监听
        key.interestOps(key.readyOps() & ~keyOps);

        Runnable runnable = map.get(key);
        if (runnable != null && !handlePool.isShutdown()) {
            handlePool.execute(runnable);
        }
    }

    private static SelectionKey registerSelection(SocketChannel channel, Selector selector,
                                                  int registerOps, AtomicBoolean locker,
                                                  HashMap<SelectionKey, Runnable> map,
                                                  Runnable runnable) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (locker) {
            locker.set(true);

            try {
                // 唤醒当前的selector， 让selector不处于select（）状态
                selector.wakeup();
                SelectionKey key = null;
                if (channel.isRegistered()) {
                    key = channel.keyFor(selector);
                    if (key != null) {
                        key.interestOps(key.readyOps() | registerOps);
                    }
                }

                if (key == null) {
                    // 注册selector
                    key = channel.register(selector, registerOps);
                    map.put(key, runnable);
                }
                return key;
            } catch (ClosedChannelException e) {
                return null;
            } finally {
                // 解除锁定
                locker.set(false);
                try {
                    locker.notifyAll();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void unRegisterSelection(SocketChannel channel, Selector selector, Map<SelectionKey, Runnable> map) {
        if (channel.isRegistered()) {
            SelectionKey key = channel.keyFor(selector);
            if (key != null) {
                // 取消监听
                key.cancel();
                map.remove(key);
                selector.wakeup();
            }
        }
    }

}
