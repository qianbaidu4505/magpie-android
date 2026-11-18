#!/bin/sh
# 一键发版：构建 → 验签 → 同步源码到开源 repo → commit/tag/push → 建 GitHub Release 传 APK。
#
# 用法：
#   scripts/release.sh <version_name> <version_code>
#   例：scripts/release.sh 1.0.27 27
#
# 依赖（都在容器里，一般不用动）：
#   ANDROID_JAR=/tmp/plat30/android-11/android.jar
#   ANDROID_SDK=/opt/android-sdk
#   开源 repo 默认在 ../magpie-android-repo（可用 REPO_DIR 覆盖）
#
# 说明：
#   - 不导出到手机 /sdcard/Download：容器里没有 /sdcard，那步由 App 内的 Root 工具做。
#   - GitHub 凭据走 ~/.aimini/git-credential-aimini（git credential fill 动态取，不落盘到脚本）。
#   - keystore / 内核二进制 / aapt2 不会进 repo（.gitignore 已排除）。
set -euo pipefail

VERSION_NAME="${1:-}"
VERSION_CODE="${2:-}"

if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
    echo "用法：$0 <version_name> <version_code>" >&2
    exit 2
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export ANDROID_JAR="${ANDROID_JAR:-$HOME/.aimini/android-sdk/android-11/android.jar}"
export ANDROID_SDK="${ANDROID_SDK:-/opt/android-sdk}"

REPO_DIR="${REPO_DIR:-$ROOT/../magpie-android-repo}"
APK="$ROOT/dist/magpie-android-$VERSION_NAME.apk"
TAG="v$VERSION_NAME"

# 用哪个 build-tools 里的 apksigner/d8（挑第一个带 lib/apksigner.jar 的）
bt_dir() {
    for d in "$ANDROID_SDK"/build-tools/* "$ANDROID_SDK"/android-*; do
        [ -d "$d" ] || continue
        [ -f "$d/lib/apksigner.jar" ] && echo "$d" && return 0
    done
    return 1
}
BT="$(bt_dir || true)"

echo "==> 构建 $VERSION_NAME (code $VERSION_CODE)"
VERSION_NAME="$VERSION_NAME" VERSION_CODE="$VERSION_CODE" sh scripts/build-apk-local.sh

echo "==> 验签"
if [ -n "$BT" ]; then
    java -jar "$BT/lib/apksigner.jar" verify "$APK"
else
    echo "警告：没找到 apksigner，跳过验签" >&2
fi

echo "==> SHA-256"
sha256sum "$APK"

echo "==> 同步源码到 $REPO_DIR"
[ -d "$REPO_DIR/.git" ] || { echo "找不到开源 repo：$REPO_DIR" >&2; exit 1; }
rm -rf "$REPO_DIR/app/src/main"
cp -r app/src/main "$REPO_DIR/app/src/main"
rm -rf "$REPO_DIR/app/src/main/assets/bin"
cp -f scripts/build-apk-local.sh scripts/build-magpie.sh scripts/sync-binary.sh \
    "$REPO_DIR/scripts/" 2>/dev/null || true

# README / 开发文档也一起同步，别让发布页说反了
if [ -f README.github.md ]; then cp -f README.github.md "$REPO_DIR/README.md"; fi
if [ -f README.md ]; then cp -f README.md "$REPO_DIR/README.dev.md"; fi

cd "$REPO_DIR"

echo "==> commit + tag + push"
git add -A
if ! git diff --cached --quiet; then
    git commit -q -m "发布 $VERSION_NAME"
fi
if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "tag $TAG 已存在；如要重发请先 git tag -d $TAG && git push origin :refs/tags/$TAG" >&2
    exit 1
fi
git tag "$TAG"
git push -q origin main
git push -q origin "$TAG"

echo "==> 取 GitHub 凭据"
TOKEN="$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | sed -n 's/^password=//p')"
if [ -z "$TOKEN" ]; then
    echo "拿不到 GitHub 凭据（credential helper 没返回 token）" >&2
    exit 1
fi
OWNER="$(git remote get-url origin | sed -E 's#.*github.com[/:]([^/]+)/.*#\1#')"
REPO="$(git remote get-url origin | sed -E 's#.*github.com[/:][^/]+/([^/]+?)(\.git)?$#\1#')"
[ -n "$OWNER" ] && [ -n "$REPO" ] || { echo "解析 owner/repo 失败" >&2; exit 1; }

BODY="Magpie 的 Android 壳（非官方移植）。

本版：$VERSION_NAME

## 本次更新

### 修复：通知栏一直「正在启动网关」

系统重投递服务时，通知会被刷回启动文案却不再纠正。现在通知始终与网关真实状态对齐。

### 修复：检查 App 更新失败

GitHub API 匿名访问返回 403 后，自动回退到 releases.atom 继续查，不再报「GitHub 返回 403」；同时不再内置任何 token。

### 修复：供应商余额进度条

进度条改为按「聚合后的总额度已用」画，不再因为某个账号用光而整条标红。

### 菜单精简

移除「停止网关」和「重新加载网页」两个残留项。

### 新增：文件管理器

⋮ → 插件 → 文件管理器。

- 浏览 magpie 私有 HOME 目录（filesDir/home）。
- 从手机导入文件到任意子目录、新建文件夹、删除、重命名。
- 长按/点文件可复制**壳内绝对路径**，方便给插件填路径。
- 不经过 WebView 文件选择器，手机导入文件不再点不开。

### 账号页：模型工具

- 「添加供应商」入口换成两个按钮：
  - **刷新模型**：手动向各家供应商重新拉一次模型表。
  - **全部模型**：弹出面板，按供应商列出当前全部模型。
- 每个模型显示：显示名、倍率、能力胶囊（免费 / 视觉 / 推理 / 生图 / 生视频）、模型 id。
- **长按模型行复制完整引用**（供应商id/模型id），配合路由组的「模式」匹配用。

### 局域网开关与 magpie 同步

壳里的「允许局域网访问」现在会同步写 magpie 自己的「局域网共享」，不用再两边各点一次；反过来在网页里改，回到设置页也会同步回壳。

### 通知栏地址修正

开启局域网后，通知栏的网关地址不再显示 0.0.0.0，固定显示本机 127.0.0.1。

### 菜单：复制网页登录链接

⋮ → 其他 → 复制网页登录链接，粘到浏览器直接登进 magpie 后台，不再卡在「magpie web」登录页。

### 冻结提醒（1.4.3 已有，继续生效）

小米 / 澎湃OS 的省电策略会在 App 退后台约 40 秒后冻结整个进程组，导致 AI 智能体卡在「正在思考」。

- 打开 App 时：如果是小米系且没设「无限制」，发通知引导直达设置。
- 后台回来时：超过 40 秒补一条「刚才可能被冻结」的提醒。
- 7 天最多一次。

### 之前版本已含

- 上下文地图（矩形分区图）。
- 额度写「剩余 / 总额度」，进度条画「已用」。
- 请求记录最新在上、会话轮次最新在上。
- 通知栏点进原生界面。
- 关于页补壳仓库与作者链接。
- 天枢等带名字的 Key 启停修复。

## 安装
下载本 Release 的 APK 直接安装。升级须用同一签名的 APK 覆盖安装；不要先卸载，否则本地配置、账号和数据会被清除。

当前只提供 Android arm64 包，最低 Android 7.0（API 24）。

## 说明
本项目基于 magpie（MIT）制作，仅供学习交流；内核版权归 magpie 原作者所有，如涉及侵权请联系删除。"

echo "==> 创建 Release"
# JSON 必须先落文件再用 @ 传给 curl。
# 直接 -d "$JSON" 会让 shell/echo 层吃掉反斜杠转义，GitHub 回 400 Problems parsing JSON。
REL_JSON_FILE="$(mktemp)"
trap 'rm -f "$REL_JSON_FILE" "$REL_JSON_FILE.out"' EXIT
python3 - "$REL_JSON_FILE" "$TAG" "$VERSION_NAME" "$BODY" <<'PY'
import json, sys
path, tag, ver, body = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
with open(path, 'w', encoding='utf-8') as f:
    json.dump({"tag_name": tag, "name": "Magpie for Android " + ver,
               "body": body, "draft": False, "prerelease": False},
              f, ensure_ascii=False)
PY

# 用 Python 直接发请求并解析，不再依赖 curl -w 重定向出的 .code 文件
# （那个临时文件在 curl 失败时会缺失，导致解析脚本 Traceback）。
RELEASE_ID="$(python3 - "$REL_JSON_FILE" "$TOKEN" "$OWNER" "$REPO" <<'PY'
import json, sys, urllib.request, urllib.error
path, token, owner, repo = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
payload = open(path, 'rb').read()
req = urllib.request.Request(
    f"https://api.github.com/repos/{owner}/{repo}/releases",
    data=payload, method="POST",
    headers={"Authorization": "token " + token,
             "Accept": "application/vnd.github+json",
             "Content-Type": "application/json"})
try:
    d = json.load(urllib.request.urlopen(req))
except urllib.error.HTTPError as e:
    sys.stderr.write("创建 Release HTTP %s: %s\n" % (e.code, e.read().decode()[:300]))
    print("")
    sys.exit(0)
print(d.get("id", ""))
PY
)"

[ -n "$RELEASE_ID" ] || { echo "创建 Release 失败" >&2; exit 1; }
echo "    release id = $RELEASE_ID"

echo "==> 上传 APK（约 75MB，耐心等）"
ASSET_NAME="$(basename "$APK")"
python3 - "$TOKEN" "$OWNER" "$REPO" "$RELEASE_ID" "$APK" "$ASSET_NAME" <<'PY'
import json, sys, http.client

_, token, owner, repo, rid, apk, name = sys.argv
data = open(apk, 'rb').read()
conn = http.client.HTTPSConnection("uploads.github.com", timeout=1800)
conn.request(
    "POST",
    f"/repos/{owner}/{repo}/releases/{rid}/assets?name={name}",
    body=data,
    headers={"Authorization": "token " + token,
             "Content-Type": "application/vnd.android.package-archive",
             "Content-Length": str(len(data))})
resp = conn.getresponse()
out = resp.read().decode()
d = json.loads(out) if out.strip().startswith('{') else {}
print("    HTTP %s" % resp.status)
print("    asset: %s %s bytes" % (d.get("name"), d.get("size", "")))
if d.get("message"):
    print("    error: %s" % d.get("message"))
PY

echo
echo "完成："
echo "  源码:   https://github.com/$OWNER/$REPO"
echo "  Release: https://github.com/$OWNER/$REPO/releases/tag/$TAG"
echo "  APK:    $APK"
echo "  SHA-256: $(sha256sum "$APK" | cut -d' ' -f1)"
echo
echo "下一步：在 App 内用 Root 工具把 APK 导出到 /sdcard/Download（容器里没有 /sdcard）。"
