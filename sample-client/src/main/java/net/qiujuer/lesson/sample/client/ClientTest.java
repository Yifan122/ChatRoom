package net.qiujuer.lesson.sample.client;

import net.qiujuer.lesson.sample.client.bean.ServerInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class ClientTest {
    private static boolean done = false;

    public static void main(String[] args) throws IOException, InterruptedException {
        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);
        if (info == null)
            return;

        int size = 0;
        final List<TCPClient> tcpClientList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            try {
                TCPClient tcpClient = TCPClient.startWith(info);
                if (tcpClient == null) {
                    System.out.println("连接异常");
                    continue;
                }

                System.out.println("连接成功" + (++size));
                tcpClientList.add(tcpClient);
            } catch (IOException e) {
                System.out.println("连接异常");
                e.printStackTrace();
            }

            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.in.read();


        while (!done) {
            for (TCPClient tcpClient : tcpClientList) {
                tcpClient.send("hello~");
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        System.in.read();
        done = true;

        for (TCPClient tcpClient : tcpClientList) {
            tcpClient.exit();
        }


    }
}
