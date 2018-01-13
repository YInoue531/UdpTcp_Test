package com.corleois.craft.udptcp_test;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created by corleois on 2017/09/21.
 */

public class HostStandby {

    //UDP処理
    private DatagramSocket receiveUdpSocket;
    private boolean waiting;
    private int udpPort = 9999;//ホスト、ゲストで統一

    //TCP処理
    private ServerSocket serverSocket; //サーバーを起動する
    private Socket connectedSocket; //ソケットを用いてゲスト側と通信を行う。
    private Socket returnSocket; //ゲスト側に返信するようソケット
    int tcpPort = 3333;//ホスト・ゲストで統一
    private WifiManager wifi;


    HostStandby(Context context){
        wifi = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        getDeviceName();
        getIpAddress();
    }

    /**
     * 自分のIPアドレスを取得。
     * @return
     */
    private String getIpAddress() {
        int ipAddress_int = wifi.getConnectionInfo().getIpAddress();
        String ipAddress;
        if(ipAddress_int == 0){
            ipAddress = null;
        }else{
            ipAddress = (ipAddress_int & 0xFF) + "." + (ipAddress_int >> 8 & 0xFF) + "." +
                    (ipAddress_int >> 16 & 0xFF) + "." + (ipAddress_int >> 24 & 0xFF);

            HostDeviceInfo.setDeviceIpAddress(ipAddress);
        }
        Log.d("HostIp",ipAddress);
        return ipAddress;
    }

    /**
     * 自分のモデル名を取得。
     * @return
     */
    private String getDeviceName() {
        HostDeviceInfo.setDeviceName(Build.MODEL);
        return Build.MODEL;
    }


    /**
     * ①
     * ブロードキャスト受信用ソケットの作成
     * ブロードキャスト受信待ち状態にする
     */
    public void createReceiveUdpSocket() {
        waiting = true;
        new Thread() {
            @Override
            public void run(){
                String address = null;
                try {
                    //waiting = trueの間、ブロードキャストを受け取る
                    while(waiting) {
                        //受信用ソケット
                        receiveUdpSocket = new DatagramSocket(udpPort);
                        byte[] buf = new byte[256];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        //ゲスト端末からのブロードキャストを受け取る
                        //受け取るまでは待ち状態になる
                        receiveUdpSocket.receive(packet);

                        //以下、IPアドレスを受け取ると上で止まっていた処理が再開する。

                        //受信バイト数取得
                        int length = packet.getLength();
                        //受け取ったパケットを文字列にする
                        address = new String(buf, 0, length);

                        //ゲスト端末のIPアドレスを保存
                        GuestDeviceInfo.setDeviceIpAddress(address);
                        Log.d("GuestIp",GuestDeviceInfo.getDeviceIpAddress());
                        RelayToMonitor.receiveGuestIp();

                        //③ホスト：受信データ(IPアドレス)を文字列に変換して、
                        // IPアドレスを返すために用意したメソッドに受け取ったアドレスを引数に渡す
                        returnIpAddress(address);
                        receiveUdpSocket.close();
                        receiveUdpSocket = null;

                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * ②
     * ゲストからの接続を待つ。
     * ゲスト側からの通信要求のためにTCPも待ち受け状態にする。
     */
    public void connectReceive(){
        new Thread(){
            @Override
            public void run(){
                //ServerSocketを生成する
                try {
                    Log.d("Host","Stand-by Connect");
                    //tcpPort番号でサーバーを起動する
                    if(serverSocket == null) {
                        serverSocket = new ServerSocket(tcpPort);
                    }
                    //ゲストからの接続を待って処理を進める
                    connectedSocket = serverSocket.accept();
                    //この後はconnectSocketに対してInputStreamやOutputStreamを用いて入出力を行ったりする

                    //ホスト側のテキストデータ受信テスト
                    Log.d("Host","Input");
                    inputDevice(connectedSocket);

                } catch (SocketException e){
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * ③
     * ①の処理内で呼び出される。
     * 自分(ホスト)の端末情報を受信したIPアドレスに対して返す。
     * @param remoteAddress　ゲストのIPアドレス
     */
    private void returnIpAddress(final String remoteAddress) {
        new Thread(){
            @Override
            public void run(){
                try{
                    if(returnSocket != null){
                        returnSocket.close();
                        returnSocket = null;
                    }

                    //ゲストのIPアドレスとTCPポート番号を引数にして返信用ソケットを作成
                    returnSocket = new Socket(remoteAddress, tcpPort);

                    //端末情報をゲストに送り返す
                    outputDeviceNameAndIp(returnSocket, HostDeviceInfo.getDeviceName(), HostDeviceInfo.getDeviceIpAddress());

                }catch (UnknownHostException e){
                    e.printStackTrace();
                }catch (java.net.ConnectException e){
                    e.printStackTrace();
                    try{
                        if(returnSocket != null){
                            returnSocket.close();
                            returnSocket = null;
                        }
                    }catch (IOException e1){
                        e1.printStackTrace();
                    }
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * ③の処理内で呼び出される
     * 端末名とIPアドレスのセットを送る。
     * @param outputSocket　ソケット
     * @param deviceName　自分の端末名
     * @param deviceAddress　自分のIPアドレス
     */
    private void outputDeviceNameAndIp(final Socket outputSocket, final String deviceName, final String deviceAddress){
        new Thread(){
            @Override
            public void run(){
                final BufferedWriter bufferedWriter;
                try{
                    bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputSocket.getOutputStream()));
                    //デバイス名を書き込む
                    bufferedWriter.write(deviceName);
                    bufferedWriter.newLine();

                    //IPアドレスを書き込む
                    bufferedWriter.write(deviceAddress);
                    bufferedWriter.newLine();

                    //出力終了の文字列を書き込む
                    bufferedWriter.write("outputFinish");

                    //出力する
                    bufferedWriter.flush();

                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }


    /**
     * ゲスト端末から受け取ったデータをゲスト情報に格納し、表示する。
     * @param socket
     */
    private void inputDevice(Socket socket){

        try{
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String remoteDeviceInfo;

            //ゲスト端末から受け取ったデータを保存する
            while((remoteDeviceInfo = bufferedReader.readLine()) != null && !remoteDeviceInfo.equals("outputFinish")){
                Log.d("Host","Inputted");
                GuestDeviceInfo.setReceiveText(remoteDeviceInfo);
                RelayToMonitor.receiveData();
            }

        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * 接続している端末（ゲスト端末）に対してデータ（文字列）を送信する。
     * @param remoteIpAddress
     */
    public void connectSend(final String remoteIpAddress, final String text){
        new Thread(){
            @Override
            public void run(){
                try{

                    if(connectedSocket != null){
                        connectedSocket.close();
                        connectedSocket = null;
                    }

                    connectedSocket = new Socket(remoteIpAddress, tcpPort);


                    //この後はホストに対してInputStreamやOutputStreamを用いて入出力を行う
                    outputDevice(connectedSocket, text);
                    Log.d("Host","Output to " + remoteIpAddress);

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
     * 文字列送信処理
     * @param connectedSocket　送信先の情報が入ってるソケット
     */
    private void outputDevice(final Socket connectedSocket, final String text) {
        new Thread(){
            @Override
            public void run(){
                final BufferedWriter bufferedWriter;
                try{
                    bufferedWriter = new BufferedWriter(new OutputStreamWriter(connectedSocket.getOutputStream()));
                    //デバイス名を書き込む
                    bufferedWriter.write(text);
                    bufferedWriter.newLine();

                    //出力終了の文字列を書き込む
                    bufferedWriter.write("outputFinish");

                    //出力する
                    bufferedWriter.flush();

                    Log.d("Host","outputFinish");

                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

}