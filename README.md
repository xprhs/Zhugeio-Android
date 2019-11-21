---
 诸葛Android集成开发指南
---

##1. 集成准备

###导入诸葛SDK

您可以用下面的方法之一进行添加：

1.使用AndroidStudio自动导入

在app的`build.gradle`文件中添加
	
	dependencies {
    	compile 'com.zhuge.analysis:zhugeio:latest.integration'
	}

添加完毕之后重新build工程即可

2.导入jar文件

  * [下载SDK](http://sdk.zhugeio.com/Zhuge_Android_SDK.zip)
  * 解压文件，将`zhugesdk_version.jar`添加到工程的libs目录下

  * 在app的`build.gradle`文件中添加：
  
	``` 
	dependencies {
		compile files('libs/zhugesdk_version.jar')
	}
	```
	
##2. 基本功能集成

###2.1 添加权限与AppKey

诸葛的统计功能需要以下权限：

```
<!--需要网络权限-->
<uses-permission 
	android:name="android.permission.INTERNET"/>
<!--需要获取网络状态-->
<uses-permission 
	android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission 
	android:name="android.permission.ACCESS_WIFI_STATE"/>
<!--获取设备唯一标识-->
<uses-permission 
	android:name="android.permission.READ_PHONE_STATE"/>
```

应用的AppKey与统计的渠道导入可以通过两种方式：

*  在Manifest文件中配置

  在你的应用`AndroidManifest.xml`文件的`application`节点下为应用配置AppKey和渠道名称：

``` XML
<meta-data
	android:value="此处填写您在诸葛申请的App Key" android:name = "ZHUGE_APPKEY"/>   
<meta-data 
	android:value="自定义您的渠道名称（如：豌豆荚）" android:name = "ZHUGE_CHANNEL"/>
```
* 通过代码集成

   如果希望在代码中配置Appkey、Channel信息，请在启动统计时调用如下方法：
   
```java
ZhugeSDK.getInstance().init(Context context,String appKey,String channel);
```

参数说明：

context: 应用上下文

appKey : 官网申请的AppKey

channel: 应用渠道

###2.2 启动统计

为了正确启动诸葛的统计功能，您需要在应用的入口Activity的onCreate方法中调用如下方法：

```java
ZhugeSDK.getInstance().init(Context context);
```

***注意：init(Context context)默认从Manifest中获取AppKey及Channel.如果您选择了通过代码配置AppKey的方式，那么此处应该调用包含AppKey，Channel的init(Context context,String appKey, String appChannel),具体接口说明参见添加权限与AppKey的介绍。两种init是互斥的，后调用的会覆盖之前传入的AppKey.***

init方法只需调用一次即可。

###2.3 打开日志

在init之前调用如下接口来启动日志输出：

```
ZhugeSDK.getInstance().openLog();
```

通过如下接口来设置日志输出的级别：

```
ZhugeSDK.getInstance().setLogLevel(int level);
```

参数说明：

* level 

  日志输出的级别，Log.VERBOSE,Log.DEBUG,Log.INFO,Log.WARN,Log.ERROR。
   默认为Log.INFO;

## 3.识别用户

为了保持对用户的跟踪，你需要为他们记录一个识别码，你可以使用用户id、email等唯一值来作为用户的识别码。另外，你可以在跟踪用户的时候， 记录用户更多的属性信息，便于你更了解你的用户：

```
ZhugeSDK.getInstance().identify(Context context, String uid,JSONObject pro);

```
参数说明：

context 应用上下文

uid     用户唯一标识

pro     用户属性

*  代码示例：

``` Java
//定义用户识别码
String userid = user.getUserId();

//定义用户属性
JSONObject personObject = new JSONObject();
personObject.put("avatar", "http://tp4.sinaimg.cn/5716173917/1");
personObject.put("name", "张三");
personObject.put("gender", "男");
personObject.put("等级", 90);

//标识用户
ZhugeSDK.getInstance().identify(getApplicationContext(),userid,
       personObject);
```

## 4.自定义事件

在您希望记录事件的部分，调用如下代码：

```
ZhugeSDK.getInstance().track(Context context,String eventName,JSONObject pro);
```

参数说明：

context 应用上下文

uid     事件名称

pro     事件属性

如果您只希望统计事件的次数，那么可以调用另一个接口:

```
ZhugeSDK.getInstance().track(Context context,String eventName);
```

代码示例：

``` Java
//定义与事件相关的属性信息  
JSONObject eventObject = new JSONObject();
eventObject.put("分类", "手机");
eventObject.put("名称", "iPhone6 plus 64g");  

//记录事件
ZhugeSDK.getInstance().track(getApplicationContext(), "购买", 
        eventObject);
```

## 5.时长事件的统计

若您希望统计一个事件发生的时长，比如视频的播放，页面的停留，那么可以调用如下接口来进行：

```
ZhugeSDK.getInstance().startTrack(String eventName);
```
说明：调用`startTrack()`来开始一个事件的统计，eventName为一个事件的名称

```
ZhugeSDK.getInstance().endTrack(String eventName,JSONObject pro);
```

说明：调用`endTrack()`来记录事件的持续时长。调用`endTrack()`之前，相同eventName的事件必须已经调用过`startTrack()`，否则这个接口并不会产生任何事件。

代码示例：

```
//视频播放开始
ZhugeSDK.getInstance().startTrack("观看视频");
...
//视频观看结束
JSONObject pro = new JSONObject();
pro.put("名称","非诚勿扰");
pro.put("期数","2016-11-02");
ZhugeSDK.getInstance().endTrack("观看视频",pro);
```
***注意：***startTrack()与endTrack()必须成对出现（eventName一致），单独调用一个接口是无效的。

## 6.在WebView中进行统计

如果你的页面中使用了**WebView**嵌入HTML,js 的代码，并且希望统计HTML中的事件，那么可以通过下面的文档来进行跨平台的统计。注意如果你的HTML是运行在浏览器的，那么还是无法统计的，下文仅针对使用**WebView**加载网页的情况。

* java代码集成

  首先要找到您的WevView对象，并做如下处理：
  
  ```java 
  WebView webView = (WebView) findViewById(R.id.web);
  webView.getSettings().setJavaScriptEnabled(true);
  webView.addJavascriptInterface(new ZhugeSDK.ZhugeJS(),"zhugeTracker");
  ```
  
* js代码统计

  集成了Java代码之后，您就可以在js代码中进行统计：
  
  ```js
  var name = 'click';
  var pro = {'名称':'iPhone',
  				'分类':'手机'
  				};
  zhugeTracker.trackProperty(name,JSON.stringify(pro)); 
  ```
  
  类似的，用户标识可以这样：
  
  ```
  var uid = '123@11.com';
  var pro = {'name':'Jack',
  				'gender':'male'
  				};
  zhugeTracker.identifyProperty(uid,JSON.stringify(pro));
  
  ```
  
参数说明：

uid     名称

pro     属性，需转化为String类型。
  
  
##7. 设置自定义属性

* 事件自定义属性

```
 ZhugeSDK.getInstance().setSuperProperty(JSONObject pro);
```
若有一些属性对于您来说，每一个事件都要拥有，那么您可以调用``setSuperProperty() ``将它传入。之后，每一个经过track(),endTrack()传入的事件，都将自动获得这些属性。

* 设备自定义属性

```
 ZhugeSDK.getInstance().setPlatform(JSONObject pro);
```

诸葛默认展示的设备信息包含一些硬件信息，如系统版本，设备分辨率，设备制造商等。若您希望在展示设备信息时展示一些额外的信息，那么可以调用``setPlatform()``传入，我们会将这些信息添加在设备信息中。

## 其他可选API

*  `ZhugeSDK.getInstance().getDid()`  您可以通过这个接口来获取当前设备在诸葛体系下的设备标识

* `ZhugeSDK.getInstance().getSid()`  您可以通过这个接口来获得当前应用所属的会话ID

* `ZhugeSDK.getInstance().flush()`  

    应用通过诸葛统计的数据，都是先存储在设备上。当应用启动或者设备上存储的事件大于等于5条时，会尝试进行上传。若您想尽快的发送数据，那么可以调用`flush()`来进行一次数据发送。
    
* 实时调试

	你可以使用诸葛io提供的**实时调试**功能来查看实时布点数据，并确认是否准确

使用方法：
	
在诸葛统计初始化之前，调用如下代码，以开启实时调试（注意：建议仅在测试设备上开启）：

``` Java
ZhugeSDK.getInstance().openDebug();
```

  然后在诸葛io中打开**实时调试**页面，即可实时查看上传的数据.

