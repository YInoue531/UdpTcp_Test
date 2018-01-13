package com.corleois.craft.udptcp_test;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created by corleois on 2017/09/21.
 */

public class GuestSearchHost {
    private WifiManager wifi;
    private ServerSocket serverSocket; //サーバーを起動する
    private Socket connectedSocket; //ソケットを用いてホスト側と通信を行う
    private boolean waiting;
    private int udpPort = 9999;//ホスト・ゲストで統一
    private int tcpPort = 3333;//ホスト・ゲストで統一
    private Socket receiveSocket; //受け取り用


    GuestSearchHost(Context context){
        wifi = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        getIpAddress();
    }



    /**
     * ブロードキャストアドレスを取得。
     * 呼び出し元：sendBroadcast
     * @return
     */
    private InetAddress getBroadcastAddress() {
        DhcpInfo dhcpInfo = wifi.getDhcpInfo();
        int broadcast = (dhcpInfo.ipAddress & dhcpInfo.netmask) | ~dhcpInfo.netmask;
        byte[] quads = new byte[4];

        for(int i = 0;i < 4; i++){
            quads[i] = (byte)((broadcast >> i * 8) & 0xFF);
        }
        try{
            return InetAddress.getByAddress(quads);
        }catch (IOException e){
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 自分のIPアドレスを取得
     * @return IPアドレス
     */
    private String getIpAddress() {
        int ipAddress_int = wifi.getConnectionInfo().getIpAddress();
        String ipAddress;
        if(ipAddress_int == 0){
            ipAddress = null;
        }else{
            ipAddress = (ipAddress_int & 0xFF) + "." + (ipAddress_int >> 8 & 0xFF) + "." +
                    (ipAddress_int >> 16 & 0xFF) + "." + (ipAddress_int >> 24 & 0xFF);
            GuestDeviceInfo.setDeviceIpAddress(ipAddress);
        }
        Log.d("GuestIp",ipAddress);
        return ipAddress;
    }

    /**
     * 同一のWi-fiに接続している全端末に対してブロードキャスト送信を行う
     */
    public void sendBroadcast(){
        final String myIpAddress = getIpAddress();
        waiting = true;
        new Thread(){
            @Override
            public void run(){
                int count = 0;
                //送信回数を10回に制限する
                while(count < 4 && waiting){
                    try{
                        DatagramSocket udpSocket = new DatagramSocket(udpPort);
                        udpSocket.setBroadcast(true);
                        DatagramPacket packet = new DatagramPacket(myIpAddress.getBytes(), myIpAddress.length(), getBroadcastAddress(), udpPort);
                        udpSocket.send(packet);
                        udpSocket.close();
                    }catch (SocketException e){
                        e.printStackTrace();
                    }catch (IOException e){
                        e.printStackTrace();
                    }

                    //5秒待って再送信を行う
                    try{
                        Thread.sleep(5000);
                        count++;
                    }catch(InterruptedException e){
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    /**
     * ホストからTCPでIPアドレスが帰ってきたときに受け取るメソッド。
     * ホストからIPアドレスが送られてくるので、受け取るための待ち受け状態を作っておく
     */
    public void receiveHostIp(){
        new Thread(){
            @Override
            public void run(){
                while(waiting){
                    try{
                        if(serverSocket == null){
                            //サーバーを起動する
                            serverSocket = new ServerSocket(tcpPort);
                        }
                        //IPアドレスを受け取るための待ち受け状態にする。
                        connectedSocket = serverSocket.accept();

                        //ゲストからTCP通信が行われると待ち状態になっていた処理が再開。
                        //④ゲスト：ホストから端末情報を受け取る。
                        inputDeviceNameAndIp(connectedSocket);
                        if(serverSocket != null){
                            serverSocket.close();
                            serverSocket = null;
                        }
                        if(connectedSocket != null){
                            connectedSocket.close();
                            connectedSocket = null;
                        }
                        waiting = false;
                        connectReceive();
                    }catch (IOException e){
                        waiting = false;
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    /**
     * IPアドレスと端末名を取得して保持する。
     * @param socket
     */
    private void inputDeviceNameAndIp(Socket socket){
        try{
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            int infoCounter = 0;
            String remoteDeviceInfo;
            //ホスト端末情報(端末名とIPアドレス)を保存する
            while((remoteDeviceInfo = bufferedReader.readLine()) != null && !remoteDeviceInfo.equals("outputFinish")){
                switch (infoCounter){
                    case 0:
                        //一行目：端末名の格納
                        HostDeviceInfo.setDeviceName(remoteDeviceInfo);
                        infoCounter++;
                        break;
                    case 1:
                        //二行目：IPアドレスの取得
                        HostDeviceInfo.setDeviceIpAddress(remoteDeviceInfo);
                        Log.d("HostIp",HostDeviceInfo.getDeviceIpAddress());
                        RelayToMonitor.receiveHostIp();
                        return;
                    default:
                        return;
                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }


    /**
     * IPアドレスが判明したホストに対して接続を行い、メッセージを送信する。
     * @param remoteIpAddress　ホストのIPアドレス
     */
    public void connect(final String remoteIpAddress){
        waiting = false;
        new Thread(){
            @Override
            public void run(){
                try{
                    if(serverSocket == null){
//                        serverSocket = new ServerSocket(tcpPort);
                    }
                    if(connectedSocket == null){
                        connectedSocket = new Socket(remoteIpAddress, tcpPort);
                    }

                    //この後はホストに対してInputStreamやOutputStreamを用いて入出力を行う
                    outputDevice(connectedSocket, "Hello, " + HostDeviceInfo.getDeviceName() + "!");
                    Log.d("Guest","Output to " + remoteIpAddress);

                }catch (UnknownHostException e){
                    e.printStackTrace();
                }catch (ConnectException e){
                    e.printStackTrace();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * IPアドレスが判明したホストに対して接続を行い、メッセージを送信する。
     * @param remoteIpAddress　ホストのIPアドレス
     */
    public void  connectSend(final String remoteIpAddress, final String text){
        new Thread(){
            @Override
            public void run(){
                try{
                    //前に使ったconnectSocketがあるならいったん閉じる
                    if(connectedSocket != null){
                        connectedSocket.close();
                        connectedSocket = null;
                    }

                    connectedSocket = new Socket(remoteIpAddress, tcpPort);

                    //この後はホストに対してInputStreamやOutputStreamを用いて入出力を行う
                    Log.d("Guest","Output to " + remoteIpAddress);
                    outputDevice(connectedSocket, text);

                }catch (UnknownHostException e){
                    e.printStackTrace();
                }catch (ConnectException e){
                    e.printStackTrace();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * ホスト側からの送信データの受け取り用
     */
    public void connectReceive(){
        new Thread(){
            @Override
            public void run(){
                try {
                    if(serverSocket == null) {
                        serverSocket = new ServerSocket(tcpPort);
                    }

                    receiveSocket =  serverSocket.accept();
                    inputDevice(receiveSocket);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }.start();
    }

    /**
     * 文字列送信処理
     * @param sendSocket　送信先の情報が入ってるソケット
     */
    private void outputDevice(final Socket sendSocket, final String text) {
        new Thread(){
            @Override
            public void run(){
                BufferedWriter bufferedWriter = null;
                try{
                    if(bufferedWriter == null) {
                        bufferedWriter = new BufferedWriter(new OutputStreamWriter(sendSocket.getOutputStream()));
                    }
                    //デバイス名を書き込む
                    bufferedWriter.write(text);
                    bufferedWriter.newLine();

                    //出力終了の文字列を書き込む
                    bufferedWriter.write("outputFinish");

                    //出力する
                    bufferedWriter.flush();

                    Log.d("Guest","outputFinish");

                    bufferedWriter.close();

//                    bufferedWriter = null;

                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * ホスト側から受信した文字列をホスト情報内に保存して、画面に表示する
     * @param socket
     */
    private void inputDevice(Socket socket){
        try{
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String remoteDeviceInfo;
            //ホスト端末情報(端末名とIPアドレス)を保存する
            while((remoteDeviceInfo = bufferedReader.readLine()) != null && !remoteDeviceInfo.equals("outputFinish")){
                HostDeviceInfo.setReceiveText(remoteDeviceInfo);
                RelayToMonitor.receiveData();
            }

//            receiveSocket.close();
//            receiveSocket = null;
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    public void disconnect(){
        new Thread(){
            public void run() {
                try {
                    if (connectedSocket != null) {
                        connectedSocket.close();
                        connectedSocket = null;

                        Log.d("Guest","ConnectedSocket Stop");
                    }
//                    if(serverSocket != null){
//                        serverSocket.close();
//                        serverSocket = null;
//
//                        Log.d("Guest","ServerSocket Stop");
//                    }

                }catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
