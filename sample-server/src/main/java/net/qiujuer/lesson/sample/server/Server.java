package net.qiujuer.lesson.sample.server;

import net.qiujuer.common.constants.TCPConstants;
import net.qiujuer.common.util.FileUtil;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.core.impl.IoSelectorProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class Server {
    public static void main(String[] args) throws IOException {
        File cachePath = FileUtil.getCacheDir("server");
        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .start();
        TCPServer tcpServer = new TCPServer(TCPConstants.PORT_SERVER, cachePath);
        boolean isSucceed = tcpServer.start();
        if (!isSucceed) {
            System.out.println("Start TCP server failed!");
            return;
        }

        UDPProvider.start(TCPConstants.PORT_SERVER);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String str;
        do {
            str = bufferedReader.readLine();
            if (!"00bye00".equalsIgnoreCase(str)) {
                break;
            }

            tcpServer.broadcast(str);
        } while (true);

        UDPProvider.stop();
        tcpServer.stop();

        IoContext.close();
    }
}
