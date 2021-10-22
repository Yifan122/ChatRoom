package net.qiujuer.lesson.sample.client;


import net.qiujuer.lesson.sample.client.bean.ServerInfo;

import java.io.IOException;
import java.util.ArrayList;

public class Client {

    public static boolean done = false;
    public static void main(String[] args) throws IOException, InterruptedException {
        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);
        final ArrayList<TCPClient> tcpClients = new ArrayList<>();

        if (info != null) {
            try {
                for (int i = 0; i < 1000; i++) {
                    TCPClient tcpClient = TCPClient.startWith(info);
                    if (tcpClient != null) {
                        tcpClients.add(tcpClient);
                        System.out.println("TCPClient connections: " + tcpClients.size());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.in.read();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!done) {
                    for(TCPClient client: tcpClients) {
                        try {
                            client.write("hello");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });

        thread.start();

        thread.join();

        for (TCPClient tcpClient : tcpClients) {
            tcpClient.exit();
        }
        System.out.println("done!");


    }
}
