package com.magpie.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 悬浮球 / 保活窗口设置面板（菜单 → 网关 → 悬浮球保活）。
 *
 * <p>设计：不再是一堆「标签 + 修改按钮」的表格，改成卡片式分组——
 * 每个能力一张卡（悬浮球 / 保活窗口），卡内是该能力的开关和参数，
 * 参数行用「图标 + 标题 + 说明 + 右侧值胶囊」的结构，点一下就能改。
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
        int pad = ui.dp(16);
        root.setPadding(pad, ui.dp(4), pad, ui.dp(10));
        scroll.addView(root, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // ================= 顶部说明 =================
        LinearLayout hero = ui.column();
        GradientDrawable heroBg = ui.glowStroke(ui.col(M3.PRIMARY_CONTAINER), 20,
                ui.col(M3.OUTLINE_VARIANT), 1);
        hero.setBackground(heroBg);
        hero.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        hero.setLayoutParams(ui.matchWrap(12));

        LinearLayout heroHead = ui.row();
        heroHead.setGravity(Gravity.CENTER_VERTICAL);
        heroHead.addView(ui.icon(R.drawable.ic_aperture, M3.ON_PRIMARY_CONTAINER, 22));
        TextView heroTitle = ui.text("为什么要开这个", 14, M3.ON_PRIMARY_CONTAINER, true);
        LinearLayout.LayoutParams htlp = ui.wrap();
        htlp.leftMargin = ui.dp(8);
        heroHead.addView(heroTitle, htlp);
        hero.addView(heroHead);

        TextView heroBody = ui.text(
                "部分系统（MIUI / 澎湃）即使电源设置选了「无限制」，息屏后仍会掐掉网关，"
                        + "AI 请求就断了。屏幕上有悬浮窗的应用，系统的冻结判定会宽松很多。",
                11.5f, M3.ON_PRIMARY_CONTAINER, false);
        heroBody.setLineSpacing(ui.dp(2), 1f);
        heroBody.setLayoutParams(ui.matchWrap(6));
        hero.addView(heroBody);
        root.addView(hero);

        // ================= 卡片 1：悬浮球 =================
        LinearLayout ballCard = card(act, ui);
        ballCard.addView(cardHead(act, ui, R.drawable.ic_aperture,
                "悬浮球", "屏幕上一个小圆球，可拖动、点击进 App"));

        final android.widget.Switch ballSw = switchRow(act, ui, "显示悬浮球",
                Prefs.ballEnabled(act));
        ballCard.addView(ballSw);

        final TextView iconValue = valueText(act, ui,
                Prefs.ballIconMode(act) == Prefs.BALL_ICON_DEFAULT ? "自身图标"
                        : Prefs.ballIconMode(act) == Prefs.BALL_ICON_CUSTOM ? "自定义" : "透明");
        ballCard.addView(paramRow(act, ui, "图标",
                "自身 logo / 相册图片 / 透明", iconValue, v -> pickIcon(act, ui, iconValue)));

        final TextView sizeValue = valueText(act, ui, Prefs.ballSizeDp(act) + " dp");
        ballCard.addView(paramRow(act, ui, "大小",
                "24 – 96 dp", sizeValue, v -> editInt(act, ui, "悬浮球直径（dp）",
                Prefs.ballSizeDp(act), 24, 96, n -> {
                    Prefs.setBallSizeDp(act, n);
                    sizeValue.setText(n + " dp");
                    FloatingBall.refresh(act);
                })));
        root.addView(ballCard);

        // ================= 卡片 2：保活窗口 =================
        LinearLayout keepCard = card(act, ui);
        keepCard.addView(cardHead(act, ui, R.drawable.ic_shield_check,
                "保活窗口", "一个几乎看不见的小窗口，专门用来防冻结"));

        final android.widget.Switch keepSw = switchRow(act, ui, "启用保活窗口",
                Prefs.keepAliveEnabled(act));
        keepCard.addView(keepSw);

        final TextView keepValue = valueText(act, ui, Prefs.ballKeepAliveDp(act) + " dp");
        keepCard.addView(paramRow(act, ui, "窗口大小",
                "1 dp 几乎隐形；个别系统会冻结超小窗口",
                keepValue, v -> editInt(act, ui, "保活窗口（dp）",
                Prefs.ballKeepAliveDp(act), 1, 16, n -> {
                    Prefs.setBallKeepAliveDp(act, n);
                    keepValue.setText(n + " dp");
                    FloatingBall.refresh(act);
                })));
        root.addView(keepCard);

        // ================= 行为 =================
        ballSw.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setBallEnabled(act, checked);
            if (checked && !ensurePermission(act, ui, () -> ballSw.setChecked(false))) {
                return;
            }
            if (checked) {
                String err = FloatingBall.showBall(act);
                if (err != null) {
                    toast(act, "开启失败：" + err);
                    Prefs.setBallEnabled(act, false);
                    ballSw.setChecked(false);
                }
            } else {
                FloatingBall.hideBall();
            }
        });

        keepSw.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setKeepAliveEnabled(act, checked);
            if (checked && !ensurePermission(act, ui, () -> keepSw.setChecked(false))) {
                return;
            }
            if (checked) {
                String err = FloatingBall.showKeepAlive(act);
                if (err != null) {
                    toast(act, "开启失败：" + err);
                    Prefs.setKeepAliveEnabled(act, false);
                    keepSw.setChecked(false);
                }
            } else {
                FloatingBall.hideKeepAlive();
            }
        });

        AlertDialog dlg = new AlertDialog.Builder(act)
                .setView(scroll)
                .setPositiveButton("完成", null)
                .create();
        styleDialog(act, ui, dlg);
        dlg.show();
    }

    // ------------------------------------------------------------------
    // 小部件
    // ------------------------------------------------------------------

    private static LinearLayout card(Activity act, Ui ui) {
        LinearLayout c = ui.column();
        c.setBackground(ui.cardBg());
        c.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(8));
        LinearLayout.LayoutParams lp = ui.matchWrap(10);
        c.setLayoutParams(lp);
        return c;
    }

    private static View cardHead(Activity act, Ui ui, int icon, String title, String sub) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(ui.matchWrap(8));
        LinearLayout iconBox = new LinearLayout(act);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(ui.round(ui.col(M3.SECONDARY_CONTAINER), 12));
        iconBox.addView(ui.icon(icon, M3.ON_SECONDARY_CONTAINER, 18));
        row.addView(iconBox, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));

        LinearLayout texts = ui.column();
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = ui.dp(10);
        row.addView(texts, tlp);
        texts.addView(ui.text(title, 14.5f, M3.ON_SURFACE, true));
        TextView s = ui.text(sub, 11, M3.ON_SURFACE_VARIANT, false);
        s.setLayoutParams(ui.matchWrap(2));
        texts.addView(s);
        return row;
    }

    private static android.widget.Switch switchRow(Activity act, Ui ui, String label, boolean on) {
        android.widget.Switch sw = new android.widget.Switch(act);
        sw.setText(label);
        sw.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13.5f);
        sw.setTextColor(ui.col(M3.ON_SURFACE));
        sw.setChecked(on);
        sw.setPadding(0, ui.dp(6), 0, ui.dp(6));
        return sw;
    }

    private static TextView valueText(Activity act, Ui ui, String s) {
        TextView v = ui.pill(s, M3.SURFACE_CONTAINER_HIGH, M3.ON_SURFACE);
        v.setPadding(ui.dp(12), ui.dp(5), ui.dp(12), ui.dp(5));
        return v;
    }

    /** 一行参数：标题 + 说明（左），值胶囊（右，点了改）。 */
    private static View paramRow(Activity act, Ui ui, String label, String sub,
                                 TextView value, View.OnClickListener onClick) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        row.setLayoutParams(ui.matchWrap(2));

        LinearLayout texts = ui.column();
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(texts, tlp);
        texts.addView(ui.text(label, 13, M3.ON_SURFACE, false));
        TextView s = ui.text(sub, 10.5f, M3.ON_SURFACE_VARIANT, false);
        s.setLayoutParams(ui.matchWrap(1));
        texts.addView(s);

        value.setOnClickListener(onClick);
        ui.clickable(value, ui.roundStroke(ui.col(M3.SURFACE_CONTAINER_HIGH), 999,
                ui.col(M3.OUTLINE_VARIANT), 1));
        row.addView(value);

        View divider = new View(act);
        divider.setBackgroundColor(ui.col(M3.OUTLINE_VARIANT));
        LinearLayout wrap = ui.column();
        wrap.addView(row);
        wrap.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return wrap;
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    /** 检查悬浮窗权限，没有就跳授权页。返回 true 表示已有权限。 */
    private static boolean ensurePermission(Activity act, Ui ui, Runnable onDenied) {
        if (FloatingBall.canDraw(act)) {
            return true;
        }
        toast(act, "先允许「显示在其他应用上层」，再回来打开");
        onDenied.run();
        FloatingBall.requestPermission(act);
        return false;
    }

    private static void pickIcon(final Activity act, final Ui ui, final TextView value) {
        String[] items = {"自身图标（默认）", "从相册选图片", "透明（只剩热区）"};
        AlertDialog d = new AlertDialog.Builder(act)
                .setTitle("悬浮球图标")
                .setItems(items, (dl, which) -> {
                    if (which == 1) {
                        Prefs.setBallIconMode(act, Prefs.BALL_ICON_CUSTOM);
                        Intent i = new Intent(Intent.ACTION_PICK);
                        i.setType("image/*");
                        try {
                            act.startActivityForResult(i, REQ_PICK_ICON);
                        } catch (Exception e) {
                            toast(act, "打不开相册");
                        }
                    } else {
                        Prefs.setBallIconMode(act, which == 2
                                ? Prefs.BALL_ICON_TRANSPARENT : Prefs.BALL_ICON_DEFAULT);
                        value.setText(which == 2 ? "透明" : "自身图标");
                        FloatingBall.refresh(act);
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        styleDialog(act, ui, d);
        d.show();
    }

    static void onActivityResult(Activity act, int req, int result, Intent data) {
        if (req != REQ_PICK_ICON || result != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        try {
            act.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        String path = copyToPrivate(act, uri);
        if (path == null) {
            toast(act, "读取图片失败");
            return;
        }
        Prefs.setBallIconMode(act, Prefs.BALL_ICON_CUSTOM);
        Prefs.setBallIconPath(act, path);
        FloatingBall.refresh(act);
        toast(act, "图标已更新");
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

    private static void toast(Activity act, String s) {
        android.widget.Toast.makeText(act, s, android.widget.Toast.LENGTH_SHORT).show();
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
