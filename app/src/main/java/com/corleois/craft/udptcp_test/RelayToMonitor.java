package com.corleois.craft.udptcp_test;

import android.os.Handler;
import android.os.Message;

/**
 * Created by corleois on 2017/09/23.
 */

public class RelayToMonitor {

    private static Handler handler;

    public static final int CODE_RECEIVE_HOSTINFO = 1;
    public static final int CODE_RECEIVE_GUESTINFO = 2;
    public static final int CODE_RECEIVE_DATA = 3;
    /**
     * 通知する先を指定するhandlerを設定
     * @param handler1
     */
    public static void setReceiveHandler(Handler handler1){
        handler = handler1;
    }

    /**
     * ホストのIPアドレスを取得したことを知らせて、IPアドレスを表示させる
     */
    public static void receiveHostIp(){
        //ハンドラがnullだったら通知しても意味がないので終了
        if (handler == null) {
            return;
        }
        //メッセージ送信
        Message message = Message.obtain();
        message.what = CODE_RECEIVE_HOSTINFO;
        handler.sendMessage(message);
    }

    /**
     * ゲストのIPアドレスを取得したことを知らせて、IPアドレスを表示させる
     */
    public static void receiveGuestIp(){
        //ハンドラがnullだったら通知しても意味がないので終了
        if (handler == null) {
            return;
        }
        //メッセージ送信
        Message message = Message.obtain();
        message.what = CODE_RECEIVE_GUESTINFO;
        handler.sendMessage(message);
    }

    /**
     * データを受信したことを知らせて、受信内容を表示する
     */
    public static void receiveData(){
        //ハンドラがnullだったら通知しても意味がないので終了
        if (handler == null) {
            return;
        }
        //メッセージ送信
        Message message = Message.obtain();
        message.what = CODE_RECEIVE_DATA;
        handler.sendMessage(message);
    }
}
