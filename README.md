# PulseRelay

> 将小米运动健康中的实时心率，以低开销 UDP 方式转发给 OBS 或其他局域网接收端。

PulseRelay 是一个针对 **小米运动健康（Mi Fitness / `com.mi.health`）3.58.x** 适配的 LSPosed 模块。模块运行在小米运动健康进程中，从运动/日常心率链路取得 BPM，并通过局域网 UDP 将最新心率转发到一个或多个接收端。

当前版本文档对应 **PulseRelay v3.0.3 UI 系列源码**。核心网络协议为 `PULSE/1` UDP。

---

## 目录

- [功能概览](#功能概览)
- [工作原理](#工作原理)
- [兼容性与要求](#兼容性与要求)
- [快速开始](#快速开始)
- [从源码 Build](#从源码-build)
    - [1. 安装 JDK 17](#1-安装-jdk-17)
    - [2. 安装 Android SDK](#2-安装-android-sdk)
    - [3. 配置 local.properties](#3-配置-localproperties)
    - [4. 可选：配置固定签名](#4-可选配置固定签名)
    - [5. 编译 Debug APK](#5-编译-debug-apk)
    - [6. 安装 APK](#6-安装-apk)
- [LSPosed 配置](#lsposed-配置)
- [如何打开 PulseRelay 设置](#如何打开-pulserelay-设置)
- [PulseRelay 设置说明](#pulserelay-设置说明)
- [运动模式与非运动模式](#运动模式与非运动模式)
- [运动界面悬浮入口](#运动界面悬浮入口)
- [OBS / 接收端配置](#obs--接收端配置)
- [网络测试](#网络测试)
- [UDP 协议](#udp-协议)
- [日志与调试](#日志与调试)
- [常见问题与故障排查](#常见问题与故障排查)
- [升级与配置迁移](#升级与配置迁移)
- [安全说明](#安全说明)
- [项目结构](#项目结构)
- [截图清单](#截图清单)

---

## 功能概览

PulseRelay 目前专注于一件事：**稳定地把实时 BPM 从小米运动健康送到局域网接收端**。

主要功能：

- 适配小米运动健康 3.58.x；
- Hook 运动实时心率；
- 被动 Hook 日常/非运动心率作为 fallback；
- UDP 单播，多目标最多 8 路；
- 可选 UDP 局域网广播；
- `PING / PONG` 连接测试；
- 自动迁移旧版 HTTP 地址到 `IP:端口`；
- 可优先强制 UDP Socket 走 Wi-Fi；
- 运动页面注入可拖动 `♥ + BPM` 悬浮入口；
- 运动中无需退出即可打开快捷面板和完整设置；
- 诊断页面显示 Hook、BPM、UDP 目标、解析 IP、成功/失败次数等信息；
- 不需要登录、注册、UUID、Cookie、HTTP Server 或公网服务。

PulseRelay **不会**长期在手机上开放一个 HTTP/TCP 服务端口。默认架构是手机主动推送，配置更简单，也更适合“最新值优先”的实时心率。

---

## 工作原理

```text
小米手环
   │ Bluetooth
   ▼
小米运动健康 3.58.x
   │
   │ LSPosed Hook
   ▼
PulseRelay
   │
   │ PULSE/1 UDP
   ├──────────────► OBS / Receiver A
   ├──────────────► OBS / Receiver B
   └──────────────► 其他局域网接收端
```

运动状态下，模块优先从实时运动数据链路读取 `heart_rate`；日常状态下，如果小米运动健康产生新的日常心率记录，也可以通过 fallback 链路捕获。

当前保留的主要 Hook 点：

```text
com.xiaomi.fitness.sport_manager.server.SportDataServer#onPhoneDataChanged
com.xiaomi.fitness.sport.model.CommonSportModel#onPhoneDataChanged
com.xiaomi.fitness.sport_eco_manager.server.EcoSportDataServer#onPhoneDataChanged
com.xiaomi.fitness.sport_eco.model.CommonSportModel#onPhoneDataChanged
com.xiaomi.fitness.sport.viewmodel.BaseSportVM#onSuccess
com.xiaomi.fit.fitness.export.data.aggregation.DailyHrReport#setLatestHrRecord
```

---

## 兼容性与要求

### Android / 模块端

| 项目 | 当前工程配置 |
| --- | --- |
| 目标应用 | 小米运动健康 / `com.mi.health` |
| 已针对版本 | 3.58.x |
| 模块包名 | `website.xihan.pbra` |
| 模块显示名 | PulseRelay |
| LSPosed/Xposed 最低 API | 90 |
| Android `minSdk` | 26 |
| Android `targetSdk` | 35 |
| Android `compileSdk` | 35 |
| Java / JVM | 17 |
| Kotlin | 2.3.10 |
| Android Gradle Plugin | 8.13.2 |
| Gradle Wrapper | 9.3.1 |

> `minSdk 26` 只是模块工程的最低 API。实际是否能运行，还取决于你安装的小米运动健康版本和 LSPosed 环境。

### 电脑 / 接收端

需要：

- 手机和电脑能够通过局域网互相访问；
- OBS 端使用支持 `PULSE/1 UDP` 的接收插件，或自行实现协议；
- 默认监听 UDP 端口：`18181`；
- Windows 防火墙允许对应 UDP 入站。

---

# 快速开始

如果你已经有可用的 Android SDK、JDK 17、LSPosed 和小米运动健康 3.58.x，最快流程如下。

### 1. 配置 Android SDK

在项目根目录新建：

```text
local.properties
```

例如：

```properties
sdk.dir=D:/DevEnv/Android/Sdk
```

### 2. Build

PowerShell：

```powershell
.\gradlew.bat clean assembleDebug
```

成功后 APK：

```text
app\build\outputs\apk\debug\app-debug.apk
```

### 3. 安装

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

### 4. LSPosed 作用域

在 LSPosed：

```text
模块
└─ PulseRelay
   └─ 勾选 小米运动健康 / com.mi.health
```

然后强制停止小米运动健康：

```powershell
adb shell am force-stop com.mi.health
```

重新打开小米运动健康。

### 5. 打开设置

进入小米运动健康的 **关于页面**，长按页面中的图标。

如果当前版本 UI 结构导致图标未命中，模块会尝试回退到长按关于页面空白区域。

### 6. 添加电脑

例如电脑 IP 是 `192.168.100.20`，OBS UDP 端口是 `18181`：

```text
192.168.100.20:18181
```

也可以只写：

```text
192.168.100.20
```

省略端口时默认 `18181`。

### 7. 先做 PING

点击：

```text
PING 全部接收端
```

应看到：

```text
PONG
```

### 8. 发送 123 BPM 测试

点击：

```text
发送测试心率 123 BPM
```

OBS 应立即显示 `123 BPM`。

### 9. 开始运动

建议手环开启“自由训练 / 跑步 / 步行”等运动模式。

运动页面右侧会出现 PulseRelay 的 `♥ + BPM` 快捷悬浮入口。

---

# 从源码 Build

## 1. 安装 JDK 17

本项目源码和 Gradle 配置使用 **Java 17**。

检查：

```powershell
java -version
```

建议输出中包含：

```text
17
```

如果电脑同时安装了多个 JDK，可临时指定：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
```

> Gradle 9.x 对 Java 版本要求较新。不要用 Java 8 构建本项目。

---

## 2. 安装 Android SDK

至少需要：

```text
Android SDK Platform 35
Android SDK Build-Tools 35.x
Android SDK Platform-Tools
Android SDK Command-line Tools
```

如果安装了 Android Studio：

```text
Android Studio
→ Settings
→ Android SDK
```

确认 Android 35 已安装。

项目第一次 Build 时，Gradle 也可能自动安装缺少的 Platform / Build-Tools，只要许可证已接受且网络可用。

---

## 3. 配置 local.properties

项目不会把个人电脑的 Android SDK 路径提交到仓库，所以需要自己创建：

```text
<项目根目录>\local.properties
```

推荐使用 `/`：

```properties
sdk.dir=D:/DevEnv/Android/Sdk
```

例如 Android Studio 默认路径也可能是：

```properties
sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk
```

也可以配置环境变量：

```powershell
[Environment]::SetEnvironmentVariable(
    "ANDROID_HOME",
    "D:\DevEnv\Android\Sdk",
    "User"
)
```

不过对本项目来说，`local.properties` 最直观。

### 验证 SDK

```powershell
Test-Path "D:\DevEnv\Android\Sdk\platforms\android-35"
```

返回 `True` 即说明 Platform 35 存在。

---

## 4. 可选：配置固定签名

### Debug 测试可以不配置

如果项目根目录没有：

```text
keystore.properties
```

`assembleDebug` 可以使用 Android 默认 Debug 签名。

### 为什么建议长期使用固定签名

如果每次构建使用不同签名，更新 APK 时可能出现：

```text
INSTALL_FAILED_UPDATE_INCOMPATIBLE
```

长期使用建议生成自己的 keystore。

### 生成 keystore

在项目根目录：

```powershell
keytool -genkeypair `
  -v `
  -keystore pulserelay.jks `
  -alias pulserelay `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

然后新建：

```text
keystore.properties
```

示例：

```properties
storePassword=你的Store密码
keyPassword=你的Key密码
keyAlias=pulserelay
storeFile=../pulserelay.jks
```

说明：`app/build.gradle.kts` 中使用的是模块项目路径，因此如果 `pulserelay.jks` 放在仓库根目录，通常写：

```properties
storeFile=../pulserelay.jks
```

> **不要把真实 keystore、密码或 `keystore.properties` 上传到公开仓库。**

---

## 5. 编译 Debug APK

在项目根目录：

```powershell
.\gradlew.bat clean assembleDebug
```

也可以使用项目脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1
```

成功时应看到：

```text
BUILD SUCCESSFUL
```

APK：

```text
app\build\outputs\apk\debug\app-debug.apk
```

### Gradle Wrapper 下载超时

本项目使用：

```text
Gradle 9.3.1
```

`gradle-wrapper.properties` 已将下载超时提高到：

```properties
networkTimeout=120000
```

如果仍无法下载，可手动下载：

```text
gradle-9.3.1-bin.zip
```

然后临时把 `distributionUrl` 改为本地文件，例如：

```properties
distributionUrl=file\:///D:/Gradle/gradle-9.3.1-bin.zip
```

构建成功后建议恢复官方 URL，避免误提交个人路径。

---

## 6. 安装 APK

### ADB 安装

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

或者：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-debug.ps1
```

安装脚本还会执行：

```text
adb shell am force-stop com.mi.health
```

这样下次打开小米运动健康时会重新注入模块。

### 签名不同导致无法覆盖

如果出现：

```text
INSTALL_FAILED_UPDATE_INCOMPATIBLE
```

说明旧版 APK 与当前 APK 签名不同。

需要先卸载旧版：

```powershell
adb uninstall website.xihan.pbra
```

再安装：

```powershell
adb install .\app\build\outputs\apk\debug\app-debug.apk
```

> 卸载可能清除模块自己的 SharedPreferences 配置。升级前建议记下 UDP 目标。

---

# LSPosed 配置

PulseRelay 本身没有普通 Launcher 主界面，它是一个 LSPosed 模块。

安装 APK 后：

1. 打开 LSPosed Manager；
2. 进入“模块”；
3. 找到 **PulseRelay**；
4. 启用模块；
5. 作用域勾选：

```text
小米运动健康
com.mi.health
```

6. 强制停止并重新打开小米运动健康。

```powershell
adb shell am force-stop com.mi.health
```

> **截图位置预留：LSPosed 作用域**  
> 建议文件：`docs/images/01-lsposed-scope.png`
>
> <!-- ![LSPosed 作用域](docs/images/01-lsposed-scope.png) -->

### 如何确认注入成功

LSPosed 日志中应看到类似：

```text
Mi Health process started: com.mi.health
Heart-rate hooks installed: ...
Sport activity quick-entry hooks installed
```

---

# 如何打开 PulseRelay 设置

PulseRelay 没有独立启动 Activity。设置界面直接注入小米运动健康。

### 普通状态

进入：

```text
小米运动健康
→ 关于
→ 长按页面图标
```

模块会给关于页面里的图片控件安装长按入口。

如果当前小米运动健康 UI 结构发生变化，没有找到图标，会尝试回退到：

```text
长按“关于”页面空白区域
```

> **截图位置预留：关于页面设置入口**  
> 建议文件：`docs/images/02-settings-entry.png`
>
> <!-- ![关于页面设置入口](docs/images/02-settings-entry.png) -->

### 运动状态

进入运动后不用退出运动。

运动页面会出现可拖动：

```text
♥
87
```

点击即可打开运动快捷面板。

---

# PulseRelay 设置说明

> **截图位置预留：完整设置页**  
> 建议文件：`docs/images/03-settings.png`
>
> <!-- ![PulseRelay 设置页](docs/images/03-settings.png) -->

## 启用实时中继

总开关。

关闭后：

- HeartRate Hook 仍然可以采集；
- UI 仍然可以显示当前 BPM；
- 不再向 UDP 接收端发送实时心率。

适合临时停止网络输出，不需要禁用整个 LSPosed 模块。

---

## UDP 接收端

每行一个目标，最多 8 个单播目标。

示例：

```text
192.168.100.20
192.168.100.30:18181
192.168.100.40:18182
```

规则：

- 省略端口 → 默认 `18181`；
- 可以填写主机名；
- 空行忽略；
- `#` 开头的行忽略；
- 重复目标自动去重；
- 最多保留 8 个单播目标。

### 旧 HTTP 地址自动迁移

旧版本可能保存：

```text
http://192.168.100.20:18181/receive_data
```

v3 会自动规范为：

```text
192.168.100.20:18181
```

所以升级时通常不需要重新输入地址。

---

## 局域网 UDP 优先走 Wi-Fi

建议在典型直播局域网环境中开启：

```text
手机 ─ Wi-Fi ─ 路由器 ─ 电脑 OBS
```

开启后，PulseRelay 会尽量把 DatagramSocket 绑定到当前 Wi-Fi 网络，避免手机同时开启 4G/5G 时，系统路由选择错误。

### 建议开启

- 接收端是 `192.168.x.x`；
- 接收端是 `10.x.x.x`；
- 手机与电脑在同一个 Wi-Fi / LAN；
- 不依赖 VPN 虚拟网卡。

### 建议关闭

- Tailscale；
- ZeroTier；
- VPN；
- 其他虚拟网络；
- 开启后 PING 失败，但关闭后正常。

“优先走 Wi-Fi”**不是加速开关**，而是网络路由选择。

---

## 同时发送局域网广播

开启后，除单播目标外，还会向：

```text
255.255.255.255:<广播端口>
```

发送一份实时心率。

适合：

```text
                ┌─ OBS A :18181
手机 PulseRelay ├─ OBS B :18181
                └─ OBS C :18181
```

所有接收端都监听同一个 UDP 端口时，手机无需逐台填写 IP。

### 广播不工作时

部分路由器/AP 会启用：

- AP Isolation；
- Client Isolation；
- 无线客户端隔离；
- Guest Network 隔离。

这种情况下建议关闭广播，改用明确的单播 IP。

---

## 广播端口

默认：

```text
18181
```

范围：

```text
1 - 65535
```

手机广播端口必须和接收端监听端口一致。

---

## 运动页面显示快捷悬浮按钮

开启后，模块会在小米运动健康的运动 Activity `DecorView` 中注入一个可拖动的快捷按钮。

它不是 Android 系统悬浮窗，所以：

- 不需要 `SYSTEM_ALERT_WINDOW`；
- 不跨应用显示；
- 只存在于识别到的运动页面；
- 退出运动 Activity 后自动移除。

---

## 详细调试日志（LSPosed）

建议开发/排错时开启。

会记录：

- Hook 安装；
- 心率回调；
- 心率来源；
- UDP 发送；
- 目标解析；
- PING/PONG；
- 失败原因。

稳定使用后可以关闭以减少日志量。

---

## PING 等待时间

仅影响连接测试，不影响实时心率发送。

范围：

```text
200 - 5000 ms
```

默认：

```text
900 ms
```

Wi-Fi 比较慢时可以调到：

```text
1500 - 2500 ms
```

---

## 相同 BPM 去重窗口

如果短时间连续 Hook 到完全相同的 BPM，可以在一个小窗口内去重，减少无意义 UDP 包。

默认：

```text
300 ms
```

范围：

```text
0 - 5000 ms
```

设为 `0` 基本等价于关闭时间窗口去重。

> 这是“网络发送去重”，不会阻止 UI 更新当前心率。

---

# 运动模式与非运动模式

## 运动模式：推荐直播使用

如果目标是 OBS 实时显示，推荐手环开启运动：

```text
自由训练
跑步
步行
骑行
...
```

运动期间，小米运动健康会更持续地处理实时心率，PulseRelay 也更容易获得高频更新。

UI 中数据来源通常显示：

```text
运动实时
```

## 非运动模式

PulseRelay 保留 `DailyHrReport` 被动 fallback。

当小米运动健康产生新的日常心率记录时，模块可能捕获：

```text
日常心率
```

但非运动状态的采样频率由：

- 手环健康监测设置；
- 小米运动健康同步策略；
- 设备固件；
- 系统后台策略；

共同决定。

因此 **非运动模式不保证秒级实时刷新**，不建议作为直播主要数据源。

---

# 运动界面悬浮入口

> **截图位置预留：运动页面悬浮按钮**  
> 建议文件：`docs/images/04-sport-overlay.png`
>
> <!-- ![运动页面悬浮按钮](docs/images/04-sport-overlay.png) -->

识别到运动 Activity 后，右上附近会出现：

```text
♥
87
```

### 操作

- **点击**：打开快捷面板；
- **拖动**：移动按钮；
- BPM 无数据时显示 `--`；
- 关闭实时中继后按钮会降低透明度。

### 运动快捷面板

> **截图位置预留：运动快捷面板**  
> 建议文件：`docs/images/05-sport-quick-panel.png`
>
> <!-- ![运动快捷面板](docs/images/05-sport-quick-panel.png) -->

快捷面板可查看：

- 当前 BPM；
- 当前来源：运动实时 / 日常心率 / 无数据；
- 最后更新时间；
- UDP 成功/失败统计；
- 当前目标数量；
- 中继启用状态。

快捷操作：

```text
发送测试心率
打开完整设置
返回运动
```

因此运动中无需结束本次运动即可配置模块。

---

# OBS / 接收端配置

PulseRelay v3 使用 UDP，**旧 HTTP 版 OBS 插件不能直接接收 v3 数据**。

接收端至少需要：

```text
监听地址：0.0.0.0
UDP 端口：18181
协议：PULSE/1
```

手机侧填写电脑局域网 IP：

```text
192.168.100.20:18181
```

> **截图位置预留：OBS UDP Receiver 设置**  
> 建议文件：`docs/images/06-obs-udp-receiver.png`
>
> <!-- ![OBS UDP Receiver](docs/images/06-obs-udp-receiver.png) -->

## 如何查看电脑 IP

Windows：

```powershell
ipconfig
```

找到当前 Wi-Fi / Ethernet 的 IPv4 地址，例如：

```text
192.168.100.20
```

不要把这些地址当电脑地址填到手机：

```text
127.0.0.1
localhost
```

在手机里，`127.0.0.1` 指的是**手机自己**。

---

## Windows 防火墙

需要允许对应 UDP 端口入站。

例如：

```powershell
New-NetFirewallRule `
  -DisplayName "PulseRelay UDP 18181" `
  -Direction Inbound `
  -Protocol UDP `
  -LocalPort 18181 `
  -Action Allow `
  -Profile Private
```

如果配套 OBS Receiver 工程包含脚本，也可以使用它的 `open-firewall.ps1`。

建议只开放 **Private** 网络，不要为了图省事开放所有公网网络配置文件。

---

# 网络测试

## 1. PING / PONG

PulseRelay 设置页点击：

```text
PING 全部接收端
```

手机发送：

```text
PULSE/1|PING|<nonce>
```

接收端应回复：

```text
PULSE/1|PONG|<同一个 nonce>
```

正常示例：

```text
✓ 192.168.100.20:18181 · PONG · 8ms · 192.168.100.20
```

广播模式可能显示：

```text
broadcast:18181 · PONG x3
```

表示同网段有 3 个接收器回复。

> **截图位置预留：PING/PONG 测试结果**  
> 建议文件：`docs/images/07-ping-pong.png`
>
> <!-- ![PING/PONG](docs/images/07-ping-pong.png) -->

---

## 2. 发送测试心率 123 BPM

点击：

```text
发送测试心率 123 BPM
```

PulseRelay 会立即发送 HR 数据包。

OBS 应显示：

```text
123 BPM
```

注意：设置页中的：

```text
UDP SENT
```

只表示 Android 网络栈成功接受了这个 UDP 数据报，**不等于对端一定收到**。

需要正向确认时请使用 `PING / PONG`。

---

# UDP 协议

PulseRelay 使用 UTF-8 文本协议。

## 心率

```text
PULSE/1|HR|<seq>|<bpm>|<unix_ms>
```

例如：

```text
PULSE/1|HR|1024|87|1787890000123
```

字段：

| 字段 | 含义 |
| --- | --- |
| `PULSE/1` | 协议版本 |
| `HR` | 数据类型 |
| `1024` | 单调递增序号 |
| `87` | BPM |
| `1787890000123` | 手机测量/捕获时间，Unix 毫秒 |

合法 BPM 范围：

```text
20 - 260
```

接收器建议根据 `seq` / `unix_ms` 丢弃重复或明显晚到的旧包。

## Ping

```text
PULSE/1|PING|<nonce>
```

## Pong

```text
PULSE/1|PONG|<nonce>
```

接收端必须原样返回 nonce。

---

# 日志与调试

## LSPosed 日志

最可靠的模块日志通常在：

```text
LSPosed Manager
→ 日志
→ 模块日志
```

重点搜索：

```text
PulseRelay
Mi Health process started
Hooked:
实时心率
UDP 已发送
UDP 发送失败
PING
PONG
```

## ADB Logcat

项目提供：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\watch-log.ps1
```

脚本执行：

```text
adb logcat -v time PulseRelay:D *:S
```

如果 LSPosed 将模块日志重定向到自己的日志系统，ADB 不一定能看到全部 Xposed 输出，所以排查 Hook 时仍以 LSPosed 模块日志为准。

## 设置页“复制诊断信息”

诊断快照包含：

```text
Android 版本
设备型号
进程名
模块开关
目标列表
广播状态
Wi-Fi 强制路由
运动 Overlay Activity
本机 IP
已安装 Hook 数量
Hook 回调次数
去重次数
最后 BPM
最后来源
最后 Hook 时间
UDP 成功/失败
最后网络状态
解析 IP
最后错误
各目标状态
```

出现问题时，优先复制这段信息再查看 LSPosed 日志。

---

# 常见问题与故障排查

## 快速定位表

| 现象 | 最可能位置 | 先做什么 |
| --- | --- | --- |
| 模块完全没有日志 | LSPosed 注入 | 检查模块启用和 `com.mi.health` 作用域 |
| 设置页可以打开，但一直“无数据” | 心率 Hook | 开启运动，查看 `实时心率[...]` 日志 |
| 有实时 BPM，但 OBS 不变 | UDP 网络/接收器 | 先执行 PING/PONG |
| `UDP SENT`，但 OBS 没收到 | UDP 无确认特性 | 用 PING，不要把 SENT 当 ACK |
| PING timeout | IP/端口/防火墙/隔离 | 检查电脑 IP、UDP 防火墙、AP 隔离 |
| 单播成功、广播失败 | 路由器广播策略 | 使用单播或关闭客户端隔离 |
| 开启“优先 Wi-Fi”失败 | VPN/虚拟网络 | 关闭“优先 Wi-Fi”再测 |
| 运动中没有悬浮按钮 | Activity 匹配/开关 | 检查“运动页面显示快捷悬浮按钮”及日志 |
| APK 无法覆盖旧版 | 签名不同 | 固定 keystore 或卸载旧版后安装 |
| Build 找不到 Android SDK | `local.properties` | 设置 `sdk.dir=...` |
| Gradle 下载超时 | 网络 | 调大 timeout 或使用本地 Gradle ZIP |

---

## 1. 注入成功，但没有任何心率

先开启一个手环运动模式，保持至少十几秒。

LSPosed 日志应出现类似：

```text
实时心率[SportDataServer]: 87
```

如果完全没有：

1. 确认小米运动健康版本；
2. 确认 LSPosed 作用域；
3. 强制停止 `com.mi.health` 后重开；
4. 查看是否有：

```text
Hook target missing
Hook method missing
Hook install failed
```

如果新版本小米运动健康修改了内部类/方法，需要重新适配 Hook 点。

---

## 2. 有 BPM，但 PING 超时

检查手机和电脑是否同一局域网。

电脑：

```powershell
ipconfig
```

手机目标：

```text
电脑IPv4:18181
```

再检查：

- OBS Receiver 是否正在运行；
- Receiver 是否监听 `0.0.0.0`；
- UDP 端口是否一致；
- Windows 防火墙；
- 路由器 AP Isolation；
- 手机是否连了 Guest Wi-Fi。

---

## 3. `UDP SENT` 为什么不是连接成功？

UDP 没有 TCP 那种连接和 ACK。

```text
UDP SENT
```

只表示：

```text
手机成功把数据报交给本机网络栈
```

它无法证明：

```text
路由器转发成功
电脑防火墙放行
OBS 插件收到
```

所以 PulseRelay 专门提供 `PING / PONG` 来做正向验证。

---

## 4. PING 成功，但实时 BPM 不更新

说明网络链路通常是好的，重点看 Hook。

设置页观察：

```text
当前 BPM
最后更新
Hook 来源
```

如果 BPM 长时间不变：

- 尝试进入运动模式；
- 检查手环是否仍与小米运动健康连接；
- 查看 `Hook callbacks` 是否增长。

---

## 5. 运动页面没有悬浮按钮

确认设置中：

```text
运动页面显示快捷悬浮按钮 = 开
```

再查看诊断：

```text
Sport overlay: true @ <Activity>
```

如果 Activity 一直是 `-`，可能该运动类型使用了新的 Activity 路径，需要扩展 `SportOverlay.isSportingActivityName()`。

---

## 6. Build 报 SDK location not found

错误：

```text
SDK location not found
```

创建：

```text
local.properties
```

写入：

```properties
sdk.dir=D:/DevEnv/Android/Sdk
```

---

## 7. Build 报找不到 Android 35

安装：

```text
Android SDK Platform 35
Android SDK Build-Tools 35.x
```

然后重新：

```powershell
.\gradlew.bat clean assembleDebug
```

---

## 8. `Unable to strip libxxx.so`

如果最终是：

```text
BUILD SUCCESSFUL
```

这类 strip 警告通常不是构建失败原因。

以最终 `BUILD SUCCESSFUL / BUILD FAILED` 为准。

---

## 9. Gradle 的 restricted method / deprecated warning

例如：

```text
WARNING: A restricted method in java.lang.System has been called
Deprecated Gradle features were used...
```

如果 Build 最终成功，这些属于工具链警告，不是 PulseRelay 功能故障。

---

# 升级与配置迁移

## v2.x → v3.x

v2.x 使用 HTTP 时可能保存：

```text
http://192.168.100.20:18181/receive_data
```

PulseRelay v3 会自动提取：

```text
192.168.100.20:18181
```

旧 SharedPreferences 名称也被保留，以尽量延续以前的设置。

## 热修复包

如果使用 `apply-hotfix.ps1`：

1. 脚本通常只覆盖本次需要修改的源码；
2. 原文件会备份到项目目录下的时间戳备份文件夹；
3. `local.properties`、keystore 等本机文件不会主动删除；
4. 应用后建议执行：

```powershell
.\gradlew.bat clean assembleDebug
```

## 从旧工程复制本地配置

项目提供：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\import-local-config.ps1 `
  -From D:\Projects\旧工程
```

它会尝试复制：

```text
local.properties
keystore.properties
*.jks
*.keystore
```

---

# 安全说明

当前 `PULSE/1` 是局域网轻量协议：

- 无 TLS；
- 无认证；
- 无加密；
- UDP 广播时，同网段设备可以收到数据；
- 同网段其他设备理论上也可以伪造 `PULSE/1|HR|...` 数据包。

因此建议：

- 只在可信家庭/私人 LAN 使用；
- Windows 防火墙规则限定为 `Private`；
- 公共 Wi-Fi 不建议启用 UDP 广播；
- 不要把 UDP 18181 直接做公网端口映射。

如果以后需要跨互联网传输，应增加：

- Token/HMAC；
- VPN/Tailscale/ZeroTier；
- 或重新设计带认证加密的传输层。

---

# 项目结构

```text
PulseRelay/
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ assets/
│     │  └─ xposed_init
│     ├─ res/
│     │  └─ drawable/
│     │     └─ ic_pulse_relay.xml
│     └─ kotlin/website/xihan/pbra/
│        ├─ AppContext.kt
│        ├─ ConfigDialog.kt
│        ├─ Diagnostics.kt
│        ├─ HeartRateBridge.kt
│        ├─ HookEntry.kt
│        ├─ Log.kt
│        ├─ PulseUdp.kt
│        ├─ Reflect.kt
│        ├─ Settings.kt
│        ├─ SportOverlay.kt
│        └─ SportQuickPanel.kt
├─ gradle/
├─ scripts/
│  ├─ build-debug.ps1
│  ├─ import-local-config.ps1
│  ├─ install-debug.ps1
│  └─ watch-log.ps1
├─ build.gradle.kts
├─ gradle.properties
├─ gradlew
├─ gradlew.bat
├─ settings.gradle.kts
└─ README.md
```

### 核心文件职责

| 文件 | 作用 |
| --- | --- |
| `HookEntry.kt` | LSPosed 入口、安装心率 Hook、设置入口、运动 Activity 生命周期 Hook |
| `HeartRateBridge.kt` | BPM 去重、latest-wins 队列、向多目标发送 |
| `PulseUdp.kt` | UDP 协议、目标解析、Wi-Fi socket、PING/PONG |
| `Settings.kt` | SharedPreferences 配置和旧地址迁移 |
| `Diagnostics.kt` | Hook / BPM / UDP 诊断状态 |
| `ConfigDialog.kt` | 完整设置 UI |
| `SportOverlay.kt` | 运动页面 `♥ + BPM` 悬浮入口 |
| `SportQuickPanel.kt` | 运动中快捷面板 |

---

# 截图清单

README 已预留以下图片位置。建议后续截图后按下面文件名放到：

```text
docs/images/
```

| 文件名 | 建议内容 |
| --- | --- |
| `01-lsposed-scope.png` | LSPosed 模块启用和 `com.mi.health` 作用域 |
| `02-settings-entry.png` | 小米运动健康“关于”页面长按入口 |
| `03-settings.png` | PulseRelay 完整设置页 |
| `04-sport-overlay.png` | 运动界面的 `♥ + BPM` 悬浮按钮 |
| `05-sport-quick-panel.png` | 运动中的 PulseRelay 快捷面板 |
| `06-obs-udp-receiver.png` | OBS UDP Receiver 监听配置 |
| `07-ping-pong.png` | 手机端 PING/PONG 成功结果 |

建议截图：

- 手机截图优先使用原始分辨率；
- 隐去个人账号、设备序列号、公网 IP 等隐私信息；
- OBS 截图尽量只截来源属性区域；
- README 中手机截图建议宽度 320–420 px；
- 桌面截图建议宽度 700–900 px。

如果加入图片，可把对应的注释：

```markdown
<!-- ![PulseRelay 设置页](docs/images/03-settings.png) -->
```

改成：

```markdown
![PulseRelay 设置页](docs/images/03-settings.png)
```

---

## 开发提示

修改 Hook 逻辑前，建议先确认 3.58.x APK 中对应类/字段仍存在。PulseRelay 当前大量 Hook 点依赖小米运动健康内部实现，这些都不是公开稳定 API。

当小米运动健康升级后出现：

```text
模块注入成功
设置 UI 正常
但没有任何 BPM 回调
```

首先怀疑 Hook 点变化，而不是 UDP。

网络侧则反过来：如果设置页已经能看到 BPM 持续更新，但 OBS 没有变化，优先使用 `PING / PONG` 排查 UDP，不要继续改 Hook。

---

## License

见项目根目录：

```text
LICENSE
```
