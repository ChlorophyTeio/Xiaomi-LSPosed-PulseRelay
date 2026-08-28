# PulseRelay v3.0.3

- 修复运动快捷面板错误引用 `Diagnostics.lastEndpoint` 导致 Kotlin 编译失败
- 最近网络信息改用现有的 `lastStatus + lastResolvedIp`
- 重做“发送测试心率 / 打开完整设置”双卡片按钮
- 去掉 AlertDialog 默认底部白条按钮，使用自定义“返回运动”按钮
- UDP、心率 Hook、多目标、运动悬浮入口逻辑不变
