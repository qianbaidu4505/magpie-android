package com.magpie.android;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 一键导出诊断包：收集排障要用的现场信息，打成 zip 放到用户可见的位置。
 *
 * <p>收集内容（全部只读，不改任何状态）：
 * <ol>
 *   <li>info.txt —— 壳版本 / Android 版本 / 机型 / 网关与网页端口 / 启动方式</li>
 *   <li>magpie.log —— 网关二进制最后 500 行 stdout</li>
 *   <li>crash.txt —— 本包最近的系统崩溃日志（logcat -b crash 过滤）</li>
 *   <li>state.json —— magpie /api/state 里的非敏感字段
 *       （剥掉 keys / tokens / secret 一切凭据，只留版本、端口、开关）</li>
 *   <li>usage-tail.json —— 最近 10 条请求摘要（模型/状态/耗时/错误码，不含内容）</li>
 * </ol>
 *
 * <p>隐私红线：不打包 settings.json、providers.json、caller-keys.json 等任何
 * 含密钥/账号的文件；state.json 只留白名单字段；日志里的 web key 出现时打码。
 */
final class DiagnosticsPack {

    private static final String TAG = "MagpieDiag";

    private DiagnosticsPack() {
    }

    /** 生成并写入 zip，返回目标文件；失败抛异常带原因。 */
    static File export(Activity act) throws Exception {
        File dir = new File("/sdcard/Download");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            // /sdcard 不可用时退回应用私有目录（用户仍可从文件管理器拿，路径提示里说明）
            dir = new File(act.getExternalFilesDir(null), "Download");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                dir = act.getCacheDir();
            }
        }
        String ver = version(act);
        File out = new File(dir, "magpie-diag-" + ver + "-"
                + new java.text.SimpleDateFormat("MMdd-HHmmss", java.util.Locale.US)
                .format(new java.util.Date()) + ".zip");

        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out))) {
            put(z, "info.txt", buildInfo(act, ver));
            // 日志打码后打包：magpie 启动横幅会打出 web key，那是网页控制台万能钥匙
            put(z, "magpie.log", safe(GatewayService.lastLog(500)));
            put(z, "crash.txt", crashLog());
            put(z, "state.json", stateSafe(act));
            put(z, "usage-tail.json", usageTail(act));
        }
        return out;
    }

    /** 给用户看的产出位置说明。 */
    static String where(File f) {
        String p = f.getAbsolutePath();
        if (p.startsWith("/sdcard/")) {
            return "已导出到 " + p;
        }
        return "已导出到（应用目录）" + p;
    }

    // ------------------------------------------------------------------

    private static String version(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName == null ? "?" : pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String buildInfo(Context ctx, String ver) {
        StringBuilder sb = new StringBuilder();
        sb.append("magpie-android 诊断包\n");
        sb.append("生成时间: ").append(
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.US).format(new java.util.Date())).append('\n');
        sb.append("壳版本: ").append(ver).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("机型: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n');
        sb.append("网关端口: ").append(GatewayService.gatewayPort(ctx)).append('\n');
        sb.append("网页端口: ").append(GatewayService.webPort(ctx)).append('\n');
        sb.append("网关运行中: ").append(Updater.isRunning() ? "是（更新中）" : "—").append('\n');
        sb.append("局域网共享: ").append(Prefs.lan(ctx) ? "开" : "关").append('\n');
        sb.append("保活: ").append(Prefs.autostart(ctx) ? "开机自启开" : "关").append('\n');
        return sb.toString();
    }

    /** 本包最近的 crash 日志；没有 root/adb 权限时返回提示。 */
    private static String crashLog() {
        try {
            Process p = new ProcessBuilder("logcat", "-b", "crash", "-d", "-t", "200")
                    .redirectErrorStream(true).start();
            String all = readAll(p.getInputStream());
            StringBuilder sb = new StringBuilder();
            for (String line : all.split("\n")) {
                if (line.contains("com.magpie.android") || line.contains("FATAL")) {
                    sb.append(line).append('\n');
                }
            }
            return sb.length() > 0 ? sb.toString() : "（本包最近没有崩溃记录）";
        } catch (Exception e) {
            return "（读不到 crash 日志：" + e.getMessage() + "）";
        }
    }

    /**
     * /api/state 的白名单版本：只留版本号、端口、开关类布尔值，
     * 剥掉 keys / tokens / providers / 一切凭据。
     */
    private static String stateSafe(Context ctx) {
        try {
            MagpieApi.Result r = MagpieApi.stateLast(ctx);
            if (!r.ok()) {
                return "{\"error\": \"" + safe(r.why()) + "\"}";
            }
            JSONObject o = r.json();
            JSONObject out = new JSONObject();
            out.put("version", o.optString("version", ""));
            JSONObject settings = o.optJSONObject("settings");
            if (settings != null) {
                JSONObject s = new JSONObject();
                // 白名单：脱敏相关 + 语言 + 代理（无凭据）
                for (String k : new String[]{"redact", "redactPersonal", "lang",
                        "proxy", "pluginProxy", "lan", "gatewayMode", "theme"}) {
                    if (settings.has(k)) {
                        s.put(k, settings.get(k));
                    }
                }
                org.json.JSONArray words = settings.optJSONArray("redactWords");
                if (words != null) {
                    // 只留数量，不留词内容
                    s.put("redactWordsCount", words.length());
                }
                out.put("settings", s);
            }
            return out.toString(2);
        } catch (Exception e) {
            return "{\"error\": \"" + safe(e.getMessage()) + "\"}";
        }
    }

    /** 最近 10 条请求摘要：模型/状态/耗时/错误码，不含请求内容。 */
    private static String usageTail(Context ctx) {
        try {
            MagpieApi.Result r = MagpieApi.requests(ctx, "today", 10);
            if (!r.ok()) {
                return "{\"error\": \"" + safe(r.why()) + "\"}";
            }
            JSONObject o = r.json();
            JSONArray rows = NativeData.arr(o, "rows");
            JSONArray out = new JSONArray();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = NativeData.at(rows, i);
                if (row == null) {
                    continue;
                }
                JSONObject slim = new JSONObject();
                slim.put("model", row.optString("model", ""));
                slim.put("status", row.optInt("status", 0));
                slim.put("ms", row.optDouble("ms", 0));
                String err = row.optString("err", "");
                if (!err.isEmpty()) {
                    slim.put("err", err.length() > 160
                            ? err.substring(0, 160) + "…" : err);
                }
                out.put(slim);
            }
            JSONObject wrap = new JSONObject();
            wrap.put("total", o.optInt("total", 0));
            wrap.put("rows", out);
            return wrap.toString(2);
        } catch (Exception e) {
            return "{\"error\": \"" + safe(e.getMessage()) + "\"}";
        }
    }

    private static void put(ZipOutputStream z, String name, String content)
            throws Exception {
        if (content == null) {
            content = "";
        }
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[1 << 14];
        int n;
        while ((n = in.read(b)) != -1) {
            buf.write(b, 0, n);
        }
        in.close();
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        // web key 打码
        return s.replaceAll("k=[0-9a-f]{8,}", "k=***")
                .replaceAll("(?i)(key|token|secret)=[^\\s\"&]+", "$1=***");
    }
}
