package com.corleois.craft.udptcp_test;

import java.util.ArrayList;

/**
 * Created by corleois on 2017/09/22.
 */

public class HostDeviceInfo {

    private static String deviceName;
    private static String deviceIpAddress;
    private static ArrayList<String> receiveText = new ArrayList<String>();


    static void setDeviceName(String name) {
        deviceName = name;
    }

    static void setDeviceIpAddress(String ipAddress) {
        deviceIpAddress = ipAddress;
    }

    static void setReceiveText(String text){ receiveText.add(text); }

    static String getDeviceName(){ return deviceName; }

    static String getDeviceIpAddress() {
        return deviceIpAddress;
    }

    static String getReceiveText(){ return receiveText.get(receiveText.size() - 1); }
}
