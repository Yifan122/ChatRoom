package net.qiujuer.lesson.sample.client;


import net.qiujuer.common.util.FileUtil;
import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.library.clink.box.FileSendPacket;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.core.impl.IoSelectorProvider;

import java.io.*;

public class Client {
    public static void main(String[] args) throws IOException {
        File cachePath = FileUtil.getCacheDir("client");
        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .start();

        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (info != null) {
            TCPClient tcpClient = null;
            try {
                tcpClient = TCPClient.startWith(info, cachePath);
                if (tcpClient == null) {
                    return;
                }

                write(tcpClient);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                tcpClient.exit();
            }
        }

        IoContext.close();
    }

    public static void write(TCPClient client) throws IOException {
        // 构建键盘输入流
        InputStream in = System.in;
        BufferedReader input = new BufferedReader(new InputStreamReader(in));


        do {
            // 键盘读取一行
            String str = input.readLine();
            if ("00bye00".equalsIgnoreCase(str)) {
                break;
            }

            // --f 發送文件
            if (str.startsWith("--f")) {
                String[] array = str.split(" ");
                if (array.length > 1) {
                    String fileName = array[1];
                    File file = new File(fileName);
                    if (file.exists() && file.isFile()) {
                        FileSendPacket fileSendPacket = new FileSendPacket(file);
                        client.send(fileSendPacket);
                        continue;
                    }
                }
            }
            // 发送到服务器
            client.send(str);


        } while (true);
    }
}
