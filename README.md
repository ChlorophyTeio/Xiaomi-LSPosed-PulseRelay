# PulseRelay

> 将小米运动健康里的实时心率，通过局域网 UDP 低延迟转发给 OBS 或其他接收端。

PulseRelay 是一个面向 **Root + LSPosed** 环境的 Android 模块。目前主要针对 **小米运动健康 3.58.x** 适配，并针对直播/录制场景做了专门简化：不需要账号、UUID、Cookie、HTTP Server、JSON API，也不要求手机长期充当服务端。

它的工作链路非常简单：

```text
小米手环
   ↓
小米运动健康 com.mi.health
   ↓  LSPosed Hook
PulseRelay
   ↓  UDP / PULSE/1
局域网
   ↓
OBS Pulse Receiver / 其他接收端
```

PulseRelay 主要做三件事：

1. 从小米运动健康的运动实时链路和日常心率链路中取得 心跳 BPM；
2. 把最新 心跳 BPM 通过局域网 UDP 发送给一个或多个接收端；
3. 在小米运动健康的运动全屏页面中加入可拖动快捷入口，运动过程中也能查看状态、测试发送和打开完整设置。

---

## 目录

- [适合谁使用](#适合谁使用)
- [功能概览](#功能概览)
- [使用前准备](#使用前准备)
- [五分钟快速开始](#五分钟快速开始)
- [第一步：安装并启用 PulseRelay](#第一步安装并启用-pulserelay)
- [第二步：准备 OBS UDP 接收端](#第二步准备-obs-udp-接收端)
- [第三步：打开 PulseRelay 设置](#第三步打开-pulserelay-设置)
- [第四步：填写接收目标](#第四步填写接收目标)
- [第五步：先做 PINGPONG 测试](#第五步先做-pingpong-测试)
- [第六步：发送 123 BPM 测试](#第六步发送-123-bpm-测试)
- [第七步：开始实时心率](#第七步开始实时心率)
- [运动页面快捷入口](#运动页面快捷入口)
- [多路发送与广播](#多路发送与广播)
- [“优先强制走 Wi-Fi”是什么意思](#优先强制走-wi-fi是什么意思)
- [运动模式与非运动模式](#运动模式与非运动模式)
- [Windows 防火墙](#windows-防火墙)
- [如何判断问题出在哪一层](#如何判断问题出在哪一层)
- [常见问题](#常见问题)
- [从源码构建 APK](#从源码构建-apk)
- [ADB 安装与更新](#adb-安装与更新)
- [Release 签名（可选）](#release-签名可选)
- [UDP 协议](#udp-协议)
- [兼容性与 Hook 点](#兼容性与-hook-点)
- [安全与隐私](#安全与隐私)
- [截图目录](#截图目录)

---

## 适合谁使用

如果你的目标是下面这种场景，PulseRelay 就是为此设计的：

- 小米手环已经绑定“小米运动健康”；
- Android 手机已经 Root；
- 已安装并可正常使用 LSPosed；
- 希望直播时在 OBS 中显示实时 BPM；
- 手机和 OBS 电脑通常位于同一个 Wi-Fi / 局域网；
- 希望一份心率同时提供给多台电脑或多个接收端。

当前主要测试/适配目标：

```text
小米运动健康：3.58.x
包名：com.mi.health
Android：最低 API 26
LSPosed / Xposed API：90+
```

> 更新到更高版本的小米运动健康后，如果突然无法取得心率，优先怀疑 Hook 点发生变化。请保留可正常工作的 APK 和小米运动健康版本，方便回退与排查，有如何问题可以提交到 ISSUES。

---

## 功能概览

### 心率采集

- 运动实时心率；
- `sport_eco` 运动链路；
- 日常心率 fallback；
- 相同 BPM 短时间去重；
- 当前 BPM、来源、更新时间实时显示。

### 局域网发送

- UDP，无 HTTP 路径和 JSON；
- 默认端口 `18181`；
- 最多 8 个单播目标；
- 可选 UDP 局域网广播；
- 可选优先绑定 Wi-Fi；
- PING/PONG 双向连通性测试；
- `123 BPM` 一键测试包。

### 运动中操作

- 运动全屏页面显示 `♥ + BPM` 悬浮入口；
- 不需要 Android“显示在其他应用上层”权限；
- 可拖动位置；
- 点击打开运动快捷面板；
- 运动过程中可暂停/恢复中继；
- 可发送测试心率；
- 可直接打开完整设置；
- 可查看当前数据来源、目标数量与发送状态。

---

# 使用指南

## 使用前准备

你需要：

### 手机端

- 一台已经 Root 的 Android 手机；
- LSPosed；
- 小米运动健康 3.58.x；
- 小米手环已在小米运动健康内正常同步；
- PulseRelay APK。

### 电脑端

- OBS Studio；
- PulseRelay 配套的 **Pulse Receiver for OBS** UDP 版插件；
- 手机与电脑处于同一局域网；
- Windows 防火墙允许对应 UDP 端口。

推荐网络结构：

```text
                    ┌──────── OBS 电脑 A 192.168.1.20:18181
小米手环 → 手机 ─────┼──────── OBS 电脑 B 192.168.1.21:18181
                    └──────── 其他 UDP 接收端
```

---

## 五分钟快速开始

已经装好 LSPosed 和 OBS 插件的话，只需要完成下面几步：

1. 安装 PulseRelay APK；
2. LSPosed → 模块 → **PulseRelay** → 启用；
3. 作用域勾选 **小米运动健康 `com.mi.health`**；
4. 强制停止并重新打开小米运动健康；
5. OBS 添加 **PulseRelay 心率 / Heart Rate** Source，监听 UDP `18181`；
6. 找到 OBS 电脑局域网 IPv4，例如 `192.168.1.20`；
7. PulseRelay 设置里填写：

   ```text
   192.168.1.20:18181
   ```

8. 点 **PING 全部接收端**；
9. 确认出现 `PONG`；
10. 点 **发送测试心率 123 BPM**；
11. OBS 应立即显示 `123 BPM`；
12. 在手环上开始“自由训练 / 步行 / 跑步”等运动；
13. OBS 开始显示真实实时心率。

只要第 8～11 步成功，手机到 OBS 的 UDP 网络链路就已经正常。之后如果没有真实 BPM，应重点排查心率 Hook，而不是继续改网络。

---

## 第一步：安装并启用 PulseRelay

安装 APK 后打开 LSPosed：

```text
LSPosed
  → 模块
  → PulseRelay
  → 开启模块
  → 作用域
  → 勾选 小米运动健康 / com.mi.health
```

然后强制停止小米运动健康，并重新打开：

```powershell
adb shell am force-stop com.mi.health
```

也可以直接在 Android 系统的应用信息页中强制停止“小米运动健康”。

> LSPosed 模块是在目标进程启动时注入的。修改作用域、更新 PulseRelay APK 或修改 Hook 代码后，都建议强制停止并重新打开小米运动健康。

---

## 第二步：准备 UDP 接收端

PulseRelay 3.x 使用 UDP，因此 **旧 HTTP 版 OBS 插件无法接收 v3 数据**。

配套 OBS Source 的基本网络设置：

```text
监听地址：0.0.0.0
UDP 端口：18181
```

`0.0.0.0` 表示监听电脑上的所有网络接口，通常最省事。

### 找到电脑局域网 IP

Windows PowerShell / CMD：

```powershell
ipconfig
```

寻找当前 Wi-Fi 或以太网网卡的 IPv4，例如：

```text
IPv4 Address . . . . . . . . . . : 192.168.1.20
```

那么手机端目标就是：

```text
192.168.1.20:18181
```

当然你可以准备**OBS 插件**或者**串口调试助手**用于测速UDP的报文。

---

## 第三步：打开 PulseRelay 设置

PulseRelay 没有单独的 Launcher Activity。设置入口直接注入小米运动健康。

### 普通页面进入

打开：

```text
小米运动健康
  → 我的 / 个人页
  → 关于
```

在“关于”页面 **长按页面中的图片/图标**。

PulseRelay 会尝试给 About 页面中的 ImageView 安装长按入口；如果页面结构发生变化并且没有找到图片，模块会回退到“长按页面空白区域”。

成功后会弹出 PulseRelay 完整设置界面。

<img width="1200" height="2608" width="500" alt="Screenshot_2026-08-28-14-51-34-445_com mi health" src="https://github.com/user-attachments/assets/70991b75-151c-442d-af89-882efcc5c9bd" />

### 运动过程中进入

运动开始后，不需要结束运动再去“关于”页面。

PulseRelay 会在运动页面加入一个可拖动的：

```text
♥
87
```

点击它即可打开运动快捷面板，再点击 **打开完整设置**。

详见：[运动页面快捷入口](#运动页面快捷入口)。

---

## 第四步：填写接收目标

PulseRelay 3.x **不再填写 URL**。

正确：

```text
192.168.1.20:18181
```

也可以省略默认端口：

```text
192.168.1.20
```

省略时自动使用：

```text
18181
```

### 多台电脑

每行填写一个目标，最多 8 个：

```text
192.168.1.20:18181
192.168.1.21:18181
192.168.1.22:18182
```

一条实时心率会依次发送到所有目标。

### 从 v2 HTTP 配置升级

旧版本可能保存的是：

```text
http://192.168.1.20:18181/receive_data
```

PulseRelay 3.x 会自动迁移为：

```text
192.168.1.20:18181
```

所以通常不需要手动清除旧配置。

<img width="800" height="1738" width="500" alt="Screenshot_2026-08-28-14-51-37-489_com mi health" src="https://github.com/user-attachments/assets/9c43634f-182d-40b3-a533-1b0eddb90c99" />

---

## 第五步：先做 PING/PONG 测试

配置目标后，**强烈建议第一件事先点：**

```text
PING 全部接收端
```

这一步与普通 UDP 心率包不同：PulseRelay 会发送：

```text
PULSE/1|PING|<nonce>
```

OBS 接收端必须返回：

```text
PULSE/1|PONG|<nonce>
```

因此 PING 成功可以证明：

```text
手机 PulseRelay
   ↓ UDP
Windows 防火墙
   ↓
OBS 插件
   ↓ UDP PONG
手机
```

这一整条链路都通了。

成功示例：

```text
✓ 192.168.1.20:18181 · PONG · 8ms · 192.168.1.20
```

### PING 超时时间

完整设置 → **高级设置** 可以调整 PING 等待时间：

```text
200–5000 ms
默认：900 ms
```

普通局域网不需要调大。如果 Wi-Fi 很忙或使用虚拟网络，可以适当提高。

---

## 第六步：发送 123 BPM 测试

PING 成功以后，再点：

```text
发送测试心率 123 BPM
```

OBS 应立即显示：

```text
♥ 123 BPM
```

这一步会发送正常的心率数据包：

```text
PULSE/1|HR|<seq>|123|<timestamp>
```

### 注意：UDP SENT 不等于对端确认收到

PulseRelay 的实时 HR 是 fire-and-forget。

界面显示：

```text
UDP SENT
```

只说明数据已经交给 Android 网络栈，不等于 OBS 已经确认收到。

需要确认真正的端到端连通性时，请使用 **PING 全部接收端**。

---

## 第七步：开始实时心率

网络测试全部通过后，再测试真实手环心率。

推荐直接在手环上启动一个运动，例如：

```text
自由训练
步行
跑步
室内骑行
```

然后保持运动页面运行几秒。

PulseRelay 设置中的“当前数据”应开始显示：

```text
运动实时
当前 BPM：87
最后更新：刚刚
Hook：SportDataServer ...
```

OBS 中也会从测试值 `123` 切换成真实 BPM。

### 为什么推荐运动模式

运动模式下，小米运动健康会持续接收手环的运动实时数据，更新频率明显更适合直播。

非运动状态也可能取得 `DailyHrReport` 日常心率，但更新频率受小米手环健康监测策略影响，可能几十秒甚至更久才变化一次。

因此：

| 使用场景 | 推荐方式 |
|---|---|
| OBS 直播实时心率 | **开启手环运动模式** |
| 游戏时展示实时 BPM | **开启自由训练等运动模式** |
| 日常静息观察 | 可依赖日常心率，但刷新较慢 |
| 调试网络 | 不需要手环，直接发送 `123 BPM` |

---

## 运动页面快捷入口

PulseRelay 会检测小米运动健康的运动 Activity，并把悬浮入口直接加入 Activity 的 `DecorView`。

这意味着它：

- 不需要 `SYSTEM_ALERT_WINDOW`；
- 不需要系统“显示在其他应用上层”权限；
- 不会在其他 App 里一直漂浮；
- 只跟随小米运动健康运动页面存在。

入口默认类似：

```text
 ♥
 87
```

### 操作

- **拖动**：移动按钮位置；
- **点击**：打开运动快捷面板；
- BPM 会随当前实时心率刷新；
- 中继暂停后入口会降低透明度。

快捷面板内可以：

- 查看当前 BPM；
- 查看“运动实时 / 日常心率 / 无数据”；
- 查看最后更新时间；
- 查看目标数量与网络状态；
- 开启/暂停实时 UDP 中继；
- 发送 `123 BPM` 测试；
- 打开完整设置；
- 返回运动页面。

> **运动页悬浮按钮**  
<img width="800" height="1738" width="500" alt="Screenshot_2026-08-28-15-16-07-002_com mi health" src="https://github.com/user-attachments/assets/ae927c2e-fe33-4f64-aec8-eb292f1e223b" />


> **运动快捷面板**  
<img width="800" height="1738" width="500" alt="Screenshot_2026-08-28-15-16-10-641_com mi health" src="https://github.com/user-attachments/assets/34ba1194-3860-4028-8aba-2edf4b6db1d6" />

---

## 多路发送与广播

PulseRelay 有两种一对多方式。

### 方式 A：多目标单播

配置：

```text
192.168.1.20:18181
192.168.1.21:18181
192.168.1.22:18181
```

优点：

- 目标明确；
- 不依赖局域网广播策略；
- 适合固定的几台电脑。

适合：

```text
手机
 ├→ 主直播机
 ├→ 录制机
 └→ 调试电脑
```

### 方式 B：局域网广播

打开：

```text
同时发送局域网广播
```

默认广播到：

```text
255.255.255.255:18181
```

同一局域网中所有监听 UDP `18181` 的兼容接收器都可以收到。

优点：

- 手机只配置一次；
- 新增电脑不需要修改手机目标列表；
- 很适合多台 OBS 都使用同一端口。

限制：

- 某些路由器会限制广播；
- AP / Client Isolation 会阻止设备互访；
- 公共 Wi-Fi 不建议使用；
- 广播协议当前没有认证。

### 单播和广播可以同时开启

如果你既填写了单播目标，又开启广播，PulseRelay 会都发送。

注意避免让同一接收端同时通过“单播 + 广播”收到重复包。配套 OBS Receiver 会利用序号/时间戳尽量过滤旧包和重复包，但网络设计上仍建议尽量避免无意义的重复路径。

---

## “优先强制走 Wi-Fi”是什么意思

这个开关不是“让 Wi-Fi 更快”。

它的作用是：**当手机同时存在 Wi-Fi、4G/5G、VPN 等多个网络时，尽量把 PulseRelay 的 UDP Socket 绑定到 Wi-Fi Network。**

典型场景：

```text
手机同时：
  Wi-Fi = 192.168.1.x
  5G    = 公网

OBS = 192.168.1.20
```

如果访问 `192.168.1.20`，它应该从 Wi-Fi 出去，而不是蜂窝网络。

### 建议开启

- 手机和电脑处于同一个 Wi-Fi；
- 使用 `192.168.x.x`、`10.x.x.x` 等局域网地址；
- 手机同时开启了移动数据。

### 建议关闭

- Tailscale；
- ZeroTier；
- WireGuard / VPN；
- 其他需要虚拟网卡路由的方案；
- 开启强制 Wi-Fi 后 PING 失败，而关闭后正常。

如果无法绑定 Wi-Fi，PulseRelay 会记录警告并回退到 Android 默认路由。

---

## 运动模式与非运动模式

PulseRelay 本身已经不需要让用户手动选择“运动上传模式 / 非运动上传模式”。

模块会从多个 Hook 点接收数据，并自动判断来源。

### 运动实时

常见来源：

```text
SportDataServer
CommonSportModel
EcoSportDataServer
BaseSportVM
```

界面显示：

```text
运动实时
```

这是 OBS 推荐使用的数据源。

### 日常心率

常见来源：

```text
DailyHrReport
```

界面显示：

```text
日常心率
```

它不保证每秒更新。

### 无数据

如果显示：

```text
无数据
当前 BPM：--
```

说明网络是否正常暂时不重要——模块当前还没有从小米运动健康 Hook 到 BPM。

此时请先开启手环运动模式进行验证。

---

## Windows 防火墙

如果手机和电脑互相能上网，但 PING 一直失败，Windows 防火墙是最常见原因之一。

配套 OBS Receiver 源码中提供：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\open-firewall.ps1 -Port 18181
```

建议只对 **Private / 专用网络** 放行 UDP `18181`。

如果手工创建规则：

```text
协议：UDP
本地端口：18181
网络类型：Private
操作：允许连接
```

不要为了测试直接永久关闭整个 Windows Defender Firewall。

---

# 故障排查

## 如何判断问题出在哪一层

最有效的方式不是一次排查所有东西，而是按下面顺序测试。

### 1. PulseRelay 是否成功注入？

确认：

```text
LSPosed → PulseRelay → 作用域包含 com.mi.health
```

更新 APK 后强制停止并重新打开小米运动健康。

如果需要日志：

```powershell
.\scripts\watch-log.ps1
```

或：

```powershell
adb logcat -v time PulseRelay:D '*:S'
```

### 2. 手机到 OBS 的 UDP 是否正常？

先完全忽略手环，直接点：

```text
PING 全部接收端
```

如果没有 PONG，排查：

```text
IP / 端口
↓
Windows 防火墙
↓
OBS 插件是否已经加载并监听
↓
是否同一局域网
↓
Wi-Fi 客户端隔离
↓
强制 Wi-Fi / VPN 路由
```

### 3. 123 BPM 是否能显示？

PING 成功后点：

```text
发送测试心率 123 BPM
```

如果 OBS 显示 123：

```text
PulseRelay → UDP → OBS
```

已经完全正常。

### 4. 测试 BPM 正常，但真实 BPM 不动？

此时不要继续折腾防火墙。

重点看完整设置中的：

```text
当前数据
```

如果：

```text
当前 BPM：--
来源：无数据
```

说明问题位于：

```text
小米手环 → 小米运动健康 → Hook
```

建议：

1. 手环开始自由训练；
2. 等 10～20 秒；
3. 查看是否出现 `运动实时`；
4. 查看 LSPosed / logcat 中是否出现 `实时心率[...]`。

### 5. 当前 BPM 有数据，但 OBS 没变化？

如果 PulseRelay 页面已经显示：

```text
当前 BPM：87
运动实时
```

但 OBS 没变化，则重点检查：

- “启用实时 UDP 中继”是否打开；
- 目标列表是否为空；
- OBS 端口是否一致；
- PING 是否还能成功；
- 是否错误地绑定了 VPN / Wi-Fi；
- OBS 是否添加了正确的 UDP 版 Source。

---

## 常见问题

### Q：为什么不再使用 HTTP？

实时心率属于“最新值优先”。

例如：

```text
86 BPM 丢了一包
下一包 87 BPM 很快就会到
```

因此没有必要为每一条 BPM 建立 HTTP 请求、JSON、路径、状态码和重试语义。

UDP 更简单，也更适合多目标和局域网广播。

---

### Q：为什么不使用 TCP？

TCP 当然可以，但需要考虑：

- 建立连接；
- 断线重连；
- 心跳保活；
- 阻塞；
- 粘包/拆包；
- 某个慢客户端拖慢处理。

对于几十字节的实时 BPM，UDP 更符合需求。

---

### Q：为什么不让手机直接做服务器？

手机做 UDP/TCP Server 并不是一定不安全，但会带来额外问题：

- 手机需要开放入站端口；
- Android 后台进程生命周期更复杂；
- Doze / 厂商后台限制可能影响监听；
- Wi-Fi、VPN、移动网络切换时更难维护；
- 多台电脑主动连接手机需要知道手机 IP。

当前“手机主动推送”对直播使用更简单。

---

### Q：必须开运动模式吗？

不是绝对必须。

日常心率 Hook 可能也有数据，但是刷新频率通常较低。

如果目标是 OBS 实时显示，建议开启运动模式。

---

### Q：为什么一直显示相同 BPM？

可能原因：

1. 手环没有产生新测量；
2. 当前是日常心率而不是运动实时；
3. 小米运动健康没有持续收到设备数据；
4. 相同 BPM 在去重窗口内被丢弃。

默认相同 BPM 去重窗口：

```text
300 ms
```

可以在高级设置中修改为 `0–5000 ms`。

---

### Q：广播模式为什么找不到其他电脑？

检查：

- 所有电脑是否在同一个二层局域网；
- OBS Receiver 是否监听同一个 UDP 端口；
- Windows 防火墙；
- 路由器 AP Isolation / Client Isolation；
- 企业 Wi-Fi 是否禁止广播；
- 手机是否接入 Guest Wi-Fi。

广播不是所有网络都保证可用。无法使用时改用多目标单播。

---

### Q：旧版 HTTP OBS 插件还能用吗？

不能。

PulseRelay 3.x 发送的是：

```text
PULSE/1 UDP
```

旧插件等待的是 HTTP `/receive_data`，协议完全不同。

请使用 UDP 版 Pulse Receiver for OBS。

---

### Q：公共 Wi-Fi 能不能用？

不建议。

PULSE/1 当前没有加密和认证。同一局域网里的其他设备理论上可以伪造 UDP BPM 包。

建议仅在可信家庭/工作室 LAN 使用，并将 Windows 防火墙规则限制在 Private 网络。

---

# 从源码构建 APK

以下步骤以 Windows PowerShell 为例。

## 构建环境

当前工程配置：

```text
Gradle Wrapper：9.3.1
Android Gradle Plugin：8.13.2
Kotlin：2.3.10
Java / JVM Target：17
compileSdk：35
targetSdk：35
minSdk：26
```

推荐准备：

- Windows 10 / 11；
- JDK 17；
- Android SDK Platform 35；
- Android SDK Build-Tools 35；
- Android Platform-Tools / adb；
- PowerShell；
- 网络可访问 Google Maven、Maven Central、Xposed API Maven 和 Gradle Distribution。

### 检查 Java

```powershell
java -version
```

应使用 Java 17。

如果系统存在多个 JDK，确保 Gradle 实际使用的是正确版本：

```powershell
.\gradlew.bat --version
```

---

## 配置 Android SDK

项目根目录创建：

```text
local.properties
```

例如：

```properties
sdk.dir=D:/DevEnv/Android/Sdk
```

Windows 下推荐使用 `/`，可以少处理很多转义问题。

也可以使用：

```properties
sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk
```

如果没有 Android SDK，可以通过 Android Studio → SDK Manager 安装。

至少需要：

```text
Android SDK Platform 35
Android SDK Build-Tools 35.x
Android SDK Platform-Tools
```

首次构建时 Gradle/AGP 在许可证已接受的情况下，也可能自动补装缺失的 Platform/Build-Tools。

---

## 构建 Debug APK

进入项目目录：

```powershell
cd D:\Projects\android\PulseRelay
```

推荐直接使用工程脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1
```

或者：

```powershell
.\gradlew.bat clean assembleDebug
```

成功后会看到：

```text
BUILD SUCCESSFUL
```

APK 输出：

```text
app\build\outputs\apk\debug\app-debug.apk
```

### 首次构建比较慢是正常的

第一次执行可能需要下载：

- Gradle 9.3.1；
- Android Gradle Plugin；
- Kotlin Plugin；
- Xposed API；
- Android SDK Platform / Build Tools。

之后有缓存时会快很多。

### Gradle 下载超时

Wrapper 已设置：

```properties
networkTimeout=120000
```

如果仍然无法从 `services.gradle.org` 下载，可以手动下载对应：

```text
gradle-9.3.1-bin.zip
```

然后临时把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 改为本地 `file:///...` 地址。

---

## ADB 安装与更新

手机开启 USB 调试后：

```powershell
adb devices
```

确认设备处于：

```text
device
```

然后：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-debug.ps1
```

或手工：

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.mi.health
```

安装后重新打开小米运动健康。

### `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

这通常说明：

```text
旧 APK 的签名 != 你当前编译 APK 的签名
```

如果不需要保留旧模块应用数据，可以：

```powershell
adb uninstall website.xihan.pbra
adb install .\app\build\outputs\apk\debug\app-debug.apk
```

然后重新确认 LSPosed 模块和作用域。

如果需要长期无缝覆盖升级，建议一直使用同一个 keystore 构建。

---

## Release 签名（可选）

Debug 自测不要求自定义 keystore。

如果项目根目录存在：

```text
keystore.properties
```

工程会自动创建 `custom` signingConfig。

示例：

```properties
storePassword=你的store密码
keyPassword=你的key密码
keyAlias=pulserelay
storeFile=../pulserelay.jks
```

如果 `pulserelay.jks` 位于项目根目录，而 `file(...)` 从 `app` module 解析，通常使用：

```text
../pulserelay.jks
```

然后：

```powershell
.\gradlew.bat assembleRelease
```

Release APK 位于：

```text
app\build\outputs\apk\release\
```

> 不要把私有 keystore、密码或 `keystore.properties` 提交到公开仓库。

---

## 从旧工程迁移本地构建配置

工程附带：

```powershell
.\scripts\import-local-config.ps1
```

例如：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\import-local-config.ps1 `
  -From D:\Projects\OldPulseRelay
```

脚本会尝试复制：

```text
local.properties
keystore.properties
*.jks
*.keystore
```

适合重建干净源码目录时使用。

---

## 调试日志

实时查看 PulseRelay 日志：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\watch-log.ps1
```

等价于：

```powershell
adb logcat -v time PulseRelay:D '*:S'
```

建议反馈问题时同时提供：

1. PulseRelay 完整设置中的“复制诊断信息”；
2. LSPosed 模块日志；
3. `adb logcat` 中 PulseRelay 日志；
4. 小米运动健康版本；
5. 手机 Android 版本；
6. 手环型号；
7. 是否处于运动模式；
8. PING 是否成功；
9. `123 BPM` 是否能在 OBS 显示。

这样可以快速判断问题发生在 Hook、Android 网络还是 OBS 接收端。

---

# UDP 协议

PulseRelay 使用极简 UTF-8 文本协议：

```text
PULSE/1|HR|<seq>|<bpm>|<unix_ms>
PULSE/1|PING|<nonce>
PULSE/1|PONG|<nonce>
```

### 实时心率

示例：

```text
PULSE/1|HR|1042|87|1787881234567
```

字段：

| 字段 | 含义 |
|---|---|
| `PULSE/1` | 协议版本 |
| `HR` | 数据类型 |
| `1042` | 单调递增序号 |
| `87` | BPM |
| `1787881234567` | Unix 毫秒时间戳 |

PulseRelay 只发送合理范围内的 BPM：

```text
20–260 BPM
```

### PING/PONG

请求：

```text
PULSE/1|PING|abc123
```

接收端返回：

```text
PULSE/1|PONG|abc123
```

广播 PING 会在超时时间内收集多个 PONG，并显示类似：

```text
PONG x3
```

---

# 兼容性与 Hook 点

当前为小米运动健康 3.58.x 保留多级 Hook：

```text
SportDataServer.onPhoneDataChanged
CommonSportModel.onPhoneDataChanged
EcoSportDataServer.onPhoneDataChanged
sport_eco CommonSportModel.onPhoneDataChanged
BaseSportVM.onSuccess            # fallback
DailyHrReport.setLatestHrRecord  # 日常心率 fallback
```

其中运动链路会优先直接取得 `PhoneSportData.heart_rate` 等实时字段，尽量避免依赖 UI 文本和页面展示逻辑。

如果未来小米运动健康升级后：

- PulseRelay 仍显示已注入；
- PING 和 `123 BPM` 都正常；
- 但“当前 BPM”始终 `--`；

那么极有可能是小米修改了内部运动数据链路，需要重新定位 Hook 点。

---

# 安全与隐私

PulseRelay 的设计目标是 **本地局域网中继**。

当前网络层会优先接受：

- IPv4 私网地址；
- loopback / link-local；
- IPv6 ULA；
- 部分 CGNAT 本地地址。

普通实时数据不会上传到 PulseRelay 官方服务器，因为项目本身没有账号、登录、UUID 或云端 API。

但是 PULSE/1 当前：

- 不加密；
- 不认证；
- 广播模式同网段可见。

所以推荐：

- 家庭 LAN；
- 自己控制的工作室网络；
- Windows 防火墙限定 Private 网络。

不建议：

- 手机流量；
- 公共机场/酒店 Wi-Fi；
- 不可信共享局域网；
- 直接暴露 UDP 端口到公网。

---

# 截图目录

建议把 README 图片放在：

```text
docs/images/
```

预留文件名：

| 文件 | 内容 |
|---|---|
| `01-lsposed-scope.png` | LSPosed PulseRelay 作用域 |
| `02-settings-entry.png` | 小米运动健康 About 页面设置入口 |
| `03-settings.png` | PulseRelay 完整设置页 |
| `04-sport-overlay.png` | 运动页面 `♥ + BPM` 悬浮按钮 |
| `05-sport-quick-panel.png` | 运动快捷面板 |
| `06-obs-udp-receiver.png` | OBS UDP Receiver 来源属性 |
| `07-ping-pong.png` | PING/PONG 成功结果 |

截图准备好后，把 README 中对应的 Markdown 图片注释取消即可。

---

# 项目说明

应用/LSPosed 中显示名称：

```text
PulseRelay
```

Android 包名继续保留：

```text
website.xihan.pbra
```

保留包名是为了减少从旧 HeartRateHook / HeartRate OBS Bridge 版本升级时出现“被 Android 当成另一个完全不同模块”的问题。

本项目是在原 HeartRateHook 思路基础上针对“小米运动健康 → 局域网实时心率”场景进行的专用化重构。许可证与第三方项目声明请查看仓库中的 `LICENSE` 及相应依赖许可证。

---

## 最短排错口诀

遇到问题时按这个顺序：

```text
LSPosed 注入？
    ↓
PING 有 PONG？
    ↓
123 BPM 能显示？
    ↓
PulseRelay 当前 BPM 有数据？
    ↓
运动实时来源是否更新？
```

对应判断：

```text
PING 不通           → 网络 / 防火墙 / OBS 接收端
PING 通，123 不显示 → OBS Source / 端口 / 渲染
123 正常，BPM 为 -- → 小米运动健康 Hook
BPM 有值，OBS 不变  → 中继开关 / 目标 / UDP 路径
```
