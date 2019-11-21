package com.zhuge.analysis.stat;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;

import com.zhuge.analysis.util.AutoTrackUtils;
import com.zhuge.analysis.util.DeviceInfoUtils;
import com.zhuge.analysis.util.ZGLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * 应用Activity生命周期回调
 * Created by Omen on 16/6/17.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
@SuppressLint("PrivateApi")
public class ZhugeCallbacks implements Application.ActivityLifecycleCallbacks, ViewTreeObserver.OnGlobalLayoutListener, Handler.Callback {

    private static final String TAG = "com.zhuge.CallBack";
    private ZGCore mCore;
    private View rootView;
    private Method getListenerInfo;
    private String currentActivityName;
    private String currentActivityUrl;
    private newRecordTouchListener mListener;
    private Handler mainHandler;
    private WeakHashMap<View,Boolean> viewCollection;

    private long startTime;
    private float density;
    private long lastDown = 0L;

    private boolean keyboardShow = false;

    private JSONArray editableTextRects;
    private int currentSize = 0;
    private boolean mosaicIsOk = false;
    private String url = "";
    private String title = "";

    public ZhugeCallbacks(ZGCore core){
        mCore = core;
        mainHandler = new Handler(Looper.getMainLooper(),this);
        try {
            getListenerInfo = View.class.getDeclaredMethod("getListenerInfo");
            getListenerInfo.setAccessible(true);
            mListener = new newRecordTouchListener();
            viewCollection = new WeakHashMap<>();
            editableTextRects = new JSONArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        startTime = System.currentTimeMillis();
        currentActivityName = activity.getLocalClassName();
        mCore.onEnterForeground("resu_"+ currentActivityName);
        mCore.pageName = currentActivityName;
        currentActivityUrl = activity.getClass().getCanonicalName();
        rootView = activity.getWindow().getDecorView().getRootView();
        if (density == 0){
            float[] size = DeviceInfoUtils.getScreenDensity(activity);
            if (size != null){
                density = size[2];
                mCore.setScreenSize((int) size[0],(int) size[1]);
            }
        }
        traverseRootView((ViewGroup) rootView);
        ViewTreeObserver viewTreeObserver = rootView.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(this);
        if (!ZhugeSDK.getInstance().isEnableAutoTrack()){
            return;
        }
        url = activity.getClass().getCanonicalName();
        title = AutoTrackUtils.getActivityTitle(activity);
        try {
            JSONObject object = new JSONObject();
            object.put("$url",url);
            object.put("$page_title",title);
            object.put("$eid","pv");
            mCore.sendObjMessage(Constants.MESSAGE_AUTO_TRACK,object);
        }catch (Exception e){
            //ignore
        }
//        if (!mainHandler.hasMessages(0)){
//            mainHandler.sendEmptyMessageDelayed(0,1000);
//        }
//        if (!mainHandler.hasMessages(1)){
//            mainHandler.sendEmptyMessageDelayed(1,2000);
//        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        mCore.onExitForeground(activity.getLocalClassName());
        View rootView = activity.getWindow().getDecorView().getRootView();
        ViewTreeObserver viewTreeObserver = rootView.getViewTreeObserver();
        if (viewTreeObserver.isAlive()){
            viewTreeObserver.removeGlobalOnLayoutListener(this);
        }
        this.rootView = null;
        currentSize = 0;
        if (mainHandler.hasMessages(1)){
            mainHandler.removeMessages(1);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        mainHandler.removeMessages(0);
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }

    @Override
    public void onGlobalLayout() {
        if (rootView == null || !mCore.appInfo.isZGSeeEnable()){
            ZGLogger.logMessage(TAG,"onGlobalLayout"+" ,  return enableZGSee is "+mCore.appInfo.isZGSeeEnable());
            return;
        }
        currentSize = 0;
        detectKeyboardShow();
        traverseRootView((ViewGroup) rootView);
    }

    /**
     * 判断是否是键盘弹出
     */
    private void detectKeyboardShow() {
//        Rect r = new Rect();
//        rootView.getWindowVisibleDisplayFrame(r);
//        int i = r.bottom - r.top;
//        if (screenHeight < i) {
//            screenHeight = i;
//        }
//        boolean old = keyboardShow;
//        keyboardShow = i < screenHeight && (screenHeight - i) > 200;
//        if (!old && keyboardShow){
//            String s = mListener.mCachedBitmap.tackScreenshot(rootView);
//            if (s == null){
//                lastDown = 0L;
//                return;
//            }
//            long time = System.currentTimeMillis();
//            long ac = lastDown > 0? time - lastDown:0;
//            double gap = ac / 1000.0;
//            lastDown = time;
//            long dru = time - startTime;
//            ZGCore.ScreenshotInfo info = new ZGCore.ScreenshotInfo();
//            info.setPageStayTime(dru);
//            info.setGap(gap);
//            info.setEid("2");
//            info.setPageUrl(currentActivityUrl);
//            info.setPageName(currentActivityName);
//            info.setScreenshot(s);
//            if (firstScreenshot == 0){
//                firstScreenshot = time;
//            }else {
//                long l = time - firstScreenshot;
//                info.setIval((l / 1000 + 1));
//            }
//            mCore.sendScreenshot(info);
//        }
    }

    private void addOnTouchListenerForView(View view){
        try {
            if (viewCollection.containsKey(view)){
                return;
            }
            Object invoke = getListenerInfo.invoke(view);
            Field mOnTouchListener = invoke.getClass().getDeclaredField("mOnTouchListener");
            mOnTouchListener.setAccessible(true);
            View.OnTouchListener o = (View.OnTouchListener) mOnTouchListener.get(invoke);
            mListener.setViewAndListenerMap(view,o);
            view.setOnTouchListener(mListener);
            viewCollection.put(view,true);
        }  catch (Exception e) {
            ZGLogger.handleException(TAG,"add on touch listener error.",e);
        }
    }


    private void traverseRootView(ViewGroup parent) {

        for (int i = 0, count = parent.getChildCount(); i < count; i++) {
            View view = parent.getChildAt(i);
            if (view == null) {
                continue;
            }
            if (view instanceof ViewGroup) {
                traverseRootView((ViewGroup) view);
            }
            addOnTouchListenerForView(view);
            getEditableView(view);
        }
    }


    @Override
    public boolean handleMessage(Message msg) {

        if (keyboardShow ||!mCore.appInfo.isZGSeeEnable()){
            return false;
        }
        String s;
        if (mCore.appInfo.needMosaic() && !mosaicIsOk){
            s = null;
        }else {
            s = mListener.mCachedBitmap.getBase64StringFromView(rootView,mCore.appInfo.needMosaic(),editableTextRects,currentSize);
        }
        if (s == null){
            lastDown = 0L;
            return false;
        }
        long time = System.currentTimeMillis();
        long ac = lastDown > 0? time - lastDown:0;
        double gap = ac / 1000.0;
        lastDown = time;
        long dru = time - startTime;
        ZGCore.ScreenshotInfo info = new ZGCore.ScreenshotInfo();
        info.setPageStayTime(dru);
        info.setGap(gap);
        info.setEid("zgsee-change");
        info.setPageName(currentActivityName);
        info.setPageUrl(currentActivityUrl);
        info.setScreenshot(s);
        JSONArray jsonArray = cloneEditableLocation();
        info.setMosaicViewArray(jsonArray);
        mCore.sendScreenshot(info);
        return false;
    }

    private void getEditableView(View view){
        if (view instanceof EditText){
            Log.e(TAG,"view is editable "+view.getClass().getCanonicalName());
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            int width = view.getWidth();
            int height = view.getHeight();
            int length = editableTextRects.length();
            if (width == 0 && height ==0 && location[0] == 0){
                mosaicIsOk = false;
                return;
            }
            if (!mosaicIsOk){
                mosaicIsOk = true;
            }

            JSONObject object;
            try {
                if (currentSize >= length){
                    object = new JSONObject();
                    editableTextRects.put(object);
                    Log.e(TAG,"新建一个item");
                }else {
                    object = editableTextRects.getJSONObject(currentSize);
                    Log.e(TAG,"复用item");
                }
                Log.e(TAG,"put location "+object.toString() +" , on index "+currentSize);
                currentSize++;
                object.put("x",location[0]);
                object.put("y",location[1]);
                object.put("w",width);
                object.put("h",height);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private JSONArray cloneEditableLocation(){
        JSONArray editableViewLocation = new JSONArray();
        try {
            for (int i=0;i<currentSize;i++){
                JSONObject jsonObject = editableTextRects.getJSONObject(i);
                JSONObject item = new JSONObject();
                int x = (int) (jsonObject.optInt("x")/density);
                int y = (int) (jsonObject.optInt("y")/density);
                int w = (int) (jsonObject.optInt("w")/density);
                int h = (int) (jsonObject.optInt("h")/density);
                item.put("x",x);
                item.put("y",y);
                item.put("w",w);
                item.put("h",h);
                editableViewLocation.put(item);
            }
        }catch (Exception e){
            ZGLogger.handleException(TAG,"cloneEditableLocation error",e);
        }
        return editableViewLocation;
    }

    private class newRecordTouchListener implements View.OnTouchListener{

        private WeakHashMap<View,View.OnTouchListener> viewAndListenerMap;
        JSONArray points ;
        /**
         * action down time
         */
        long actionStart;
        String screenshot;

        /**
         * 动作发生时，当前页面停留时间
         */
        long dru;

        int firstFingerID = -1;

        float lastX;
        float lastY;
        String eid;
        private final CachedBitmap mCachedBitmap;

        public newRecordTouchListener(){
            viewAndListenerMap = new WeakHashMap<>();
            mCachedBitmap = new CachedBitmap();
        }


        public void setViewAndListenerMap(View view, View.OnTouchListener listener){
            if (view !=null && listener!=null && !viewAndListenerMap.containsKey(view)){
                viewAndListenerMap.put(view,listener);
            }
        }


        @Override
        public boolean onTouch(View v, MotionEvent event) {
            View.OnTouchListener onTouchListener = viewAndListenerMap.get(v);

            int actionMasked = event.getActionMasked();
            switch (actionMasked){
                case MotionEvent.ACTION_DOWN:
                    if (!mCore.appInfo.isZGSeeEnable()){
                        return onTouchListener != null && onTouchListener.onTouch(v, event);
                    }
                    mainHandler.removeMessages(0);
                    //判断当前是否有截图，有就跳出，没有就返回
                    if (actionStart >0 || keyboardShow){
                        break;
                    }
                    int index = event.getActionIndex();
                    firstFingerID = event.getPointerId(index);
                    points = new JSONArray();
                    actionStart = System.currentTimeMillis();
                    float x = event.getRawX();
                    float y = event.getRawY();
                    int x1 = (int) (x / density);
                    int y1 = (int) (y / density);
                    JSONArray array = new JSONArray();
                    array.put(x1);
                    array.put(y1);
                    points.put(array);
                    if (mCore.appInfo.needMosaic() && !mosaicIsOk){
                        screenshot = null;
                    }else {
                        screenshot = mCachedBitmap.getBase64StringFromView(rootView,mCore.appInfo.needMosaic(),editableTextRects,currentSize);
                    }
                    dru = actionStart - startTime;
                    lastX = x;
                    lastY = y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!mCore.appInfo.isZGSeeEnable()){
                        return onTouchListener != null && onTouchListener.onTouch(v, event);
                    }
                    int actionIndex = event.getActionIndex();
                    int movePointID = event.getPointerId(actionIndex);
                    if (movePointID != firstFingerID){
                        break;
                    }

                    //记录手势点
                    float moveX = event.getRawX();
                    float moveY = event.getRawY();
                    float lengthX = Math.abs(moveX - lastX);
                    float lengthY = Math.abs(moveY - lastY);

                    if (lengthX >100 || lengthY >100){
                        int movePointX = (int) (moveX / density);
                        int movePointY = (int) (moveY / density);
                        JSONArray movePoint = new JSONArray();
                        movePoint.put(movePointX);
                        movePoint.put(movePointY);
                        points.put(movePoint);
                        eid = "zgsee-scroll";
                        lastX = moveX;
                        lastY = moveY;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (ZhugeSDK.getInstance().isEnableAutoTrack()){
                        onViewClick(v);
                    }
                    //发送数据，将当次截图置空，回收
                    if (!mCore.appInfo.isZGSeeEnable()){
                        return onTouchListener != null && onTouchListener.onTouch(v, event);
                    }
                    int upActionIndex = event.getActionIndex();
                    int upPointID = event.getPointerId(upActionIndex);
                    if (upPointID != firstFingerID){
                        break;
                    }
                    long actionTime = System.currentTimeMillis() - actionStart;
                    if (eid == null){
                        eid = "zgsee-click";
                    }
                    float upX = event.getRawX();
                    float upY = event.getRawY();
                    int upPointX = (int) (upX / density);
                    int upPointY = (int) (upY / density);
                    JSONArray upPoint = new JSONArray();
                    upPoint.put(upPointX);
                    upPoint.put(upPointY);
                    points.put(upPoint);
                    long ac = lastDown > 0? actionStart - lastDown:0;
                    double gap = ac / 1000.0;
                    lastDown = actionStart;
                    if (screenshot != null){
                        ZGCore.ScreenshotInfo info = new ZGCore.ScreenshotInfo();
                        info.setScreenshot(screenshot);
                        info.setPageName(currentActivityName);
                        info.setPageUrl(currentActivityUrl);
                        info.setActionTime(actionTime);
                        info.setEid(eid);
                        info.setGap(gap);
                        info.setPageStayTime(dru);
                        info.setPoints(points);
                        JSONArray jsonArray = cloneEditableLocation();
                        info.setMosaicViewArray(jsonArray);
                        mCore.sendScreenshot(info);
                    }

                    points = null;
                    actionStart = 0; //将开始时间重置
                    eid = null;
                    lastX = 0;
                    lastY = 0;
                    firstFingerID = -1;

//                    mainHandler.sendEmptyMessageDelayed(0,1000);
                    break;
                default:
                    break;
            }
            //取出view的touchListener，回调
            return onTouchListener != null && onTouchListener.onTouch(v, event);
        }
        public void onViewClick(View v) {
            if (!ZhugeSDK.getInstance().isEnableAutoTrack()){
                return;
            }
            String viewPath = AutoTrackUtils.getViewPath(v);
            String viewText = AutoTrackUtils.getViewText(v);
            String viewId = null;
            if (v.getId() != View.NO_ID){
                viewId = AutoTrackUtils.getViewIdName(v);
            }
            try {
                JSONObject obj = new JSONObject();
                obj.put("$element_id",viewId);
                obj.put("$element_content",viewText);
                obj.put("$element_selector",viewPath);
                obj.put("$element_type",v.getClass().getSimpleName());
                obj.put("$url",url);
                obj.put("$page_title",title);
                obj.put("$eid","click");
                mCore.sendObjMessage(Constants.MESSAGE_AUTO_TRACK,obj);
            }catch (Exception e){
                e.printStackTrace();
            }

        }
    }

}