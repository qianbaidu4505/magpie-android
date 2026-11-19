# 1.5.1 发布记录

| 项 | 值 |
|---|---|
| Release | https://github.com/yqing004-boop/magpie-android/releases/tag/v1.5.1 |
| tag | v1.5.1（commit 329cab2） |
| APK | magpie-android-1.5.1.apk（77,832,138 B） |
| SHA-256 | befdc1feda4195dda70523b3a47aac186f10b1ed3488c0f25e5f0549c0d6eb59 |
| GitHub | 正式版（draft=false, prerelease=false） |
| 蓝奏云 | https://wwbcx.lanzout.com/i1Lwb4gl6oah 提取码 haac |

## 本版内容

- 新增省电策略实验室（⋮ → 插件）：读 MIUI 省电策略真实档位、Shizuku 授权、一键设「无限制」
- 新增日志上报到 GitHub（⋮ → 网关 → 查看日志 → 上报问题）
- 修复检查 App 更新 403（回退 releases.atom）

## 技术要点

- 引入 Shizuku API v12.2.0 四个 jar 到 app/libs（55K），build-apk-local.sh 增加 -classpath 与 d8 jar 输入
- Shizuku 以 root 启动时 uid=0 可改 MIUI 数据库；ADB 启动时 uid=2000 读不到 /data/data
- MIUI 省电策略真开关：/data/data/com.miui.powerkeeper/databases/user_configure.db 的 userTable.bgControl
  noRestrict = 无限制，miuiAuto = 智能限制（默认，会被 greeze 冻）
- MILLET_NO_RESTRICT_APP 只是 bgControl=noRestrict 的镜像，不是原因
