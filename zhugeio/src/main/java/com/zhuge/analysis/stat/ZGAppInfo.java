package com.zhuge.analysis.stat;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.zhuge.analysis.util.ActivityServicesUtils;
import com.zhuge.analysis.util.ConnectivityUtils;
import com.zhuge.analysis.util.DeviceInfoUtils;
import com.zhuge.analysis.util.ManifestUtils;
import com.zhuge.analysis.util.Utils;
import com.zhuge.analysis.util.WifiInfoUtils;
import com.zhuge.analysis.util.ZGJSONObject;
import com.zhuge.analysis.util.ZGLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

/**
 * app相关数据
 * Created by Omen on 16/9/9.
 */
/*package*/  class ZGAppInfo {

    private static final String TAG = "com.zhuge.ZGAppInfo";
    /*package*/ static final int MSG_RECV = 0;
    /*package*/ static final int MSG_READ = 1;
    private String appKey = null;
    private String appChannel = null;
    private String appName = null;
    private String appVersion = null;
    private String cr = null;
    private String myProcessName = null;
    private String packageName;
    String did = null;
    private SharedPreferences globalSP;
    private String mac = null;
    private String imei = null;
    private String cuid = null;

    /*package*/ long sessionID = -1;

    /*package*/ long lastSessionActivityTime = -1;

    /*package*/ ConnectivityUtils connectivityUtils;

    private TelephonyManager tm;

    private SecretKey key;
    private String RSA_key;

    private static final String EVENT_TYPE_USER = "usr";
    private static final String EVENT_TYPE_CUS = "evt";
    private static final String EVENT_TYPE_PLATFORM = "pl";
    private static final String EVENT_TYPE_SESSION_START = "ss";
    private static final String EVENT_TYPE_SESSION_END = "se";
    private static final String EVENT_TYPE_EXCEPTION = "abp";

    boolean debug = false;

    boolean isInMainThread = false;
    /*package*/ JSONArray screenSize;
    String apiPath = null;
    String ZGSeeUrl = null;
    String apiPathBack = null;
    String ZGSeePolicyUrl = null;
    private boolean userDefinedEnableZGSee = false;
    private boolean enableZGSee = false;
    private boolean localZGSeeState = false;
    private int serverPolicy = -1;
    private int sessionSeeCount = 0;
    private int sessionEventCount = 0;

    private int mosaic=1;//是否进行打码，默认打码
    private String RSAMd5;
    /**
     * 第一张截图时间
     */
    private long firstScreenShotTime = -1L;

    ZGAppInfo(){
        super();
    }

    boolean setAppKey(String appKey) {
        if (null == appKey || "null".equals(appKey)){
            return false;
        }
        this.appKey = appKey;
        return true;
    }

    boolean setAppChannel(String appChannel){
        if (null == appChannel || "null".equals(appChannel)){
            return false;
        }
        this.appChannel = appChannel;
        return true;
    }

    void setUserDefinedEnableZGSee(boolean enable){
        userDefinedEnableZGSee = enable;
        if (serverPolicy == -1){
           enableZGSee  = enable;
        }else {
            enableZGSee = serverPolicy == 0;
        }
    }

    boolean isZGSeeEnable(){
        return enableZGSee;
    }

    void setServerPolicy(int policy){
        serverPolicy = policy;
        if (serverPolicy == -1){
            enableZGSee = userDefinedEnableZGSee ;
        }else {
            enableZGSee = serverPolicy == 0;
        }
    }

    void setRSAMd5(String RSAMd5) {
        this.RSAMd5 = RSAMd5;
    }

    String getAppChannel() {
        return appChannel;
    }

    String getAppKey() {
        return appKey;
    }

    public boolean needMosaic() {
        return mosaic == 1;
    }

    public void setMosaic(int mosaic) {
        this.mosaic = mosaic;
    }

    boolean getInfoFromManifest(Context context) {
        String[] appInfo = ManifestUtils.getManifestInfo(context);
        return setAppKey(appInfo[0]) && setAppChannel(appInfo[1]);
    }

    void initAppInfo(Context context){
        try {
            cuid = globalSP.getString(Constants.SP_CUID,null);
            PackageInfo appInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (appInfo != null) {
                this.appName = appInfo.applicationInfo.loadLabel(context.getPackageManager()).toString();
                this.appVersion = appInfo.versionName;
            } else {
                ZGLogger.logError(TAG,"packageInfo 为空。");
            }
        } catch (Exception e) {
            ZGLogger.handleException(TAG,"尝试获取应用信息出错。",e);
        }
    }

    void initGlobalSettingFile(Context context) {
        myProcessName = new ActivityServicesUtils(context).getMyProcessName();
        String myGlobalSPName =  myProcessName + appKey;
        isInMainThread = myProcessName.equals(context.getPackageName());
        globalSP = context.getSharedPreferences(myGlobalSPName, Context.MODE_PRIVATE);
        connectivityUtils = new ConnectivityUtils(context);
        tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        sessionEventCount = globalSP.getInt(Constants.SP_SESSION_COUNT,0);
        sessionSeeCount = globalSP.getInt(Constants.SP_SEE_COUNT,0);
        localZGSeeState = globalSP.getBoolean(Constants.SP_ENABLE_ZGSEE,false);
        firstScreenShotTime = globalSP.getLong(Constants.SP_FIRST_SCREENSHOT_TIME,-1);
        if (tm == null){
            cr = "(null)(null)";
            return;
        }
        String sim =  tm.getSimOperator();
        if (sim == null || sim.length() == 0){
            cr = "(null)(null)";
        }else {
            cr = sim;
        }
    }

    void upgradeSharedPrefs(Context mContext) {
        if (globalSP.getBoolean(Constants.SP_MODIFY,false)){
            ZGLogger.logVerbose("已经更新过sharedPrefs");
            return;
        }
        String packageName = mContext.getApplicationContext().getPackageName();//纯包名的SharedPreferences
        if (packageName.equals(myProcessName) && globalSP.contains(Constants.SP_DID)){
            //当前进程是主进程，含有did字段就不需要更新
            return;
        }
        upgradeSharedPrefsFrom(mContext,packageName);
        String withAppKey = packageName+appKey;//包名加appKey的SharedPreferences
        upgradeSharedPrefsFrom(mContext,withAppKey);
    }

    /**
     * 从sourceName所代表的SharedPreferences中复制信息到当前的文件
     * @param mContext 环境信息
     * @param sourceName 源文件名称
     */
    private void upgradeSharedPrefsFrom(Context mContext, String sourceName) {
        String targetName = myProcessName+appKey;
        ZGLogger.logVerbose("目标文件:"+targetName+" upgradeSharedPrefsFrom "+sourceName+" 更新数据");
        if (sourceName.equals(targetName)){
            //两个文件相同，无需做改变
            return;
        }
        SharedPreferences source = mContext.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        Map<String, ?> all = source.getAll();
        SharedPreferences.Editor target = globalSP.edit();
        if (all.size() == 0){
            target.putBoolean(Constants.SP_MODIFY,true).apply();
            return;
        }
        if (all.containsKey(Constants.SP_DID)){
            String did = source.getString(Constants.SP_DID,null);
            target.putString(Constants.SP_DID,did);
        }
        if (all.containsKey(Constants.SP_UPDATE_DEVICE_TIME)){
            Long updateTime = source.getLong(Constants.SP_UPDATE_DEVICE_TIME,-1);
            target.putLong(Constants.SP_UPDATE_DEVICE_TIME,updateTime);
        }
        if (all.containsKey(Constants.SP_LAST_SESSION_TIME)){
            String time = source.getString(Constants.SP_LAST_SESSION_TIME,null);
            target.putString(Constants.SP_LAST_SESSION_TIME,time);
        }
        if (all.containsKey(Constants.SP_CUID)){
            String cuid = source.getString(Constants.SP_CUID,null);
            target.putString(Constants.SP_CUID,cuid);
        }
        target.putBoolean(Constants.SP_MODIFY,true).apply();
    }

    void initDeviceInfo(Context context) {
        SharedPreferences sp;
        packageName = context.getPackageName();
        if (packageName.equals(myProcessName)){
            sp = globalSP;
        }else {
            String uniqueName = packageName+appKey;
            sp = context.getSharedPreferences(uniqueName,Context.MODE_PRIVATE);
        }
        String localDid = sp.getString(Constants.SP_DID,null);
        ZGLogger.logVerbose("获取到的localDid为"+localDid);
        try {
            if (null != localDid && localDid.length()>0){
                String[] strings = localDid.split("\\|");
                String deviceId = strings[0];
                if (strings.length >2){
                    this.mac = strings[1];
                    this.imei = strings[2];
                }
                if (this.did == null){
                    this.did = deviceId;
                }
                if (!this. did.equals(deviceId)){
                    String deviceInfo = this.did+"|"+mac+"|"+imei;
                    sp.edit().putString(Constants.SP_DID,deviceInfo).apply();
                }
            }else if (packageName.equals(myProcessName)){
                String mac = new WifiInfoUtils(context).getMacAddress();
                this.mac = mac;
                int i = context.checkPermission(Manifest.permission.READ_PHONE_STATE, Process.myPid(), Process.myUid());
                if (i == PackageManager.PERMISSION_GRANTED){
                    this.imei = tm.getDeviceId();
                }else {
                    ZGLogger.logError(TAG,"应用未获得权限：android.permission.READ_PHONE_STATE");
                    this.imei = null;
                }
                if (this.did == null){
                    this.did = generateDid();
                }
                String deviceInfo = this.did+"|"+mac+"|"+imei;
                sp.edit().putString(Constants.SP_DID,deviceInfo).apply();
                ZGLogger.logVerbose("生成的deviceInfo为"+deviceInfo);
            }
        }catch (Exception e){
            ZGLogger.handleException("com.zhuge.AppInfo","计算用户唯一ID失败",e);
        }
    }

    /**
     * 使用设备信息字段来生成UUID
     * @return 设备唯一标识did
     */
    private String generateDid() {
        long info_ts = globalSP.getLong(Constants.SP_UPDATE_DEVICE_TIME, -1);
        if (info_ts > 0 && imei!= null){
            return Utils.md5(imei+mac);
        }
        String serial = Build.SERIAL;
        String board = Build.BOARD;
        String brand = Build.BRAND;
        String cpu = Build.CPU_ABI;
        String device = Build.DEVICE;
        String display = Build.DISPLAY;
        String host = Build.HOST;
        String id = Build.ID;
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        String product = Build.PRODUCT;
        String tags = Build.TAGS;
        String type = Build.TYPE;
        String user = Build.USER;
        String info = "35"+
                board.length()%10+brand.length()%10+
                cpu.length()%10+device.length()%10+
                display.length()%10+host.length()%10+
                id.length()%10+manufacturer.length()%10+
                model.length()%10+product.length()%10+
                tags.length()%10+type.length()%10+
                user.length()%10;
        if (serial == null){
            serial = "zhuge.serial";
        }
        return new UUID(info.hashCode(),serial.hashCode()).toString();
    }

    void logInitInfo() {
        if (!ZGLogger.logEnable){
            return;
        }
        StringBuilder info = new StringBuilder();
        info.append("appKey: ").append(appKey).append("\n")
                .append("渠道: ").append(appChannel).append("\n")
                .append("应用名称: ").append(appName).append("\n")
                .append("应用版本: ").append(appVersion).append("\n")
                .append("设备标识: ").append(did).append("\n")
                .append("用户标识: ").append(cuid).append("\n")
                .append("系统版本：").append(DeviceInfoUtils.getOSVersion()).append("\n")
                .append("手机型号：").append(DeviceInfoUtils.getDevice()).append("\n");
        Constants.configString(info);
        info.append("实时调试: ").append(debug).append("\n");
        ZGLogger.logMessage(TAG,info.toString());
    }

    ZGJSONObject buildDeviceInfo(Context context , boolean force) {

        if (!isInMainThread){
            return null;
        }
        try {
            long info_ts = globalSP.getLong(Constants.SP_UPDATE_DEVICE_TIME, -1);
            long now_t = System.currentTimeMillis();
            if (force || info_ts == -1 || (now_t / 86400000 - info_ts / 86400000) >= 1) {
                ZGJSONObject infoObject = new ZGJSONObject();
                infoObject.put("dt", EVENT_TYPE_PLATFORM);
                ZGJSONObject pr = dataCommon();
                String deviceInfo = globalSP.getString(Constants.SP_USER_DEFINE_DEVICE,null);
                ZGLogger.logVerbose("获取自定义设备信息deviceInfo is "+deviceInfo);
                if (null != deviceInfo){
                    JSONObject userInfo = new JSONObject(deviceInfo);
                    Iterator keys = userInfo.keys();
                    while (keys.hasNext()){
                        String key = String.valueOf(keys.next());
                        pr.put(key,userInfo.get(key));
                    }
                }
                pr.put("$an",null);
                pr.put("$cn",null);
                pr.put("$br", DeviceInfoUtils.getBrand());
                pr.put("$dv", DeviceInfoUtils.getDevice());
                pr.put("$imei", this.imei);
                pr.put("$lang", Locale.getDefault().getLanguage());
                pr.put("$mkr", DeviceInfoUtils.getManfacturer());
                pr.put("$os","Android");
                boolean see = globalSP.getBoolean(Constants.SP_ENABLE_ZGSEE,false);
                pr.put("$zs",see?"1":"");
                pr.put("$rs", DeviceInfoUtils.getResolution(context));
                pr.put("$vn",null);
                infoObject.put("pr",pr);
                return infoObject;
            } else {
                return null;
            }
        } catch (Exception e) {
            ZGLogger.handleException(TAG,"获取设备信息出错",e);
            return null;
        }
    }

    private ZGJSONObject dataCommon() throws JSONException {
        ZGJSONObject header = new ZGJSONObject();
        header.put("$an",appName);
        header.put("$cn",appChannel);
        header.put("$cr",cr);
        header.put("$ct",System.currentTimeMillis());
        if (null != cuid){
            header.put("$cuid",cuid);
        }
        header.put("$os","Android");
        header.put("$tz",Utils.getTimeZone());
        header.put("$vn",appVersion);
        return header;
    }

    SharedPreferences getGlobalSP() {
        return globalSP;
    }

    ZGJSONObject buildSessionStart(String name) {
        ZGJSONObject st = new ZGJSONObject();
        try {
            sessionEventCount = 0;
            globalSP.edit().putInt(Constants.SP_SESSION_COUNT,sessionEventCount).apply();
            st.put("dt", EVENT_TYPE_SESSION_START);
            ZGJSONObject infoObject = dataCommon();
            infoObject.put("$net",Integer.toString(connectivityUtils.getNetworkType()));
            infoObject.put("$mnet",Integer.toString(tm.getNetworkType()));
            infoObject.put("$ov", DeviceInfoUtils.getOSVersion());
            infoObject.put("$sid", sessionID);
            infoObject.put("$ss_name",name);
            infoObject.put("$sc",sessionEventCount);
            infoObject.put("$ps",myProcessName);
            st.put("pr",infoObject);
        } catch (JSONException e) {
            ZGLogger.handleException(TAG,"会话开始错误",e);
        }
        return st;
    }

    JSONObject buildSeeStart(){
        JSONObject st = new JSONObject();
        try {
            st.put("dt",EVENT_TYPE_SESSION_START);
            sessionSeeCount = 0;
            firstScreenShotTime = -1L;
            globalSP.edit().putLong(Constants.SP_FIRST_SCREENSHOT_TIME,firstScreenShotTime).apply();
            globalSP.edit().putInt(Constants.SP_SEE_COUNT,sessionSeeCount).apply();
            JSONObject pr = new JSONObject();
            if (!TextUtils.isEmpty(cuid)){
                pr.put("$cuid",cuid);
            }
            pr.put("$ct",System.currentTimeMillis());
            pr.put("$sid",sessionID);
            pr.put("$net",Integer.toString(connectivityUtils.getNetworkType()));
            pr.put("$os","Android");
            pr.put("$ov", DeviceInfoUtils.getOSVersion());
            pr.put("$br", DeviceInfoUtils.getBrand());
            pr.put("$dv", DeviceInfoUtils.getDevice());
            pr.put("$av",appVersion);
            pr.put("$cr",cr);
            pr.put("$sc",sessionSeeCount);
            st.put("pr",pr);
        }catch (Exception e){
            ZGLogger.handleException(TAG,"see会话开始错误",e);
        }
        return st;
    }

    ZGJSONObject buildSessionEnd() {
        String info = globalSP.getString(Constants.SP_LAST_SESSION_TIME, "");
        if (info.equals("")) {
            return null;
        }
        sessionEventCount++;
        String[] infos = info.split("\\|");
        long sid = Long.parseLong(infos[0]);
        long ts = Long.parseLong(infos[1]);
        if (sid <=0 ){
            return null;
        }
        ZGJSONObject se = new ZGJSONObject();
        try {
            se.put("dt", EVENT_TYPE_SESSION_END);
            ZGJSONObject pr = dataCommon();
            pr.put("$sid", sid);
            pr.put("$dru", ts-sid);
            pr.put("$ov", DeviceInfoUtils.getOSVersion());
            pr.put("$net",Integer.toString(connectivityUtils.getNetworkType()));
            pr.put("$mnet",Integer.toString(tm.getNetworkType()));
            pr.put("$sc",sessionEventCount);
            se.put("pr",pr);
        } catch (JSONException e) {
            ZGLogger.handleException(TAG,"会话结束事件错误。",e);
            return null;
        }
        return se;
    }

    ZGJSONObject buildRevenueEvent(String eventName, JSONObject copy) {
        try {
            ZGJSONObject event = new ZGJSONObject();
            event.put("dt",EVENT_TYPE_EXCEPTION);
            ZGJSONObject pr = dataCommon();
            pr.put("$sid",sessionID);
            pr.put("$eid",eventName);
            pr.put("$net",Integer.toString(connectivityUtils.getNetworkType()));
            pr.put("$mnet",Integer.toString(tm.getNetworkType()));
            pr.put("$ov", DeviceInfoUtils.getOSVersion());
            sessionEventCount++;
            globalSP.edit().putInt(Constants.SP_SESSION_COUNT,sessionEventCount).apply();
            pr.put("$sc",sessionEventCount);
            pr.put("$ps",myProcessName);
            String deviceInfo = globalSP.getString(Constants.SP_USER_DEFINE_EVENT,null);
            if (null != deviceInfo){
                JSONObject userInfo = new JSONObject(deviceInfo);
                Iterator keys = userInfo.keys();
                while (keys.hasNext()){
                    String key = String.valueOf(keys.next());
                    pr.put(key,userInfo.get(key));
                }
            }
//            ZGLogger.logMessage(TAG,copy.toString());
            if (copy != null){
                Iterator keys = copy.keys();
                while (keys.hasNext()){
                    String key = String.valueOf(keys.next());
                    pr.put(key,copy.get(key));
                }
            }
            event.put("pr",pr);
            return event;
        }catch (Exception e){
            ZGLogger.handleException(TAG,"生成自定义事件出错，事件"+eventName+"将被丢弃。",e);
        }
        return null;
    }

    ZGJSONObject buildCustomEvent(String eventName, JSONObject copy) {
        try {
            ZGJSONObject event = new ZGJSONObject();
            event.put("dt",EVENT_TYPE_CUS);
            ZGJSONObject pr = dataCommon();
            pr.put("$sid",sessionID);
            pr.put("$eid",eventName);
            pr.put("$net",Integer.toString(connectivityUtils.getNetworkType()));
            pr.put("$mnet",Integer.toString(tm.getNetworkType()));
            pr.put("$ov", DeviceInfoUtils.getOSVersion());
            sessionEventCount++;
            globalSP.edit().putInt(Constants.SP_SESSION_COUNT,sessionEventCount).apply();
            pr.put("$sc",sessionEventCount);
            pr.put("$ps",myProcessName);
            String deviceInfo = globalSP.getString(Constants.SP_USER_DEFINE_EVENT,null);
            if (null != deviceInfo){
                JSONObject userInfo = new JSONObject(deviceInfo);
                Iterator keys = userInfo.keys();
                while (keys.hasNext()){
                    String key = String.valueOf(keys.next());
                    pr.put(key,userInfo.get(key));
                }
            }
            if (copy != null){
                Iterator keys = copy.keys();
                while (keys.hasNext()){
                    String key = String.valueOf(keys.next());
                    pr.put(key,copy.get(key));
                }
            }
            event.put("pr",pr);
            return event;
        }catch (Exception e){
            ZGLogger.handleException(TAG,"生成自定义事件出错，事件"+eventName+"将被丢弃。",e);
        }
        return null;
    }

    ZGJSONObject buildIdentify(String uid, JSONObject copy) {
        try {
            ZGJSONObject event = new ZGJSONObject();
            event.put("dt",EVENT_TYPE_USER);
            ZGJSONObject pr = dataCommon();
            pr.put("$cuid", uid);
            cuid = uid;
            if (copy != null){
                Iterator keys = copy.keys();
                while (keys.hasNext()){
                    String key = String.valueOf(keys.next());
                    pr.put(key,copy.get(key));
                }
            }
            event.put("pr",pr);
            return event;
        }catch (Exception e){
            ZGLogger.handleException(TAG,"标记用户出错，用户"+uid+"信息将被丢弃。",e);
        }
        return null;
    }


    String getDid() {
        return did;
    }

    long getSessionID() {
        return sessionID;
    }


    JSONObject wrapData(JSONArray events) {
        JSONObject postData = new JSONObject();
        try {
            postData.put("ak", appKey);
            postData.put("data", events);
            postData.put("debug",debug?1:0);
            postData.put("sln","itn");
            postData.put("sdk", "zg_android");
            postData.put("owner", "zg");
            postData.put("pl", "and");

//            postData.put("sdkv", Constants.SDK_V);
            postData.put("tz", Utils.getTimeZone());
            JSONObject object = new JSONObject();
            object.put("did",did);
            postData.put("usr",object);
            long now_t = System.currentTimeMillis();
            postData.put("ut", Utils.timeStamp2Date(now_t));
        } catch (Exception e) {
            ZGLogger.handleException(TAG,"组装数据出错",e);
        }
        return postData;
    }

    ZGJSONObject channelData(String channel, String userId) {
        ZGJSONObject infoObject = new ZGJSONObject();
        try {
            long now_t = System.currentTimeMillis();
            infoObject.put("dt", "um");
            JSONObject pr = new JSONObject();
            pr.put("$tz", Utils.getTimeZone());
            pr.put("$ct", now_t);
            pr.put("$push_ch", channel);
            pr.put("$push_id", userId);
            infoObject.put("pr", pr);
        } catch (JSONException e) {
            ZGLogger.handleException(TAG,"处理第三方推送信息出错",e);
        }
        return infoObject;
    }

    ZGJSONObject parseMid(int msgStat, ZhugeSDK.PushChannel channel, Object t) {
        JSONObject js;
        String mid = "";
        try {
            switch (channel) {
                case BAIDU:
                    String baidu = (String) t;
                    if (null == baidu)
                        return null;
                    js = new JSONObject(baidu);
                    mid = js.getString("mid");
                    break;

                case GETUI:
                    break;
                case JPUSH:
                    if (t instanceof String) {
                        String jpush = (String) t;
                        js = new JSONObject(jpush);
                        mid = js.getString("mid");
                    } else if (t instanceof JSONObject) {
                        js = (JSONObject) t;

                        mid = js.getString("mid");
                    }
                    break;
                case UMENG:
                    js = (JSONObject) t;
                    JSONObject midJson = js.getJSONObject("extra");
                    mid = midJson.getString("mid");
                    break;
                case XIAOMI:
                    if (t instanceof Map) {
                        Map<String, String> map = (Map<String, String>) t;
                        mid = map.get("mid");
                    }
                    break;
                case XINGE:

                    if (t instanceof String) {
                        String xinge = (String) t;
                        js = new JSONObject(xinge);
                        mid = js.getString("mid");
                    }
                    break;
            }
        } catch (ClassCastException e) {
            ZGLogger.handleException(TAG,"传递参数有误。",e);
        } catch (JSONException e) {
            ZGLogger.handleException(TAG,"JSON转换出错。",e);
        }
        return dealMid(mid,channel.toString(),msgStat);
    }

    private ZGJSONObject dealMid(String mid,String chan,int msgState) {
        if (null == mid || "".equals(mid) || "null".equals(mid) || mid.length() < 1){
            return null;
        }
        try {
            long now_t = System.currentTimeMillis();
            ZGJSONObject infoObject = new ZGJSONObject();
            switch (msgState) {
                case MSG_RECV:
                    infoObject.put("dt", "mrecv");
                    break;
                case MSG_READ:
                    infoObject.put("dt", "mread");
                    break;
                default:
                    return null;
            }
            JSONObject pr = new JSONObject();
            pr.put("$tz",Integer.toString(Utils.getTimeZone()));
            pr.put("$ct",now_t);
            pr.put("$channel", chan);
            pr.put("$mid", mid);
            infoObject.put("pr",pr);
            return infoObject;
        } catch (Exception e) {
            ZGLogger.handleException(TAG,"通知信息出错",e);
        }
        return null;
    }

    ZGJSONObject buildException(Thread thread, Throwable e, boolean isForgound){
        try {
            ZGJSONObject pr = dataCommon();
            pr.put("$异常名称",e.getClass().getCanonicalName());
            pr.put("$异常描述",e.getLocalizedMessage());
            pr.put("$异常进程名称",myProcessName+":"+thread.getName());
            pr.put("$应用包名",packageName);
            pr.put("$前后台状态",isForgound?"前台":"后台");
            pr.put("$CPU架构",Build.CPU_ABI);
            pr.put("$ROM",Build.DISPLAY);
            Throwable cause = e.getCause();
            StringBuilder builder = new StringBuilder();
            if (cause != null){
                fillStackTrace(builder,cause);
            }else {
                fillStackTrace(builder,e);
            }
            pr.put("$出错堆栈",builder.toString());
            ZGJSONObject event = new ZGJSONObject();
            event.put("dt",EVENT_TYPE_EXCEPTION);
            pr.put("$sid",sessionID);
            pr.put("$net",Integer.toString(connectivityUtils.getNetworkType()));
            pr.put("$mnet",Integer.toString(tm.getNetworkType()));
            pr.put("$ov", DeviceInfoUtils.getOSVersion());
            pr.put("$eid","崩溃");
            event.put("pr",pr);
            return event;
        }catch (Exception ec){
            e.printStackTrace();
        }
        return null;
    }

    private void fillStackTrace(StringBuilder builder,Throwable cause){
        StackTraceElement[] causeStackTrace = cause.getStackTrace();
        int sum=0;
        if (causeStackTrace != null){
            for (StackTraceElement element : causeStackTrace){
                String className = element.getClassName();
                String methodName = element.getMethodName();
                String lineNumber = String.valueOf(element.getLineNumber());
                sum = sum+className.length()+methodName.length()+lineNumber.length()+5;
                if (sum>256) {
                    break;
                }
                builder.append(className).append(" ");
                builder.append(methodName).append(" ");
                builder.append(lineNumber).append(" ");
                builder.append("\n ");
            }
        }
    }

    public JSONObject buildScreenshot(ZGCore.ScreenshotInfo info) {
        if (null == info){
            return null;
        }
        if (screenSize == null){
            return null;
        }
        if (RSA_key == null){ //RSA加密密文为空
            if (key == null){
                key = Utils.getAESKey();
            }
            if (key != null){
                RSA_key = Utils.publicKeyEncodeRSA(key.getEncoded());
            }
            if (RSA_key == null){
                return null;
            }
        }
        long druNew = info.pageStayTime / 1000 + 1;
        try {
            JSONObject mes = new JSONObject();
            JSONObject pr = new JSONObject();
            mes.put("dt","zgsee");
            if (cuid!=null){
                pr.put("$cuid",cuid);
            }
            pr.put("$av",appVersion);
            pr.put("$pn",info.pageName);
            long time = System.currentTimeMillis();
            pr.put("$ct",time);
            pr.put("$eid",info.eid);
            pr.put("$page",info.pageUrl);

            pr.put("$rd",info.actionTime/1000.0);
            pr.put("$sc",++sessionSeeCount);
            globalSP.edit().putInt(Constants.SP_SEE_COUNT,sessionSeeCount).apply();
            JSONArray array;
            if (info.points == null){
                array = new JSONArray();
            }else {
                array = info.points;
            }
            pr.put("$pel", array);
            pr.put("$sid",sessionID);
            pr.put("$dru",druNew);
            pr.put("$br", DeviceInfoUtils.getBrand());
            pr.put("$dv", DeviceInfoUtils.getDevice());
            int networkType = connectivityUtils.getNetworkType();
            pr.put("$net",Integer.toString(networkType));
            pr.put("$os","Android");
            pr.put("$wh",screenSize);
            pr.put("$ov", DeviceInfoUtils.getOSVersion());
            pr.put("$cr",cr);
            double ival = 0;
            if (firstScreenShotTime < 0){
                firstScreenShotTime = time;
                globalSP.edit().putLong(Constants.SP_FIRST_SCREENSHOT_TIME,firstScreenShotTime).apply();
            }else {
                ival = (time - firstScreenShotTime)/1000.0;
            }
            Log.e(TAG,"ival is "+ival);
            pr.put("$ival",Double.toString(ival));
            pr.put("$gap",info.gap);
            pr.put("$pix",info.screenshot);
            pr.put("$mosaic",info.mosaicViewArray);
            pr.put("$client",this.mosaic);
            byte[] compress = Utils.compress(pr.toString().getBytes("UTF-8"));
            String s = Utils.AESEncrypt(key, compress);
            mes.put("key",RSA_key);
            mes.put("pr",s);
            mes.put("rsa-md5",RSAMd5);
            return mes;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 设置本地zhuge see state，如果之前未设置过SP_ENABLE_ZGSEE
     * 则在sp中将其设置为true，否则更改为false
     *
     */
    boolean updateZGSeeState() {
        if (!localZGSeeState){
            globalSP.edit().putBoolean(Constants.SP_ENABLE_ZGSEE,true).apply();
            return localZGSeeState = true;
        }
        return false;
    }

    JSONObject buildAutoTrackEvent(JSONObject pro){
        if (pro == null || pro.length() == 0){
            return null;
        }
        if (!pro.has("$eid")){
            ZGLogger.logError(TAG,"不合法的autoTrack事件"+pro.toString());
        }
        JSONObject data = new ZGJSONObject();
        try {
            ZGJSONObject pr = dataCommon();
            data.put("dt", EVENT_TYPE_EXCEPTION);
            pr.put("$sid", sessionID);
            Iterator keys = pro.keys();
            while (keys.hasNext()){
                String key = String.valueOf(keys.next());
                pr.put(key,pro.get(key));
            }
            data.put("pr",pr);
        }catch (Exception e){
            ZGLogger.handleException(TAG,"pv error.",e);
        }
        return data;
    }
}
