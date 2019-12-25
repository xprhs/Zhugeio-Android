# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/paramiao/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keep class  com.zhuge.analysis.stat.ZhugeSDK$ZhugeJS{
   *;
}
-keepattributes Signature,InnerClasses,Deprecated,*Annotation*
-keepattributes Signature,InnerClasses,Deprecated,*Annotation*
-keepattributes EnclosingMethod,JavascriptInterface
-keepattributes Exceptions,SourceFile,LineNumberTable
-keep class com.zhuge.analysis.stat.ZhugeSDK{*;}
-keep enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembernames,allowoptimization enum * {*;}
-keep class com.zhuge.analysis.stat.ZhugeParam{*;}
-keep class com.zhuge.analysis.deepshare.DeepShare { *; }
-keep class com.zhuge.analysis.listeners.ZhugeInAppDataListener { *; }
-keep class  com.zhuge.analysis.stat.ZhugeParam$Builder{
   *;
}
