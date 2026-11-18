# 1.4.7 发布记录

## 结果

| 项 | 值 |
|---|---|
| Release | https://github.com/yqing004-boop/magpie-android/releases/tag/v1.4.7 |
| tag | v1.4.7 |
| APK | magpie-android-1.4.7.apk（77,807,562 B） |
| SHA-256 | 6a2d012982fcbc900e2af7fb9941417fe7b55f6685d9f1a6b12701c9a1597d84 |
| GitHub | 已发布（非草稿、非预发布，body 无「AI 辅助」） |
| 蓝奏云 | https://wwbcx.lanzout.com/i1Q754gghzsh 提取码 abd0 |

## 本版内容

- 修复：通知栏一直「正在启动网关」（syncNotification 不再被 lastNotifAlive 去重拦死）
- 修复：检查 App 更新 403/不可达（API 失败回退 releases.atom，零 token）
- 修复：供应商余额进度条按聚合后总额度已用画，不再因单账号用光而全红
- 菜单精简：删「停止网关」「重新加载网页」
- 新增：文件管理器（上一版已含，随此版继续）

## 脚本修复

release.sh 的 Release 创建分支改成 Python 直发，去掉 `curl -w` 重定向出的 `.code` 临时文件，消除连续多次的 Traceback。以后一条命令能跑通。

## 安全备注

- 曾考虑把 GitHub token 内置进 APK，查了权限发现是 admin 全仓库，已撤销，改用零 token 方案。
- 用户新生成的 github_pat_11CTW... 也是 admin 全仓库，未内置进 APK。建议生成 fine-grained 只读 token 再用于 MCP/发版。
