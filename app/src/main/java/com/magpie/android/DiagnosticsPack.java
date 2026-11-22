package com.magpie.android;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
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
 *   <li>magpie.log —— 网关二进制最后 2000 行 stdout</li>
 *   <li>crash.txt —— 本包最近的系统崩溃日志（logcat -b crash 过滤）</li>
 *   <li>state.json —— magpie /api/state 里的非敏感字段
 *       （剥掉 keys / tokens / secret 一切凭据，只留版本、端口、开关）</li>
 *   <li>usage-tail.json —— 最近 100 条请求摘要（模型/状态/耗时/错误码/token，不含内容），
 *       附当日/本周的上下行字节聚合（来自 /api/usage）</li>
 *   <li>netstats.json —— 系统口径的本 App 流量（NetworkStatsManager），
 *       以及 Magpie 与其它高流量 App 的对比；排查「流量暴涨记在谁头上」用</li>
 *   <li>vpn.json —— 当前 VPN 是否激活、哪个包名、VPN App 自身的流量</li>
 *   <li>connections.txt —— 本 App 进程树的外部 TCP 连接（IP:端口，不含 127.0.0.1），
 *       从 /proc/net/tcp* 读，普通权限能读本 uid 的</li>
 *   <li>plugins.json —— 已安装插件的 spec 与版本（不含 options 里的人设文本与密钥）</li>
 * </ol>
 *
 * <p>隐私红线：不打包 settings.json、providers.json、caller-keys.json 等任何
 * 含密钥/账号的文件；state.json 只留白名单字段；日志里的 web key 出现时打码。
 */
final class DiagnosticsPack {

    private static final String TAG = "MagpieDiag";

    /** 作者邮箱：发送至作者时收件人用，也复制到剪贴板（防止邮箱 App 带不动收件人）。 */
    static final String AUTHOR_EMAIL = "lkjhg131456@qq.com";

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
        // 文件名短一些：太长经某些邮件/IM 客户端会被截断甚至拒收。
        // 收集时间放在 zip 里的 info.txt 首行，文件名只留版本 + 当天日期。
        File out = new File(dir, "magpie-diag-" + ver + "-"
                + new java.text.SimpleDateFormat("MMdd", java.util.Locale.US)
                .format(new java.util.Date()) + ".zip");

        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out))) {
            put(z, "info.txt", buildInfo(act, ver));
            // 日志打码后打包：magpie 启动横幅会打出 web key，那是网页控制台万能钥匙
            put(z, "magpie.log", safe(GatewayService.lastLog(2000)));
            put(z, "crash.txt", crashLog());
            put(z, "state.json", stateSafe(act));
            put(z, "usage-tail.json", usageTail(act));
            // 网络诊断：流量归属（系统口径）、VPN 状态、本 App 的外部连接。
            // 全部不需要 root；都是只读查询，不改任何状态。
            put(z, "netstats.json", netStats(act));
            put(z, "vpn.json", vpnInfo(act));
            put(z, "connections.txt", connections());
            put(z, "plugins.json", pluginsSafe(act));
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
        // 收集时间只在这里显示（文件名里已经为了兼容邮件/IM 截掉了），
        // 用完整时间戳精确到秒，方便和日志时间对齐
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
            MagpieApi.Result r = MagpieApi.requests(ctx, "today", 100);
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
                slim.put("provider", row.optString("providerName",
                        row.optString("provider", "")));
                slim.put("agent", row.optString("agentName", row.optString("agent", "")));
                slim.put("status", row.optInt("status", 0));
                slim.put("ms", row.optDouble("ms", 0));
                slim.put("in", row.optInt("in", 0));
                slim.put("out", row.optInt("out", 0));
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
            // 当日/本周的上下行字节聚合（/api/usage 的 up_bytes/down_bytes）——
            // 流量暴涨时先看这里：网关口径总共走了多少
            try {
                MagpieApi.Result u = MagpieApi.usage(ctx, "today");
                if (u.ok()) {
                    JSONObject uo = u.json();
                    JSONObject day = new JSONObject();
                    day.put("calls", NativeData.num(uo, "calls"));
                    day.put("errors", NativeData.num(uo, "errors"));
                    day.put("retried", NativeData.num(uo, "retried"));
                    day.put("canceled", NativeData.num(uo, "canceled"));
                    day.put("up_bytes", NativeData.lng(uo, "up_bytes"));
                    day.put("down_bytes", NativeData.lng(uo, "down_bytes"));
                    wrap.put("today", day);
                }
            } catch (Exception ignored) {
            }
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

    // ==================================================================
    // 网络诊断（1.7.1 新增）
    // ==================================================================

    /**
     * 系统口径的本 App 流量（TrafficStats 的 uid 计数，开机以来累计）。
     *
     * <p>为什么不用 NetworkStatsManager：那个要 READ_PHONE_STATE 权限且按
     * 时间窗查；TrafficStats 读 uid 计数器只要一个静态调用，且足够回答
     * 「这个 App 从开机起总共走了多少」——跟系统流量排行里的数字同源。
     *
     * <p>同时拉 VPN 那一类的常见包名做对比：流量暴涨记在谁头上，
     * 一眼可读。
     */
    private static String netStats(Context ctx) {
        JSONObject out = new JSONObject();
        try {
            int uid = android.os.Process.myUid();
            out.put("myUid", uid);
            out.put("rx_bytes", TrafficStats.getUidRxBytes(uid));
            out.put("tx_bytes", TrafficStats.getUidTxBytes(uid));
            out.put("total_rx_bytes", TrafficStats.getTotalRxBytes());
            out.put("total_tx_bytes", TrafficStats.getTotalTxBytes());
            // 我的占比（开机以来）：流量暴涨时这个数应该接近排行上的数
            long my = TrafficStats.getUidRxBytes(uid) + TrafficStats.getUidTxBytes(uid);
            long all = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
            out.put("my_share_pct", all > 0 ? Math.round(my * 10000.0 / all) / 100.0 : 0);

            // 常见代理/VPN 的 uid 流量对比（拿不到就是 -1，无妨）
            JSONArray apps = new JSONArray();
            String[] candidates = {"com.android.vpndialogs", "flclash", "com.github.kr328.clash",
                    "com.v2ray.ang", "moe.matsuri.lite", "io.github.dogedog.doge",
                    "com.v2ray.ang.fdroid", "eu.nevila.v2ray", "com.taobao.taobao"};
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            for (String pkg : candidates) {
                try {
                    int u = pm.getPackageUid(pkg, 0);
                    if (u < 0) {
                        continue;
                    }
                    JSONObject a = new JSONObject();
                    a.put("pkg", pkg);
                    a.put("rx", TrafficStats.getUidRxBytes(u));
                    a.put("tx", TrafficStats.getUidTxBytes(u));
                    apps.put(a);
                } catch (Exception ignored) {
                }
            }
            out.put("proxy_apps", apps);
            return out.toString(2);
        } catch (Exception e) {
            return "{\"error\": \"" + safe(e.getMessage()) + "\"}";
        }
    }

    /**
     * VPN 状态：是否激活、哪个包、走的是什么网络。
     *
     * <p>排查「VPN 分流把 magpie 流量导回了隧道」用：
     * 有 VPN 激活 + Magpie 流量巨大 = 隧道流量嫌疑。
     */
    private static String vpnInfo(Context ctx) {
        JSONObject out = new JSONObject();
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return "{\"error\": \"no connectivity service\"}";
            }
            Network[] nets = cm.getAllNetworks();
            JSONArray list = new JSONArray();
            boolean vpnActive = false;
            for (Network n : nets) {
                NetworkCapabilities cap = cm.getNetworkCapabilities(n);
                if (cap == null) {
                    continue;
                }
                JSONObject o = new JSONObject();
                boolean vpn = cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                o.put("vpn", vpn);
                o.put("wifi", cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
                o.put("cellular", cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
                if (vpn) {
                    vpnActive = true;
                    LinkProperties lp = cm.getLinkProperties(n);
                    if (lp != null) {
                        o.put("interface", lp.getInterfaceName());
                        JSONArray addrs = new JSONArray();
                        // getAllAddresses 在新 API 才有；android-11 里是 getLinkAddresses()
                        if (Build.VERSION.SDK_INT >= 30) {
                            for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                                addrs.put(la.getAddress().getHostAddress());
                            }
                        }
                        o.put("addresses", addrs);
                    }
                }
                list.put(o);
            }
            out.put("vpn_active", vpnActive);
            out.put("networks", list);

            // VPN App 是谁：找拥有 VPN 接口的前台包不可行（需系统权限），
            // 退而求其次：列出已知的 VPN/代理包是否安装
            JSONArray installed = new JSONArray();
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            String[] known = {"flclash", "com.github.kr328.clash", "com.v2ray.ang",
                    "com.v2ray.ang.fdroid", "moe.matsuri.lite", "eu.nevila.v2ray",
                    "com.drazzik.inbox.v2ray", "io.github.dogedog.doge"};
            for (String pkg : known) {
                try {
                    pm.getPackageInfo(pkg, 0);
                    installed.put(pkg);
                } catch (Exception ignored) {
                }
            }
            out.put("known_proxy_apps_installed", installed);
            return out.toString(2);
        } catch (Exception e) {
            return "{\"error\": \"" + safe(e.getMessage()) + "\"}";
        }
    }

    /**
     * 本 App 进程树的外部 TCP 连接。
     *
     * <p>/proc/net/tcp 和 /proc/net/tcp6 是全局表，普通权限只能看到
     * 本 uid 的连接的详情，其它 uid 的行 uid 列是 -1（内核限制）。
     * 这里把 inode 对到 /proc/<pid>/fd/ 里自己的 socket，只留自己的。
     *
     * <p>排查「magpie 到底连着谁」：IP、端口、状态一次看全。
     */
    private static String connections() {
        StringBuilder sb = new StringBuilder();
        try {
            // 自己进程树里所有 socket inode
            java.util.Map<String, String> inodes = new java.util.HashMap<>();
            File proc = new File("/proc");
            File[] dirs = proc.listFiles();
            if (dirs != null) {
                for (File d : dirs) {
                    if (!d.isDirectory()) {
                        continue;
                    }
                    String name = d.getName();
                    boolean num = true;
                    for (char c : name.toCharArray()) {
                        if (c < '0' || c > '9') {
                            num = false;
                            break;
                        }
                    }
                    if (!num) {
                        continue;
                    }
                    File fdDir = new File(d, "fd");
                    File[] fds = fdDir.listFiles();
                    if (fds == null) {
                        continue; // 无权限读别的进程的 fd
                    }
                    for (File f : fds) {
                        String target = readlink(f.getAbsolutePath());
                        if (target.startsWith("socket:")) {
                            String ino = target.substring(8, target.length() - 1);
                            inodes.put(ino, name);
                        }
                    }
                }
            }
            sb.append("# 本 uid 的外部 TCP 连接（不含 loopback）\n");
            sb.append("# local            remote            state       pid\n");
            int shown = 0;
            shown += dumpTcp(sb, "/proc/net/tcp", false, inodes);
            shown += dumpTcp(sb, "/proc/net/tcp6", true, inodes);
            if (shown == 0) {
                sb.append("（当前无外部连接）\n");
            }
        } catch (Exception e) {
            sb.append("读取失败：").append(e.getMessage()).append('\n');
        }
        return sb.toString();
    }

    /** 解析一份 /proc/net/tcp[6]，返回写出的行数。 */
    private static int dumpTcp(StringBuilder sb, String path, boolean ipv6,
                               java.util.Map<String, String> inodes) {
        int n = 0;
        try {
            String all = readAll(new FileInputStream(path));
            String[] lines = all.split("\n");
            for (int i = 1; i < lines.length; i++) { // 第一行是表头
                String[] cols = lines[i].trim().split("\\s+");
                if (cols.length < 10) {
                    continue;
                }
                String local = cols[1], remote = cols[2], st = cols[3], ino = cols[9];
                String pid = inodes.get(ino);
                if (pid == null) {
                    continue; // 不是本 App 进程树的 socket
                }
                String rIp = ipOf(remote, ipv6);
                // 略过 loopback
                if (rIp.startsWith("127.")) {
                    continue;
                }
                sb.append(String.format("%-22s %-22s %-11s %s%n",
                        addrOf(local, ipv6), addrOf(remote, ipv6), tcpState(st), pid));
                n++;
            }
        } catch (Exception ignored) {
        }
        return n;
    }

    /** /proc/net/tcp 的地址是 AAAAAAAAPBBBBBBBB 十六进制，还原成 ip:port。 */
    private static String addrOf(String hex, boolean ipv6) {
        try {
            String[] p = hex.split(":");
            long port = Long.parseLong(p[1], 16);
            if (!ipv6) {
                long v = Long.parseLong(p[0], 16);
                return String.format("%d.%d.%d.%d:%d",
                        v & 0xff, (v >> 8) & 0xff, (v >> 16) & 0xff, (v >> 24) & 0xff, port);
            }
            // IPv6：4 段 little-endian 32 位
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                long v = Long.parseLong(p[0].substring(i * 8, i * 8 + 8), 16);
                if (i > 0) {
                    sb.append(':');
                }
                sb.append(String.format("%02x%02x:%02x%02x",
                        v & 0xff, (v >> 8) & 0xff, (v >> 16) & 0xff, (v >> 24) & 0xff));
            }
            return sb.append(':').append(port).toString();
        } catch (Exception e) {
            return hex;
        }
    }

    private static String ipOf(String hexAddr, boolean ipv6) {
        String a = addrOf(hexAddr, ipv6);
        int i = a.lastIndexOf(':');
        return i > 0 ? a.substring(0, i) : a;
    }

    private static String tcpState(String hex) {
        switch (hex) {
            case "01": return "ESTABLISHED";
            case "02": return "SYN_SENT";
            case "03": return "SYN_RECV";
            case "06": return "TIME_WAIT";
            case "08": return "CLOSE_WAIT";
            default: return hex;
        }
    }

    /** readlink：API 21+ 有 Os.readlink，但它在 android.system 里且抛异常；这里直接用 shell 的 readlink，简单可靠。 */
    private static String readlink(String path) {
        try {
            Process p = new ProcessBuilder("readlink", path)
                    .redirectErrorStream(true).start();
            String s = readAll(p.getInputStream()).trim();
            p.waitFor();
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 已安装插件的 spec 与版本。
     *
     * <p>隐私：只取 spec（npm 包名或 git 地址）和版本号，
     * 不取 options（那里有人设文本和可能的密钥）。
     */
    private static String pluginsSafe(Context ctx) {
        JSONObject out = new JSONObject();
        try {
            File f = new File(GatewayService.dataDir(ctx), "plugins.json");
            if (!f.isFile()) {
                return "{\"note\": \"没有 plugins.json\"}";
            }
            JSONObject o = new JSONObject(readAll(new FileInputStream(f)));
            JSONArray ps = o.optJSONArray("plugins");
            JSONArray out2 = new JSONArray();
            if (ps != null) {
                for (int i = 0; i < ps.length(); i++) {
                    JSONObject p = NativeData.at(ps, i);
                    if (p == null) {
                        continue;
                    }
                    JSONObject slim = new JSONObject();
                    slim.put("spec", p.optString("spec", ""));
                    if (p.has("off")) {
                        slim.put("off", p.optBoolean("off", false));
                    }
                    out2.put(slim);
                }
            }
            out.put("plugins", out2);
            return out.toString(2);
        } catch (Exception e) {
            return "{\"error\": \"" + safe(e.getMessage()) + "\"}";
        }
    }
}
