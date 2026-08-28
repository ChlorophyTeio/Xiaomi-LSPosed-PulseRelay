# PulseRelay v3.1.0

## 新增

- UDP 接收目标支持 DNS 域名 / 主机名，例如 `obs.example.com:18181`。
- 开启“优先走 Wi-Fi”时，域名优先通过 Wi-Fi 对应 Android `Network` 的 DNS 解析。
- 域名解析支持公网 IPv4/IPv6，不再强制要求 DNS 结果必须是局域网地址。
- DNS 结果短缓存，减少实时心率发送时的重复解析开销。
- DNS 临时失败时短时间回退最近一次成功解析地址。
- PING / 诊断继续显示最终解析 IP。

## 优化

- 地址选择优先级：局域网 IPv4 → IPv4 → 局域网 IPv6 → IPv6。
- 支持 IDN 域名转 ASCII（Punycode）。
- 设置页明确提示可填写 IP / 域名 / 主机名。
