package com.magpie.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 悬浮球设置面板（菜单 → 网关 → 悬浮球）。
 *
 * <p>选项：开关、图标（自身/相册图片/透明）、悬浮球大小、保活窗口大小。
 * 参考图 2 的布局：每行「标签 + 修改按钮」。
 */
final class FloatingBallSheet {

    private static final int REQ_PICK_ICON = 0x8B01;

    private FloatingBallSheet() {
    }

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

        root.addView(ui.text("悬浮球", 17, M3.ON_SURFACE, true));

        TextView intro = ui.text(
                "部分系统（MIUI / 澎湃）即使在电源设置里选了「无限制」，息屏后仍会掐掉网关"
                        + "导致 AI 请求中断。加一个悬浮窗后系统会把它当成「屏幕上有东西」的应用，"
                        + "冻结判定宽松很多——这是社区广泛用的保活手段。\n\n"
                        + "同时会挂一个几乎看不见的保活小窗口（默认 1dp），个别系统会冻结超小窗口，"
                        + "卡顿时可以把保活窗口调大一点。",
                11.5f, M3.ON_SURFACE_VARIANT, false);
        intro.setLineSpacing(ui.dp(2), 1f);
        intro.setLayoutParams(ui.matchWrap(8));
        root.addView(intro);

        // ---- 总开关 ----
        final android.widget.Switch sw = new android.widget.Switch(act);
        sw.setText("网关运行时显示悬浮球");
        sw.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
        sw.setTextColor(ui.col(M3.ON_SURFACE));
        sw.setChecked(Prefs.ballEnabled(act));
        root.addView(sw);

        // ---- 图标 ----
        final TextView iconValue = ui.text("", 12.5f, M3.ON_SURFACE, false);
        root.addView(iconRow(act, ui, "图标", iconValue, "修改", v -> pickIcon(act, ui)));

        // ---- 悬浮球大小 ----
        final TextView sizeValue = ui.text(Prefs.ballSizeDp(act) + " dp",
                12.5f, M3.ON_SURFACE, false);
        root.addView(iconRow(act, ui, "悬浮球大小", sizeValue, "修改",
                v -> editInt(act, ui, "悬浮球直径（dp）", Prefs.ballSizeDp(act), 24, 96,
                        n -> {
                            Prefs.setBallSizeDp(act, n);
                            sizeValue.setText(n + " dp");
                            FloatingBall.refresh(act);
                        })));

        // ---- 保活窗口 ----
        final TextView keepValue = ui.text(Prefs.ballKeepAliveDp(act) + " dp",
                12.5f, M3.ON_SURFACE, false);
        root.addView(iconRow(act, ui, "保活窗口大小", keepValue, "修改",
                v -> editInt(act, ui, "保活窗口（dp，1=几乎不可见）",
                        Prefs.ballKeepAliveDp(act), 1, 16, n -> {
                            Prefs.setBallKeepAliveDp(act, n);
                            keepValue.setText(n + " dp");
                            FloatingBall.refresh(act);
                        })));

        TextView keepHint = ui.text("保活窗口承载隐形会话；个别系统会冻结超小窗口，"
                        + "卡顿时可调大（仍近乎隐形）。", 11, M3.ON_SURFACE_VARIANT, false);
        keepHint.setLineSpacing(ui.dp(2), 1f);
        keepHint.setLayoutParams(ui.matchWrap(8));
        root.addView(keepHint);

        final Runnable syncIconLabel = () -> {
            int mode = Prefs.ballIconMode(act);
            iconValue.setText(mode == Prefs.BALL_ICON_DEFAULT ? "自身图标"
                    : mode == Prefs.BALL_ICON_CUSTOM ? "自定义图片" : "透明（不可见）");
        };
        syncIconLabel.run();

        sw.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setBallEnabled(act, checked);
            if (!checked) {
                FloatingBall.hide();
                return;
            }
            if (!FloatingBall.canDraw(act)) {
                FloatingBall.requestPermission(act);
                sw.setChecked(false);
                Prefs.setBallEnabled(act, false);
                android.widget.Toast.makeText(act,
                        "先允许「显示在其他应用上层」，再回来打开",
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            String err = FloatingBall.show(act);
            if (err != null) {
                android.widget.Toast.makeText(act, "开启失败：" + err,
                        android.widget.Toast.LENGTH_LONG).show();
                sw.setChecked(false);
                Prefs.setBallEnabled(act, false);
            }
        });

        AlertDialog dlg = new AlertDialog.Builder(act)
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .create();
        styleDialog(act, ui, dlg);
        dlg.show();
    }

    private static void pickIcon(final Activity act, final Ui ui) {
        String[] items = {"自身图标（默认）", "从相册选图片", "透明（只剩热区）"};
        AlertDialog d = new AlertDialog.Builder(act)
                .setTitle("选择悬浮球图标")
                .setItems(items, (dl, which) -> {
                    if (which == 1) {
                        Prefs.setBallIconMode(act, Prefs.BALL_ICON_CUSTOM);
                        Intent i = new Intent(Intent.ACTION_PICK);
                        i.setType("image/*");
                        try {
                            act.startActivityForResult(i, REQ_PICK_ICON);
                        } catch (Exception e) {
                            android.widget.Toast.makeText(act, "打不开相册",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Prefs.setBallIconMode(act, which == 2
                                ? Prefs.BALL_ICON_TRANSPARENT : Prefs.BALL_ICON_DEFAULT);
                        FloatingBall.refresh(act);
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        styleDialog(act, ui, d);
        d.show();
    }

    /** Activity onActivityResult 转发过来。 */
    static void onActivityResult(Activity act, int req, int result, Intent data) {
        if (req != REQ_PICK_ICON || result != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        try {
            // 持久化读取权限 + 拷一份到私有目录（相册 URI 可能失效）
            act.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        String path = copyToPrivate(act, uri);
        if (path == null) {
            android.widget.Toast.makeText(act, "读取图片失败",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs.setBallIconMode(act, Prefs.BALL_ICON_CUSTOM);
        Prefs.setBallIconPath(act, path);
        FloatingBall.refresh(act);
        android.widget.Toast.makeText(act, "图标已更新",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private static String copyToPrivate(Activity act, Uri uri) {
        try (java.io.InputStream in = act.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            java.io.File out = new java.io.File(act.getFilesDir(), "ball-icon.png");
            try (java.io.OutputStream os = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
            }
            return out.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    /** 「标签 — 值 [修改]」一行。 */
    private static LinearLayout iconRow(Activity act, Ui ui, String label,
                                        TextView value, String action,
                                        android.view.View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(ui.matchWrap(6));

        TextView l = ui.text(label, 13.5f, M3.ON_SURFACE, true);
        row.addView(l, ui.weight(1));
        row.addView(value);
        TextView btn = ui.pill(action, M3.SECONDARY_CONTAINER, M3.ON_SECONDARY_CONTAINER);
        btn.setPadding(ui.dp(14), ui.dp(7), ui.dp(14), ui.dp(7));
        LinearLayout.LayoutParams blp = ui.wrap();
        blp.leftMargin = ui.dp(10);
        btn.setOnClickListener(onClick);
        row.addView(btn, blp);
        return row;
    }

    private static void editInt(Activity act, Ui ui, String title, int cur,
                                int min, int max, java.util.function.IntConsumer apply) {
        final android.widget.EditText input = new android.widget.EditText(act);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(cur));
        input.setTextSize(14f);
        input.setHint(min + " - " + max);
        AlertDialog d = new AlertDialog.Builder(act)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("保存", (dl, w) -> {
                    try {
                        int n = Integer.parseInt(input.getText().toString().trim());
                        apply.accept(Math.max(min, Math.min(max, n)));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        styleDialog(act, ui, d);
        d.show();
    }

    private static void styleDialog(Activity act, Ui ui, AlertDialog dlg) {
        try {
            if (dlg.getWindow() != null) {
                dlg.getWindow().setBackgroundDrawable(ui.dialogBg());
            }
        } catch (Exception ignored) {
        }
    }
}
