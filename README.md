[![Maven Central](https://img.shields.io/maven-central/v/com.herohan/UVCAndroid.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.herohan%22%20AND%20a:%22UVCAndroid%22)

# 基于定制人脸模组的demo 



# 简述 
 由 我修改了 旷视修改的 UVCAndroid 的工程  
 需要找我采购制定的摄像头模组（同行优惠） ，获取闭源的 facepass-release.aar  ，开源的 libuvccamera-release.aar，libuvccamera 可自行打包 也可用我的； 微信QQ 330132662

本方案用于 多设备间 人脸登录的实现 ，在A设备录入人脸后  通过后端保存 faceI_id 、特征值ft、featureBytes ，下发到其他符合
登录权限的设备，通过设备上的SDK (`app/src/main/java/com/herohan/uvcapp/activity/MainActivity.java`的`mFacePass.InsertFeature`)进行用户新增;

q1 为什么不用度目类的离线对比？  
人脸特征值无法同步  ，且很多Android主板不兼容；
q2 为什么不用百度的云端对比？  
有几个项目在用，响应不如离线对比快 ；





打包 旷视程序 `./gradlew :facepass:assembleRelease`
打包 uvc 程序 `./gradlew :libuvccamera:assembleRelease`

### 方式1：生成所有版本（Debug+Release）
./gradlew :你的Lib模块名:assemble

### 方式2：仅生成Release版本（推荐）
./gradlew :你的Lib模块名:assembleRelease

### 示例：Lib模块名为rs485lib
./gradlew :rs485lib:assembleRelease
L9k android uvc & recognize
Library and sample to access UVC camera on non-rooted Android device

#### 以下是原作者的readme 

[中文文档： UVCAndroid，安卓UVC相机通用开发库](https://blog.csdn.net/hanshiying007/article/details/124118486)

How do I use it?
---

### Setup

##### Dependencies
```groovy
repositories {
  mavenCentral()
}

dependencies {
    implementation 'com.herohan:UVCAndroid:1.0.10'
}
```
R8 / ProGuard
-------------

If you are using R8 the shrinking and obfuscation rules are included automatically.

ProGuard users must manually add the below options.
```groovy
-keep class com.herohan.uvcapp.** { *; }
-keep class com.serenegiant.usb.** { *; }
-keepclassmembers class * implements com.serenegiant.usb.IButtonCallback {*;}
-keepclassmembers class * implements com.serenegiant.usb.IFrameCallback {*;}
-keepclassmembers class * implements com.serenegiant.usb.IStatusCallback {*;}
```

Requirements
--------------
Android 5.0+
```Json
{"err_info":0,"fid":2814,"ppl_code":96,"obj_type":0,"track_id":20,"ft":"TUZYMQABCQAAAAAAAAAAAPmdrgYAAAAAEjme/NO90HL4gWZlV3Dj2OJy9B5Ne/XO+XJ7nbfI3zTHNZ1QxfhzRT4eZrU1Ah8R1OiVlIxbc9LVjMn2QCZyOKuhsWFSAFSNp9uRmUllIy1MSmq5gmJ9LoxFlJI1z/O+5vf61bGLhFe4+YHbzZJlVYwI/7wIHwKY/GB99rEWN6OICsZpJfc1naJy7yEtb+XzQdp0uh8XLDmmL5XomdqkatVK+sxN7WJsKF46KCwdcqCh8++3Y4NnUW02yMaH0xrTwcQFM6XGc3wn0gfzv6ssBxPUTMfGCQ6ANAYaD9NMpcLaSDgfRZJNos94QD4T53wk4RRfZxaTHZZalpSMJkxcWg==","ft_len":376,"img":"","img_size":0,"ir_img":"","ir_img_size":0,"attr":{"yaw":-14,"roll":0,"pitch":-16,"blur":0,"live_st":1,"liveness":100},"det_info":{"det_st":0,"fail_index":94,"rect_x":218,"rect_y":517,"rect_w":188,"rect_h":187},"reg_info":{"face_id":0,"id_existed":0,"time_out":0},"iden_info":{"iden_ges":0,"iden_score":93,"stranger":0,"top1_id":1},"ota_info":{"process":0,"err_info":0},"qrcode_info":{"qrcode":"","str_len":0}}

face_id:2  TUZYMQABCQAAAAAAAAAAAPmdrgYAAAAA4SKQ+tyv2ncFrGNlUmzQ0OZjD+myh8j5D2JrZavP0z/EE6m91vp8SMoVao03NTHs1+aiYYFJTDbTheHwtzNiMUS/qH4uG02nt9CZl09lPx+xmptWeEhhKpBHhoY20P2+9+317KqclVK9yZvRMWdYV4fj9b0IAP+ozoZ89LQWwrWHASxXOwTLgKts9Sghb+Pzadd+pQ0AJDWmPY7wbdyhccm7wdRM/pBpC0kzNiofnqyt5fGOULZermTKP9aY6hnS+D4NIrLRcXYJ0hkFoKsiFQj/p+vQ9h6fCwoQ4D9mutL8ocUSRYC0WdxOTjAH/0I39TKtQwKbEWdZioKO3UBaXw==

```