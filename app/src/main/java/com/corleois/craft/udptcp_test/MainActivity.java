package com.corleois.craft.udptcp_test;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity{

    HostStandby hostStandby;
    GuestSearchHost guestSearchHost;

    Button connectButton,disConnectButton;
    TextView textViewHost,textViewGuest,textViewMain,textViewMsg;
    EditText editTextMsg;
    private Button HButton;
    private Button GButton;

    Handler handler = new Handler(){
        public void handleMessage(Message msg){
            switch (msg.what){
                //ゲスト端末側の処理。ホスト端末との接続が確立した時に表示される。
                case RelayToMonitor.CODE_RECEIVE_HOSTINFO:
                    textViewHost.setText(HostDeviceInfo.getDeviceIpAddress());
                    Toast.makeText(getApplicationContext(),"ホスト端末と通信を開始します。\nホストIP：" +
                            HostDeviceInfo.getDeviceIpAddress(),Toast.LENGTH_SHORT).show();
                    connectButton.setEnabled(true);
                    disConnectButton.setEnabled(true);
                    break;
                //ホスト端末側の処理。ゲスト端末との接続が確立した時に表示される。
                case RelayToMonitor.CODE_RECEIVE_GUESTINFO:
                    textViewGuest.setText(GuestDeviceInfo.getDeviceIpAddress());
                    Toast.makeText(getApplicationContext(),"ゲスト端末と通信を開始します。\nゲストIP：" +
                            GuestDeviceInfo.getDeviceIpAddress(),Toast.LENGTH_SHORT).show();
                    connectButton.setEnabled(true);
                    disConnectButton.setEnabled(true);
                    break;
                //通信相手からデータを受信した時に行われる処理。
                case RelayToMonitor.CODE_RECEIVE_DATA:
                    //ホスト側が受信した時の処理
                    if(hostStandby != null) {
                        textViewMsg.setText(GuestDeviceInfo.getReceiveText());
                        Toast.makeText(getApplicationContext(), GuestDeviceInfo.getReceiveText() +
                                " を受信しました。", Toast.LENGTH_SHORT).show();
                        hostStandby.connectReceive();
                    //ゲスト側が受信した時の処理
                    }else if(guestSearchHost != null){
                        textViewMsg.setText(HostDeviceInfo.getReceiveText());
                        Toast.makeText(getApplicationContext(), HostDeviceInfo.getReceiveText() +
                                "を受信しました。", Toast.LENGTH_SHORT).show();
                        guestSearchHost.connectReceive();
                    }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        HButton = (Button)findViewById(R.id.buttonH);
        GButton = (Button)findViewById(R.id.buttonG);

        textViewMain = (TextView)findViewById(R.id.textViewMain);
        textViewHost = (TextView)findViewById(R.id.textViewH);
        textViewGuest = (TextView)findViewById(R.id.textViewG);
        textViewMsg = (TextView)findViewById(R.id.textViewMsg);

        editTextMsg = (EditText)findViewById(R.id.editTextMsg);

        RelayToMonitor.setReceiveHandler(handler);

        connectButton = (Button)findViewById(R.id.buttonConnect);
        connectButton.setEnabled(false);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(guestSearchHost != null) {
                    //ゲスト側からのテキスト送信テスト
//                    guestSearchHost.connectSend(HostDeviceInfo.getDeviceIpAddress(),);
                    guestSearchHost.connectSend(HostDeviceInfo.getDeviceIpAddress(),editTextMsg.getText().toString());

                }else if(hostStandby != null){
                    //ホスト側からのテキスト送信テスト
//                    hostStandby.connectSend(GuestDeviceInfo.getDeviceIpAddress());
                    hostStandby.connectSend(GuestDeviceInfo.getDeviceIpAddress(), editTextMsg.getText().toString());
                }
            }
        });

        disConnectButton = (Button)findViewById(R.id.buttonDisConnect);
        disConnectButton.setEnabled(false);
        disConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //ゲスト側の処理
                if(guestSearchHost != null){
                    //ホストとの接続を終了する
//                    guestSearchHost.disconnect();
                //ホスト側の処理
                }else if(hostStandby != null){
                    //
                }
            }
        });



    }

    /**
     * ホストボタンが押された時の処理
     * @param v
     */
    public void host(View v){
        textViewMain.setText("あなたは　ホスト　です。");
        GButton.setEnabled(false);
        //ホストとして開始する準備
        hostStandby = new HostStandby(this);
//        HostDeviceInfo hostDeviceInfo = new HostDeviceInfo();
        //ホストのIPアドレスを表示
        textViewHost.setText(HostDeviceInfo.getDeviceIpAddress());

        //①ホスト：ゲストから発信されるブロードキャストを受信できる(受信待ち受け)状態にする
        hostStandby.createReceiveUdpSocket();

        //UDP通信とは別にTCP通信も待ち受け状態にする。
        //これをしないと④でゲストから通信しても通信先のホストが見つからない。
        hostStandby.connectReceive();
    }

    /**
     * ゲストボタンが押された時の処理
     * @param v
     */
    public void guest(View v){
        textViewMain.setText("あなたは　ゲスト　です。");
        HButton.setEnabled(false);

        //ゲストとして開始する準備
        //②ゲスト：ホスト探索開始。ゲスト端末のIPアドレスを発信する。
        guestSearchHost = new GuestSearchHost(this);
//        guestDeviceInfo = new GuestDeviceInfo();
        //ゲストのIPアドレスを表示
        textViewGuest.setText(GuestDeviceInfo.getDeviceIpAddress());

        //自分のIPアドレスを送信する
        guestSearchHost.sendBroadcast();
        //ホストのIPアドレスを受け取れるようにしておく
        guestSearchHost.receiveHostIp();
        //ホストからの通信を受信可能にしておく
//        guestSearchHost.connectReceive();
    }



}
