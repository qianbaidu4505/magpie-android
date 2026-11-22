# Magpie for Android 1.7.1

## 改进：诊断包抓取范围加大

「⋮ → 关于 → 导出诊断包」现在能抓到网络排查需要的全部现场：

### 新增 netstats.json

系统口径的本 App 流量（TrafficStats，开机以来累计），同时列出已安装的代理/VPN App 各自的流量与 Magpie 的占比。排查「流量暴涨记在谁头上」用。

### 新增 vpn.json

当前 VPN 是否激活、接口名与地址，以及设备上已安装的代理/VPN 应用列表。

### 新增 connections.txt

本 App 进程树的外部 TCP 连接（不含 loopback）：本地地址、远端地址、连接状态、所属进程。从 /proc/net/tcp 读取，普通权限可用，不需要 root。

### 新增 plugins.json

已安装插件的 spec 与版本（只取包名，不含 options 里的人设文本与密钥）。

### magpie.log 从 500 行加到 2000 行

之前 500 行经常截断到重启潮开始之前。现在能覆盖更长的历史。

### usage-tail.json 从 10 条加到 100 条

并补上 provider/agent/token 数，以及当日聚合的上下行字节（来自 /api/usage 的 up_bytes/down_bytes）——网关口径一天到底走了多少，直接可读。

## 隐私不变

不打包 settings.json、providers.json、caller-keys.json 等任何含密钥/账号的文件；state.json 只留白名单字段；日志里的 web key 出现时打码；插件只取包名不取配置。
