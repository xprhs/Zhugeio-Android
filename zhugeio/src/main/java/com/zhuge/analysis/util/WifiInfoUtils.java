package com.zhuge.analysis.util;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/**
 * Created by kongmiao on 14-4-15.
 * 网络mac信息工具类
 */
public class WifiInfoUtils {
    private WifiManager wifiManager;

    public WifiInfoUtils(Context context) {
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public String getMacAddress() {
        WifiInfo info = this.wifiManager.getConnectionInfo();
        if (info!= null){
            return info.getMacAddress();
        }
        return null;
    }
}
