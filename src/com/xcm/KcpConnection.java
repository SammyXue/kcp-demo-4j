package com.xcm;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class KcpConnection {
    InetSocketAddress targetAddress;
    DatagramSocket socket;
    KCP kcp;
    byte[] data = new byte[1024];

    public KcpConnection(int conversationId, String targetIp, int targetPort, int localPort) throws SocketException {
        socket = new DatagramSocket(localPort);
        this.targetAddress = new InetSocketAddress(targetIp, targetPort);
        kcp = new KCP(conversationId) {
            @Override
            protected void output(byte[] buffer, int size) {
                DatagramPacket packet = new DatagramPacket(buffer, size);
                packet.setSocketAddress(targetAddress);
                try {
                    socket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        refresh();
        receive();

    }

    /**
     * 开启一个线程接受udp消息并input进kcp
     */
    private void receive() {

        new Thread(() -> {
            while (true) {
                byte[] receBuf = new byte[1024];
                DatagramPacket recePacket = new DatagramPacket(receBuf, receBuf.length);
                try {
                    socket.receive(recePacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (recePacket.getLength() != 0) {
                    byte[] arr = Arrays.copyOf(receBuf, recePacket.getLength());
                    synchronized (kcp) {
                        kcp.Input(arr);
                    }
//                    System.out.println("received data from udp : " + arr);
                }
            }
        }).start();
    }

    /**
     * 每隔10ms刷新kcp内部消息
     */
    public void refresh() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            int len = 0;
            synchronized (kcp) {
                kcp.Update(System.currentTimeMillis());
                len = kcp.Recv(data);
            }
            if (len > 0) {
                String str = new String(data, 0, len);
                System.out.println("收到kcp消息：" + str);
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
    }

    public void sendMessage(byte[] buffer) {
        synchronized (kcp) {

            kcp.Send(buffer);

        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        KcpConnection server = new KcpConnection(1, "localhost", 8800, 9900);

        KcpConnection client = new KcpConnection(1, "localhost", 9900, 8800);
        while (true) {
            client.sendMessage("client say Hello".getBytes());
            server.sendMessage("server say Hello".getBytes());
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
//        kcp.Input("HELLO".getBytes());
    }
}
