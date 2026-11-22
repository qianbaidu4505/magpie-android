# magpie-android 交接文档

> 最后更新：2026-11-22
> 当前版本：**1.6.9**（VERSION_CODE=169），与发布仓库 `magpie-android-repo` HEAD 完全同步
> 本目录 = 可构建工作副本：repo 全部源码 + 本地独有 `app/src/main/assets/bin/magpie.bin`（74M）、`scripts/debug.keystore`、`scripts/tools`、`dist/`
> 这份文档是给「接手的人 / 下一个会话」看的，读完能直接干活。

---

## 一、这是什么

yetone/magpie（Go 写的 AI 网关）的 Android 壳。

**核心是 magpie 的 Go 静态二进制**（`assets/bin/magpie.bin`，74 MB），
外壳是**完全原生绘制的界面**（不是 WebView 套壳）。

包名 `com.magpie.android`，APK 约 76 MB。

用户原话：

> 「把当前壳完全摒弃，重新做个 UI 界面，但是核心还是用 magpie。
> 这样我点进软件就可以直观看到用量、账号、和路由这些，
> 毕竟 magpie 页面在手机上还是有点卡」

所以：**magpie 网页只是它 HTTP API 的一个消费方**，原生界面直接调同一批 API。

---

## 二、⚠️ 第一件事：备份 android.jar

```sh
mkdir -p ~/magpie-build-deps
cp /tmp/plat30/android-11/android.jar ~/magpie-build-deps/android-30.jar
```

**为什么必须做**：这份 `android.jar` 在 `/tmp` 下，会被系统清掉。
而且它**不可替代**——必须是带 `resources.arsc` 且能被 aapt2 2.19 读的那份
（API 30 / platform-30_r03）。API 35 那份 arsc 版本太新，
build-tools 33 的 aapt2 读不了，报 `failed to load include path`。

丢了就得重新下 `platform-30_r03.zip`。

---

## 三、目录与环境

| 项 | 路径 |
|---|---|
| 开发源码（工作区，untracked） | `~/workspace/magpie-android` |
| 发布仓库（有 git 历史 + 已发 tag） | `~/workspace/magpie-android-repo` |
| 上游 magpie Go 源码（查接口/结构体用） | `~/workspace/magpie` |
| Android SDK build-tools | `/opt/android-sdk/android-15` |
| android.jar（API 30） | `/tmp/plat30/android-11/android.jar` |
| android.jar 备用副本 | `~/.aimini/android-sdk/android-11/android.jar` |
| aarch64 aapt2 / zipalign | `scripts/tools/aarch64/`（仓库自带） |
| 签名 keystore | `scripts/debug.keystore`（口令 `android`，别名 `androiddebugkey`） |
| GitHub 凭据 | `~/.aimini/git-credential-aimini`（`git credential fill` 动态取） |
| 已发 tag | v1.0.20 / 26 / 28 / 31 / 35 / 37 / 38 / 39 / 42 / 48 |

容器环境：Alpine musl aarch64，无 `/sdcard`。
宿主真实工作区路径：`/data/data/com.aimini.fujonly/files/projects/default/`。
需要访问宿主目录/安装 APK 时用 Root 工具。

---

## 四、构建（无 Gradle）

项目**故意不用 Gradle**：AGP 会从 Google Maven 拉 linux-x86_64 的 aapt2，
在 arm64（手机、PRoot 容器）上跑不了。

所以直接拼：

```
aapt2 compile → aapt2 link → javac → d8 → 打包 → zipalign → apksigner
```

### 出一条 APK

```sh
cd ~/workspace/magpie-android

VERSION_NAME=1.4.2 VERSION_CODE=142 \
ANDROID_JAR=/tmp/plat30/android-11/android.jar \
sh scripts/build-apk-local.sh
```

产物：`dist/magpie-android-<VERSION_NAME>.apk`

### 快速验证语法（比全量快很多）

跑完整构建约 40 秒，改一行 Kotlin/Java 后可以先只跑到 javac：

```sh
# 直接跑完整脚本最省事，它会在 javac 阶段就报错停下
sh scripts/build-apk-local.sh 2>&1 | grep -A3 "error:"
```

### 发版（同步 → commit → tag → push → Release）

```sh
sh scripts/release.sh 1.4.2 142
```

脚本行为：
1. `rm -rf $REPO_DIR/app/src/main && cp -r app/src/main`（同步源码）
2. 删掉 `assets/bin`（二进制不进 repo）
3. `git add -A && git commit -m "发布 x.y.z"`
4. `git tag vx.y.z && git push origin main && git push origin <tag>`
5. `git credential fill` 拿 token → curl 建 Release + 传 APK

**注意**：
- Release body **不要**写「构建由 AI 辅助完成」（用户明确要求）
- `.gitignore` 已排除 keystore / 内核二进制 / aapt2 / dist / build-local
- 发版前先 `git -C ../magpie-android-repo tag -l` 看最新 tag，别开倒车

---

## 五、代码结构（36 个文件 / 14587 行）

### 入口与骨架

| 文件 | 行 | 职责 |
|---|---:|---|
| `NativeHome.java` | 1118 | **主界面入口（LAUNCHER）**。顶栏 3 胶囊 + 底部悬浮胶囊 tab + ⋮ 菜单 |
| `NativePage.java` | 330 | 页基类：ScrollView + 三态（加载/读不到/等网关）+ 下拉刷新 + 保留滚动位置 + `onDetach/onAttach` |
| `MainActivity.java` | 1124 | **纯 WebView 容器**（旧网页壳降级而来），从 ⋮ 「打开控制后台」进 |
| `App.java` | 35 | Application：生成 webKey、读单位模式、跑一次性迁移 |

### 四个 tab

| 文件 | 行 | 职责 |
|---|---:|---|
| `NativePageOverview.java` | 912 | 概览：时间范围条 + 2×2 汇总卡 + 趋势图 + 额度卡（按供应商）+ 网关卡 |
| `NativePageUsage.java` | 459 | 用量：周期条 + 汇总 + 模型排行 + 谁在用 + 最近请求 |
| `NativePageAccounts.java` | 941 | 账号：供应商两级折叠 + 额度 + 合计 + 启停 + 添加供应商入口 |
| `NativePageRoutes.java` | 117 | 路由：只有**实时面板 + 请求记录**两块（路由组树已按用户要求删除） |

### 路由页的三块

| 文件 | 行 | 职责 |
|---|---:|---|
| `LivePanel.java` | 636 | 「实时 · 网关正在做的决定」：两方框 + 绿虚线 + 候选账号卡（一账号一卡） |
| `TraceSource.java` | 98 | `/api/gateway/trace` **长轮询**（挂 26 秒，有新事件才回） |
| `TraceLog.java` | 406 | 「请求」区：实时/今天/昨天/更早 标签 + 流水列表 |

### 弹窗

| 文件 | 行 | 职责 |
|---|---:|---|
| `CtxSheet.java` | 519 | **上下文窗口详情**：Token 用量、缓存命中、色块占比图、分项明细 |
| `PartSheet.java` | 292 | 点分项看**原文**（工具定义 JSON / messages 分段） |
| `AddProviderSheet.java` | 286 | 添加供应商：选 presets → 填 key → `POST /api/provider/save` |
| `PullToRefresh.java` | 253 | 自写下拉刷新（无 AndroidX），最少转 500ms |
| `ShellUpdateFlow.java` | 265 | 壳（APK）自更新流程 |

### 网络与数据

| 文件 | 行 | 职责 |
|---|---:|---|
| `MagpieApi.java` | 241 | HTTP 层：Cookie、超时常量、MAX_BODY 64MB、`Result` |
| `NativeData.java` | 353 | JSON 取值 + 格式化（tokens/cost/bytes/until/parseTime）+ **单位模式** |
| `QuotaCache.java` | 97 | 额度卡跨请求合并（分批回包）+ 版本号 |
| `QuotaAgg.java` | 328 | 供应商合计：余额去重相加、窗口按名累加、最紧百分比 |
| `ProviderIcons.java` | 188 | 厂商 logo 映射（icon名→provider id→preset id 三级查） |
| `Collapse.java` | 115 | 折叠状态持久化（pref） |
| `Zh.java` | 123 | 英文词条中文化（对齐 magpie 的 `i18n.js`） |

### 底层（基本没动过）

| 文件 | 行 | 职责 |
|---|---:|---|
| `GatewayService.java` | 1080 | 跑 Go 二进制的**前台服务**（核心，别乱改） |
| `LocalProxy.java` | 299 | DNS 兜底代理（见坑 #17） |
| `Prefs.java` | 383 | SharedPreferences 封装 |
| `Updater.java` | 783 | magpie 内核（ELF）更新器 |
| `UpdateActivity.java` | 394 | 检查 magpie 更新页（**更新源+稳定渠道也在这**） |
| `SettingsActivity.java` | 579 | 设置页 |
| `ShellUpdater.java` | 218 | 壳自更新检查 |
| `M3.java` | 195 | M3 令牌表（8 种子色 + 莫奈，33 个 role） |
| `PluginTools.java` | 592 | 插件导入 / Marvis 回调 |
| `LanInfo.java` | 161 | 局域网 IP / 密钥 |
| `BackgroundGuide.java` | 168 | 后台保活引导 |
| `Ui.java` | 436 | M3 组件工厂（卡片/胶囊/统计块/quotaBar/自绘 BarChart） |
| `ApkFileProvider.java` | 63 | 手写 FileProvider（给安装器 content:// 通道） |

### 资源

```
res/
├── layout/
│   ├── activity_main.xml        只有 FrameLayout + WebView + 错误页
│   ├── activity_settings.xml    设置页
│   ├── activity_update.xml      更新页（含更新源）
│   └── dialog_about.xml
├── drawable/                    dot_off.xml / dot_ok.xml
├── drawable-nodpi/
│   ├── ic_magpie_bird.png       图标
│   └── pv_*.png                 36 个厂商 logo（cairosvg 转的，见坑 #10）
├── values/                      strings.xml / themes.xml
├── values-night/                themes.xml
└── xml/network_security_config.xml
```

---

## 六、已核实的 magpie 接口

**全部读 Go 源码确认过，不是猜的。**

| 接口 | Go 源码位置 | 内容 |
|---|---|---|
| `GET /api/usage?period=` | `gui/usage.go:101` | 总量/序列/agents/models/accounts |
| `GET /api/usage/requests?period=&limit=` | `gui/usage.go:179` | Ledger：rows/total/by |
| `GET /api/usage/quotas` | `gui/usage.go:258` | 额度卡（**分批回包**，见坑 #5） |
| `GET /api/groups` | `gui/groups.go:402` | 路由组 |
| `GET /api/providers?last=1` | `gui/providers.go:1195` | 供应商列表（原始响应很大，只用缓存版） |
| `GET /api/gateway/trace?after=&wait=1` | `gui/trace.go:375` | **长轮询**，实时路由 |
| `GET /api/gateway/history?day=` | `gui/trace.go:398` | 某天的历史路由 |
| `GET /api/gateway/call?route=<id>` | `gui/providers.go:1222` | 单条请求原文（requestBody/responseBody） |
| `POST /api/login/{on\|off}` | `gui/providers.go:2342` | 订阅账号启停，body `{agent, user}` |
| `POST /api/keys/{on\|off}` | `gui/providers.go:2431` | key 启停，body `{id, ref}` |
| `POST /api/provider/save` | `gui/providers.go:1331` | 添加/保存供应商 |
| `GET /api/icons/<name>` | `gui/providers.go:1316` | **只服务用户上传的图**（不是内置 logo） |
| `GET /icons/<name>.svg` | 静态资源 | 内置厂商 logo（真在这儿） |

### 鉴权

Cookie：`magpie_web_<webPort>=<webKey>`（`gui/web.go:347` 的 `webGuard`）。
`webPort` = 网关端口 + 5（默认 3425 → 3430）。

### 周期值（`usage/period_range.go`）

```
today              今天（按本地日历日）
last:24h0m0s       滚动 24 小时
7d / 30d           近 7 / 30 天
all                全部
2026-10-01..2026-10-07   自选日期（两端都含）
```

### 关键字段

**`Totals`**（`usage/usage.go:509`）：
`calls` / `errors` / `retried` / `canceled` / `input` / `output` /
`cache_read` / `cache_write` / `cache_unknown` / `cache_unknown_calls` /
`reasoning` / `cost` / `unpriced` / `up_bytes` / `down_bytes` /
`timed` / `ttft_ms` / `decode_ms` / `decode_out` / `decode_n`

**`Prompt`**（`gateway/prompt.go:44`）：
`window` / `tokens` / `counted` / `held` / `turns` /
`parts[{kind, tokens, items[{name, tag, n, tokens}]}]`
`kind` 取值：`system` / `tools` / `skills` / `memory` / `files` / `results` / `chat`

**`Route`**（`gateway/trace.go:48`）：
`agent` / `model` / `group{name,routing,members}` / `order[]` / `left[]` /
`tries[]` / `done` / `status` / `prompt` / `usage[]`

**`Weighed`**（`order[]`/`left[]` 的元素）：
`id` / `provider` / `name` / `icon` / `who` / `kind` / `plan` / `model` /
`used` / `amount` / `limit` / `unit` / `renews[]` / `fallback` / `barred` /
`unlisted` / `rest{}` / `rank`

---

## 七、踩过的坑（**逐条读完，很多是重复踩的**）

### 1. `Ui.text()` 第三参数是 M3 令牌下标，不是颜色

传算好的颜色 → `ArrayIndexOutOfBoundsException: index=-5752778` 直接崩。

```java
ui.text(s, sp, M3.ON_SURFACE, bold)   // 对
ui.textColor(s, sp, argb, bold)       // 算出来的颜色走这个
```

### 2. 垂直 LinearLayout 的子项必须显式 WRAP_CONTENT ★ 踩了 3 次

不给 LayoutParams 时默认 `MATCH_PARENT`，几个子项会**平分整屏高度**，
页面中间凭空多出一大段空白。

```java
parent.addView(child, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));
```

**任何要放进竖直容器的 View 都要这样，包括容器自己。**

### 3. 名字用 weight(1) 会被长文本挤成 0 宽

账号名 + 长模型串并排时：

- 名字：`WRAP_CONTENT` + `setMaxWidth(dp(130))`
- 长的那边：`weight(1)` + `setEllipsize(END)`

写反了名字直接看不见。

### 4. 额度的 `remaining` 字段几乎不出现

真正的口径是 `used` 百分比 → `remaining = 100 - used`。
计数型窗口用 `amount / limit / unit`。

### 5. 额度是**分批回包**的 ★

`/api/usage/quotas` 第一次可能只回 1 家（实测只回过「天枢」）。
必须三件事一起做：

1. `QuotaCache` 按 `provider|user|alias` 合并历次结果
2. 重取时**绕过 TTL**（靠 `quotaPending` 标志），否则永远只显示第一批
3. 页面加载时最多问 3 轮（间隔 1.5s），凑够 3 家或轮数用完就画

### 6. `/api/gateway/history?day=<今天>` 能到 **17.5 MB**

703 条请求，每条带完整 `order[]`（6KB）+ `left[]`（15KB）路由池。

`MagpieApi.MAX_BODY` 曾是 8MB → 截断 → JSON 解析失败 → 页面「没有记录」。
现在上限 **64MB**，且 `Result.truncated` 会明确报错。

### 7. 自动刷新时页面「往下跳一下」

`content.removeAllViews()` 让内容高度瞬间归零 → ScrollView 把 `scrollY` 收成 0。

修法：`NativePage.rebuildKeepingScroll()` —— 先记 `scrollY`，
重建完 `scroll.post(() -> scroll.scrollTo(0, y))`。

### 8. magpie 网页的 `show(view)` **不在 window 上**

是 `app.js` 模块内局部函数，`typeof show === 'function'` 永远 false。
而且手机版底栏会被它自己的 `phonetabs` 重新分组。

想跳页只能三招（`MainActivity.jumpToRequestedView()`）：

1. 点 `#nav button[data-view=X]`
2. 点「更多」展开后再点
3. 按按钮文字找（中英文都试）

### 9. `Ui.mono()` 只有 `(s, sp, role)` 三个参数

没有 bold。传四个参数编译不过。

### 10. 内置厂商 logo 在哪

`/icons/<name>.svg` 是**静态资源**。
**不是** `/api/icons/` —— 那个只服务用户上传的图，内置的请求会 404。

Android ImageView 解析不了 SVG，所以常用厂商的 SVG 在**构建期**用
`cairosvg` 转成 144×144 PNG，放 `res/drawable-nodpi/pv_*.png`（当前 36 个）。

```sh
pip3 install --break-system-packages cairosvg
```

### 11. `order[]` 里同一个 id 会重复出现

一个账号服务多个模型，每个模型一条。按 id 判「选中」会**全部高亮**。

- 加 `marked` 标志，只标第一个
- 画卡片要按 id 合并成一张卡（模型名拼在右侧）

### 12. 实时面板点开上下文要用「这张卡自己的 route」

不能用全局 `lastRoute` —— 两个请求连着来时点旧卡会打开新请求的上下文。

### 13. `TraceSource.stop()` 必须把 `seq` 归零

不归零的话，切回路由页时带旧 seq 去问，网关认为「这些你已经看过了」，
中间那段请求永远收不到。

### 14. 前台/后台定时器必须成对停启 ★

```java
onResume  → post(ticker); post(autoRefresh);
onPause   → removeCallbacks(ticker); removeCallbacks(autoRefresh);
onStop    → 所有 pages.onDetach()   // 停长轮询、撤额度重试
onResume  → currentPage.onAttach()  // 重挂
```

额度重试的 `postDelayed` 要存成字段，`onDetach` 里 `host.cancel()` 撤掉。

真机验证：后台 40 秒 `logcat` 里 MagpieNative / magpie-trace / MagpieApi **零日志**。

### 15. targetSdk 锁死 28（**故意，勿改**）

Android 9+ 前台服务必须声明 `FOREGROUND_SERVICE` 权限，
否则 `startForeground` 直接 `SecurityException`（targetSdk 28 也躲不开）。

### 16. 明文 HTTP

targetSdk 28 默认禁明文，需要 `res/xml/network_security_config.xml` +
`usesCleartextTraffic="true"`。

### 17. DNS 坑（`LocalProxy` 的由来）

CGO off 的 Go 读不到 `/etc/resolv.conf` → 查 `::1:53` 全挂。
App 绑不了 53 端口（`ip_unprivileged_port_start=1024`）。

解法：`LocalProxy.java` 起本机 CONNECT 代理（127.0.0.1:7899+），
`GatewayService` 往子进程 env 塞 `HTTP(S)_PROXY`，域名由 Java 走 netd 解析。

### 18. `setBackgroundResource(android.R.attr.selectableItemBackground)` 必崩

attr 的 id 不是 drawable 的 id，会抛 `NotFoundException` 把 Activity 带崩。
必须先 `getTheme().resolveAttribute(attr, tv, true)` 再取 `tv.resourceId`。

### 19. MIUI 手势返回区会吃右上角 ⋮

API 29+ 给那个 View 调 `setSystemGestureExclusionRects` 报排除区（在 `post` 里做）。

### 20. 删代码别用索引切片 ★

搬整个方法时用 `s.index(标记)` 切片很容易多删
（曾把 `setupWebView`、`startGatewayAndWait` 一起扫没了，编译报 10 个错）。

安全做法：

1. 从 `magpie-android-repo` 拿回干净原文件
2. 写个 `cut_method(text, 'private void foo() {')` 精确切除
3. **改完立即构建验证**，别一次删一堆再编译

### 21. 沉浸式：小白条（手势导航条）

三件齐了才行：

1. `SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION`（**少了这条底部一定留白**）
2. `setNavigationBarContrastEnforced(false)` + `setStatusBarContrastEnforced(false)`
   （API 29+，否则系统会在透明导航条下面垫一层遮罩）
3. `setDecorFitsSystemWindows(false)`（API 30+）

底栏自己用 `bottomMargin = 6dp + navHeight` 抬到导航条上方。

### 22. 折叠状态不能只看 View

每次刷新都重建 View 树，状态要落 pref（`Collapse.java`，存 `"1#key"` / `"0#key"`）。
折叠要就地 `setVisibility` 不重画，否则滚动位置会跳。

### 23. `Prefs` 加新设置要写迁移

老用户存过的值会一直盖住新的默认值。
用独立标记做一次性迁移（参考 `Prefs.migrateWebReleaseOnce()`），
**不能每次启动都强刷**——那样用户手改的值会被冲掉。

---

## 八、调试手段（真机）

### 装包

```sh
# 容器里先拷到宿主可见的位置，再用 Root 装
cp ~/workspace/magpie-android/dist/magpie-android-1.4.2.apk /data/local/tmp/t.apk
pm install -r -d /data/local/tmp/t.apk
```

### 直达某个 tab

```sh
am start -n com.magpie.android/.NativeHome --ei tab 3
# 0=概览 1=用量 2=账号 3=路由
```

### 截图（MIUI 上常黑屏）

```sh
input keyevent 26          # 亮屏
wm dismiss-keyguard
screencap -p /data/local/tmp/x.png
```

### 看日志

```sh
logcat -d -t 400 | grep -E "MagpieNative|MagpieRoutes|MagpieApi|AndroidRuntime"
logcat -d -b crash         # 崩溃
```

### 量布局边界

```sh
uiautomator dump /data/local/tmp/ui.xml
# MIUI 上会喷 theme_config 报错，但 xml 照样出
grep -o 'text="[^"]*"[^>]*bounds="[^"]*"' /data/local/tmp/ui.xml
```

### 直接打网关（绕过 App 验证接口）

```sh
curl -s -H "Cookie: magpie_web_3430=<webKey>" \
  http://127.0.0.1:3430/api/usage?period=today
```

`webKey` 在 `/data/data/com.magpie.android/shared_prefs/magpie_prefs.xml` 的 `webkey`。

### 数据位置

| 内容 | 路径 |
|---|---|
| magpie 数据 | `/data/data/com.magpie.android/files/magpie/data` |
| 日志 | `.../files/logs/magpie.log`（无需 root 也能看） |
| 二进制 | `.../files/magpie/magpie.bin` |

---

## 九、当前状态

### 已验证通过（真机逐项截过图）

- 四 tab 全部能用，数据正确
- 顶栏三胶囊（**地址 / 插件 / 局域网**），点「插件」直达网页的「插件 → 已安装」
- 概览页：时间范围切换（今天/24小时/7天/30天/全部）、2×2 汇总卡
  （Token 含缓存、请求数+失败/重试/取消+↑↓流量、费用+未计价、缓存命中率、输出速度+首字平均）
- 账号页：供应商两级折叠 + 额度合计（余额去重相加）+ 单卡刷新 + 启停按钮 + 添加供应商
- 路由页：实时面板（请求·改道·错误 三数字、绿虚线联动、黑框高亮、回答了这个请求+耗时）
- 请求区：标签栏（实时/今天/昨天/更早）+ 列表，点条目看上下文窗口
- 上下文窗口：Token 用量、缓存命中、色块占比图、分项明细，点分项看原文
- 下拉刷新（最少转 500ms）
- 设置页全部项 + 数字单位切换（英 K/M/B ↔ 中 万/亿）
- 后台零唤醒

### 已知限制

| 限制 | 说明 |
|---|---|
| **流式字节数** | 网页显示「回答中… 12 B」，接口没这个字段（`bytes` 是 archive 的），用「回答中… Ns」代替 |
| **实时面板会空** | trace 只保留内存里最近几条，网关重启后清空 → 自动回退到「今天」 |
| **自定义日期区间** | 概览页没做（magpie 支持 `2026-10-01..2026-10-07`） |
| **添加供应商** | 只支持填 key 的预设，订阅类（要登录）得去网页 |
| **账号额度同家多号** | 路由页成员的额度只显示第一个账号的（`/api/groups` 只到 provider 级） |

### 未做（用户明确说暂不做）

- 列表虚拟化（RecyclerView 级）—— 当前一次性建 View，14 个账号约 200+ View

---

## 十、待办 & 风险

### 高

1. **备份 `/tmp/plat30/android-11/android.jar`**（见第二节）
2. **`~/workspace/magpie-android-repo` 还是 1.0.48 老壳**，与工作区差 28 个文件。
   要发 1.4.2 得跑 `sh scripts/release.sh 1.4.2 142`

### 中

3. 工作区有大量**别的会话遗留**，约 450 MB 可清：
   - `/tmp/aicode-mapping.txt`（128 MB）、`/tmp/PKG.txt`
   - `~/workspace/{jadx.zip, apktool.jar}`（53 MB）
   - `~/workspace/Snowluma-*`（84 MB）
   - `~/workspace/{apk_extract, apktool_out, apktool_dbgw, jadx_out, jadx_dbgw, jadx}`
   - `~/workspace/{aicode, aicode-xp, aicode-prompt-importer, aicode-prompt-patch}`
   - `~/workspace/{eta_analysis, pojia-1, xiangqi-auto, my-app, myapp-shell}`
   - `~/workspace/{doubao-gateway, opencode-doubao-gateway, wechat-onebot}`
   - `~/workspace/{long-flow, skill_pkg, subagent-plugin, tmp, tools}`
   - `~/workspace/{magpie-android-backup, magpie-rebuild}`

   **⚠️ `mqgw-android` 是独立项目**（沐晴账号池网关），删前确认。

### 低

4. `~/.aimini/attachments/` 里有 60 MB 的 `magpie-android-v0.4.zip` 和历史截图

---

## 十一、用户偏好（务必遵守）

1. **发版由 AI 代跑**，用户只下指令如「发 x.y.z」
2. Release body **不要**写「构建由 AI 辅助完成」
3. 界面要跟 magpie 网页「一模一样」，**不要自作主张加东西**
   （我曾自己加了「4 个路由组 / 自动分组」卡片，被要求删掉）
4. 用户会直接发网页截图说「照这个做」，**以截图为准**
5. 说话直接，不要客套
6. 署名：酷安 @图图没耳朵（coolapk.com/u/1642567）

---

## 十二、一句话总结

**magpie 的 Go 二进制没动过，外面那层从 WebView 换成了原生 View。**

四个 tab 直连 magpie 的 HTTP API，网页版整个降级成「打开控制后台」入口。
所有数据都来自 `127.0.0.1`，不出手机。
