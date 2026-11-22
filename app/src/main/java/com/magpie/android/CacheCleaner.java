package com.magpie.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 缓存清理：把 magpie 攒下来但已经不用的东西删掉。
 *
 * <p>为什么需要：magpie 自己的 cache 目录从不清理，实测在手机上能长到 350 MB+。
 * 里面主要是：
 * <ul>
 *   <li>{@code bun/<旧版本>/} —— Bun 升级后旧版本整个目录留着，每个约 108 MB</li>
 *   <li>{@code update/<hash>/} —— 内核更新包下载完的残留，几十 MB</li>
 *   <li>{@code astrlink-guard/<hash>/} —— 脱敏模型文件，关掉脱敏后仍在</li>
 *   <li>{@code models.json}、{@code plugin-github.json} —— 可重新下载的索引</li>
 * </ul>
 *
 * <p>这些都是**可再生**的：删了 magpie 下次用到会重新下载/重建。
 * 不碰 {@code data/} 下的配置、日志、使用记录。
 *
 * <p>面板分三块：当前占用、可清理项（带大小）、清理按钮。
 */
final class CacheCleaner {

    private static final String TAG = "MagpieCache";

    private CacheCleaner() {
    }

    // ==================================================================
    // 清什么
    // ==================================================================

    /** 一个可清理的目标。 */
    private static final class Target {
        final String label;
        final String note;
        final File path;
        /** 是否「整目录删」，false 表示删单个文件。 */
        final boolean dir;
        /** 算出来之前是 -1。 */
        long size = -1;

        Target(String label, String note, File path, boolean dir) {
            this.label = label;
            this.note = note;
            this.path = path;
            this.dir = dir;
        }
    }

    /** magpie 的 cache 根：filesDir/magpie/data/cache。 */
    private static File cacheRoot(Context ctx) {
        return new File(GatewayService.dataDir(ctx), "cache");
    }

    /**
     * 列出所有可清理项。每次都重新扫，因为 Bun 版本号会变。
     */
    private static List<Target> targets(Context ctx) {
        File root = cacheRoot(ctx);
        List<Target> out = new ArrayList<>();

        // 1. Bun：只留 state.json 里 current 那个版本，其余全算垃圾
        File bunRoot = new File(root, "bun");
        String current = readBunCurrent(bunRoot);
        File[] buns = bunRoot.listFiles();
        if (buns != null) {
            for (File d : buns) {
                if (!d.isDirectory()) {
                    continue;
                }
                if (d.getName().equals(current) || d.getName().equals("install-cache")) {
                    continue;
                }
                out.add(new Target("旧版 Bun " + d.getName(),
                        "升级后留下的旧运行时，当前用的是 " + current, d, true));
            }
        }

        // 2. update：内核更新包残留
        File update = new File(root, "update");
        File[] ups = update.listFiles();
        if (ups != null) {
            for (File d : ups) {
                out.add(new Target("更新包残留 " + d.getName(),
                        "内核/壳更新下载后没删的临时文件", d, true));
            }
        }

        // 3. astrlink-guard：脱敏模型（关掉脱敏就没用了）
        File guard = new File(root, "astrlink-guard");
        File[] gs = guard.listFiles();
        if (gs != null) {
            for (File d : gs) {
                if (d.getName().startsWith("found-")) {
                    continue; // 这个很小，留着
                }
                out.add(new Target("脱敏模型 " + d.getName(),
                        "关闭脱敏后不再使用；再开会在设置里重新下载", d, d.isDirectory()));
            }
        }

        // 4. 可重建的索引/缓存文件
        String[][] files = {
                {"models.json", "模型目录", "magpie 会重新拉一次"},
                {"plugin-github.json", "插件 GitHub 索引", "magpie 会重新拉一次"},
                {"plugin-npm.json", "插件 npm 索引", "magpie 会重新拉一次"},
                {"labs.json", "实验室数据", "magpie 会重新拉一次"},
                {"page-v1.json", "用量页快照", "打开用量页时重建"},
                {"log-v1.bin", "用量日志索引", "从 usage.jsonl 重建，可能慢几秒"},
        };
        for (String[] f : files) {
            File p = new File(root, f[0]);
            if (p.exists() && p.isFile()) {
                out.add(new Target(f[1], f[0] + " · " + f[2], p, false));
            }
        }

        return out;
    }

    /** 从 bun/state.json 读当前版本，读不到返回 ""。 */
    private static String readBunCurrent(File bunRoot) {
        File st = new File(bunRoot, "state.json");
        if (!st.isFile()) {
            return "";
        }
        try {
            String s = readAll(st);
            int i = s.indexOf("\"current\"");
            if (i < 0) {
                return "";
            }
            int q1 = s.indexOf('"', s.indexOf(':', i));
            if (q1 < 0) {
                return "";
            }
            int q2 = s.indexOf('"', q1 + 1);
            if (q2 < 0) {
                return "";
            }
            return s.substring(q1 + 1, q2);
        } catch (Exception e) {
            return "";
        }
    }

    private static String readAll(File f) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) {
                bos.write(b, 0, n);
            }
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    /** 递归算目录大小。 */
    private static long sizeOf(File f) {
        if (f == null || !f.exists()) {
            return 0;
        }
        if (f.isFile()) {
            return f.length();
        }
        long total = 0;
        File[] kids = f.listFiles();
        if (kids == null) {
            return 0;
        }
        for (File k : kids) {
            total += sizeOf(k);
        }
        return total;
    }

    private static void delete(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    delete(k);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double v = bytes;
        String[] u = {"KB", "MB", "GB"};
        int i = -1;
        do {
            v /= 1024;
            i++;
        } while (v >= 1024 && i < u.length - 1);
        return String.format(java.util.Locale.US, "%.1f %s", v, u[i]);
    }

    // ==================================================================
    // 面板
    // ==================================================================

    static void show(final Activity act, final Ui ui) {
        ScrollView scroll = new ScrollView(act);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = ui.dp(18);
        root.setPadding(pad, ui.dp(6), pad, pad);
        scroll.addView(root, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- 标题 ----
        root.addView(ui.text("缓存清理", 17, M3.ON_SURFACE, true));

        TextView intro = ui.text(
                "magpie 的缓存从不自动清理，实测能长到 350 MB 以上。"
                        + "下面这些都可以安全删掉——都是能重新下载或重建的东西。\n"
                        + "不会动你的配置、日志、使用记录。",
                12, M3.ON_SURFACE_VARIANT, false);
        intro.setLineSpacing(ui.dp(2), 1f);
        intro.setLayoutParams(ui.matchWrap(4));
        root.addView(intro);

        // ---- 一、当前占用 ----
        root.addView(sectionTitle(act, ui, "当前占用"));
        final LinearLayout statusBox = ui.column();
        statusBox.setLayoutParams(ui.matchWrap(4));
        root.addView(statusBox);

        TextView refresh = ui.pill("重新扫描", M3.SURFACE_CONTAINER_HIGH, M3.ON_SURFACE);
        refresh.setPadding(ui.dp(18), ui.dp(9), ui.dp(18), ui.dp(9));
        refresh.setLayoutParams(ui.matchWrap(8));
        root.addView(refresh);

        // ---- 二、可清理项 ----
        root.addView(sectionTitle(act, ui, "可清理项"));
        final LinearLayout listBox = ui.column();
        listBox.setLayoutParams(ui.matchWrap(4));
        root.addView(listBox);

        // ---- 三、清理 ----
        root.addView(sectionTitle(act, ui, "一键清理"));
        TextView hint = ui.text("删掉上面列出的全部项目。清理过程中界面会卡一下，属正常。",
                12, M3.ON_SURFACE_VARIANT, false);
        hint.setLineSpacing(ui.dp(2), 1f);
        hint.setLayoutParams(ui.matchWrap(4));
        root.addView(hint);

        TextView clean = ui.pill("全部清理", M3.PRIMARY_CONTAINER, M3.ON_PRIMARY_CONTAINER);
        clean.setPadding(ui.dp(18), ui.dp(9), ui.dp(18), ui.dp(9));
        clean.setLayoutParams(ui.matchWrap(10));
        root.addView(clean);

        AlertDialog dlg = new AlertDialog.Builder(act)
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .create();
        styleDialog(act, ui, dlg);
        dlg.show();

        // ---- 行为 ----
        final Runnable[] loadRef = new Runnable[1];
        loadRef[0] = () -> {
            statusBox.removeAllViews();
            listBox.removeAllViews();
            statusBox.addView(ui.text("正在扫描…", 11.5f, M3.ON_SURFACE_VARIANT, false));

            new Thread(() -> {
                File cacheDir = cacheRoot(act);
                long total = sizeOf(cacheDir);
                List<Target> ts = targets(act);
                long junk = 0;
                for (Target t : ts) {
                    t.size = sizeOf(t.path);
                    junk += t.size;
                }
                Collections.sort(ts, (a, b) -> Long.compare(b.size, a.size));

                final long fTotal = total;
                final long fJunk = junk;
                final List<Target> fTs = ts;
                act.runOnUiThread(() -> {
                    statusBox.removeAllViews();
                    statusBox.addView(row(act, ui, "缓存目录总计", human(fTotal)));
                    statusBox.addView(row(act, ui, "可清理", human(fJunk)));
                    statusBox.addView(row(act, ui, "清理后剩余", human(Math.max(0, fTotal - fJunk))));

                    listBox.removeAllViews();
                    if (fTs.isEmpty()) {
                        listBox.addView(ui.text("没有可清理的东西。", 11.5f,
                                M3.ON_SURFACE_VARIANT, false));
                        return;
                    }
                    for (Target t : fTs) {
                        listBox.addView(item(act, ui, t.label, t.note + " · " + human(t.size)));
                    }
                });
            }, "magpie-cache-scan").start();
        };

        refresh.setOnClickListener(v -> loadRef[0].run());
        loadRef[0].run();

        clean.setOnClickListener(v -> confirmClean(act, ui, dlg, loadRef[0]));
    }

    private static void confirmClean(final Activity act, final Ui ui,
                                     final AlertDialog parent, final Runnable after) {
        AlertDialog c = new AlertDialog.Builder(act)
                .setTitle("清理缓存")
                .setMessage("删掉所有可清理项？\n\n"
                        + "这些都是可再生数据：模型索引、插件索引、旧版 Bun、更新包残留。\n"
                        + "配置、日志、使用记录不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清理", (d, w) -> {
                    new Thread(() -> {
                        List<Target> ts = targets(act);
                        long freed = 0;
                        int n = 0;
                        for (Target t : ts) {
                            long s = sizeOf(t.path);
                            delete(t.path);
                            if (!t.path.exists()) {
                                freed += s;
                                n++;
                            }
                        }
                        final long fFreed = freed;
                        final int fN = n;
                        act.runOnUiThread(() -> {
                            android.widget.Toast.makeText(act,
                                    "已清理 " + fN + " 项，释放 " + human(fFreed),
                                    android.widget.Toast.LENGTH_LONG).show();
                            if (after != null) {
                                after.run();
                            }
                        });
                    }, "magpie-cache-clean").start();
                })
                .create();
        styleDialog(act, ui, c);
        c.show();
    }

    // ==================================================================
    // 小部件
    // ==================================================================

    private static TextView sectionTitle(Activity act, Ui ui, String s) {
        TextView t = ui.text(s, 12.5f, M3.PRIMARY, true);
        t.setLayoutParams(ui.matchWrap(14));
        return t;
    }

    /** 一行「标签 —— 值」。 */
    private static LinearLayout row(Activity act, Ui ui, String label, String value) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setLayoutParams(ui.matchWrap(4));

        TextView l = ui.text(label, 11.5f, M3.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        r.addView(l, lp);

        TextView v = ui.text(value, 12, M3.ON_SURFACE, true);
        r.addView(v);
        return r;
    }

    /** 一个可清理项：标题 + 说明。 */
    private static LinearLayout item(Activity act, Ui ui, String title, String sub) {
        LinearLayout box = ui.column();
        box.setBackground(ui.roundStroke(ui.col(M3.SURFACE_CONTAINER), 12,
                ui.col(M3.OUTLINE_VARIANT), 1));
        int p = ui.dp(10);
        box.setPadding(p, p, p, p);
        box.setLayoutParams(ui.matchWrap(6));

        box.addView(ui.text(title, 12, M3.ON_SURFACE, true));
        TextView s = ui.text(sub, 10.5f, M3.ON_SURFACE_VARIANT, false);
        s.setPadding(0, ui.dp(2), 0, 0);
        box.addView(s);
        return box;
    }

    private static void styleDialog(Activity act, Ui ui, AlertDialog dlg) {
        try {
            if (dlg.getWindow() != null) {
                dlg.getWindow().setBackgroundDrawable(
                        ui.round(ui.col(M3.SURFACE_CONTAINER_LOW), 24));
            }
        } catch (Exception ignored) {
        }
    }
}
