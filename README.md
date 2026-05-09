安卓蓝牙通信 APP
一个使用 Java 开发的 Android 蓝牙通信APP，适合作为 Android 蓝牙开发的入门参考。

功能
扫描周围蓝牙设备并显示列表
点击设备进行连接
发送和接收文本数据
服务端模式（等待其他设备连接）

开发环境
软件：Android Studio	Panda 2025.3.2
语言：Java
Minimum SDK：API 26（Android 8.0）

项目结构
app/src/main/
├── java/com/example/bluetooth/
│   └── MainActivity.java        # 主界面逻辑
├── res/layout/
│   └── activity_main.xml        # 界面布局
└── AndroidManifest.xml          # 权限声明

测试方法
有蓝牙模块（HC-05等）： 点击"扫描蓝牙设备"，找到模块后点击连接，即可收发数据。
无硬件： 用两台手机测试，一台安装本APP点击"等待连接（服务端）"，另一台安装蓝牙串口APP连接即可。
