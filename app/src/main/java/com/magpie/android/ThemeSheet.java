package com.magpie.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 界面主题：明暗模式 + 界面风格，选中立刻整页重建。
 *
 * <p>为什么单独做一个面板而不是塞进设置页：设置页那套是
 * 「改完点保存 → 重启进程」的重流程，而主题是随点随看的，混在一起会让
 * 用户以为还得点保存。这里选完直接 {@code recreate()}，一秒钟看到效果。
 *
 * <p>风格预览是用当前主题的令牌临时画的一小块样品，不是截图 —— 所以
 * 深浅色切换时预览也跟着变，不会出现「预览是深色、界面变浅色」的错位。
 */
final class ThemeSheet {

    private ThemeSheet() {
    }

    static void show(final Activity act, final Ui ui) {
        // 供点击回调先关掉本弹窗，再交给 ThemeSwitch 做「淡出→重建→淡入」，
        // 不然弹窗会一直盖在淡出动画上面，看起来还是「啪一下变了」。
        final AlertDialog[] dlgHolder = new AlertDialog[1];
        ScrollView scroll = new ScrollView(act);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = ui.dp(18);
        root.setPadding(pad, ui.dp(6), pad, pad);
        scroll.addView(root, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(ui.text("界面主题", 17, M3.ON_SURFACE, true));

        TextView intro = ui.text("选完立刻生效，不用重启。"
                        + "「玻璃」是当前的样子，「原版」是最早那版整卡布局，"
                        + "「终端」是纯黑高对比，「墨」是深墨底单一信号色的数据工具风。"
                        + "「终端」「墨」是固定暗色，不受下面「明暗」设置影响。",
                12, M3.ON_SURFACE_VARIANT, false);
        intro.setLineSpacing(ui.dp(2), 1f);
        intro.setLayoutParams(ui.matchWrap(10));
        root.addView(intro);

        // ---- 一、明暗 ----
        root.addView(ui.groupTitle(R.drawable.ic_battery, "明暗"));

        LinearLayout modeRow = ui.row();
        modeRow.setLayoutParams(ui.matchWrap(12));
        final int curMode = Prefs.themeMode(act);
        String[] modeNames = {"跟随系统", "深色", "浅色"};
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            boolean on = curMode == mode;
            final TextView chip = ui.pill(modeNames[i],
                    on ? M3.PRIMARY_CONTAINER : M3.SURFACE_CONTAINER_HIGH,
                    on ? M3.ON_PRIMARY_CONTAINER : M3.ON_SURFACE);
            chip.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));
            LinearLayout.LayoutParams lp = ui.wrap();
            if (i > 0) {
                lp.leftMargin = ui.dp(8);
            }
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mode == Prefs.themeMode(act)) {
                        return;
                    }
                    Prefs.setThemeMode(act, mode);
                    if (dlgHolder[0] != null) {
                        dlgHolder[0].dismiss();
                    }
                    ThemeSwitch.recreate(act);
                }
            });
            modeRow.addView(chip);
        }
        root.addView(modeRow);

        // 当前主题是固定暗色时，「明暗」对它无效——给一句提示，免得用户点了没反应以为坏了。
        if (M3.theme(act).fixedDark) {
            TextView mHint = ui.text("当前「" + M3.theme(act).name
                            + "」是固定暗色，明暗设置对它无效（切到玻璃/原版才生效）",
                    11, M3.ON_SURFACE_VARIANT, false);
            mHint.setLayoutParams(ui.matchWrap(4));
            root.addView(mHint);
        }

        // ---- 二、风格 ----
        root.addView(ui.groupTitle(R.drawable.ic_layout_dashboard, "界面风格"));

        final int cur = M3.themeIndex(act);
        for (int i = 0; i < M3.THEMES.length; i++) {
            final int index = i;
            root.addView(themeRow(act, ui, M3.THEMES[i], cur == i, new Runnable() {
                @Override
                public void run() {
                    if (index == M3.themeIndex(act)) {
                        return;
                    }
                    M3.setThemeIndex(act, index);
                    if (dlgHolder[0] != null) {
                        dlgHolder[0].dismiss();
                    }
                    ThemeSwitch.recreate(act);
                }
            }));
        }

        TextView foot = ui.text("主题只管观感和排版，不影响网关、数据和任何功能设置。",
                11, M3.ON_SURFACE_VARIANT, false);
        foot.setLineSpacing(ui.dp(2), 1f);
        LinearLayout.LayoutParams flp = ui.matchWrap(0);
        flp.topMargin = ui.dp(10);
        foot.setLayoutParams(flp);
        root.addView(foot);

        AlertDialog dlg = new AlertDialog.Builder(act)
                .setView(scroll)
                .setNegativeButton("关闭", null)
                .create();
        dlgHolder[0] = dlg;
        try {
            if (dlg.getWindow() != null) {
                dlg.getWindow().setBackgroundDrawable(ui.dialogBg());
            }
        } catch (Exception ignored) {
        }
        dlg.show();
    }

    /** 一套主题的选择行：左边名称+说明，右边一块真实令牌画出来的样品。 */
    private static View themeRow(Activity act, Ui ui, final M3.Theme theme,
                                 boolean on, final Runnable pick) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = on
                ? ui.roundStroke(ui.col(M3.SURFACE_CONTAINER_HIGH), ui.style.innerRadius,
                ui.col(M3.PRIMARY), ui.style.borderWidth)
                : ui.roundStroke(ui.col(M3.SURFACE_CONTAINER_LOW), ui.style.innerRadius,
                ui.col(M3.OUTLINE_VARIANT), ui.style.borderWidth);
        ui.clickable(row, bg);
        row.setPadding(ui.dp(13), ui.dp(12), ui.dp(13), ui.dp(12));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = ui.dp(8);
        row.setLayoutParams(rlp);

        LinearLayout texts = ui.column();
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout titleRow = ui.row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(ui.text(theme.name, 14.5f,
                on ? M3.PRIMARY : M3.ON_SURFACE, true));
        if (on) {
            TextView badge = ui.pill("使用中", M3.PRIMARY_CONTAINER, M3.ON_PRIMARY_CONTAINER);
            badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f);
            badge.setPadding(ui.dp(7), ui.dp(2), ui.dp(7), ui.dp(2));
            LinearLayout.LayoutParams blp = ui.wrap();
            blp.leftMargin = ui.dp(7);
            titleRow.addView(badge, blp);
        }
        texts.addView(titleRow);

        TextView desc = ui.text(theme.desc, 11, M3.ON_SURFACE_VARIANT, false);
        desc.setLineSpacing(ui.dp(1), 1f);
        desc.setLayoutParams(ui.matchWrap(0));
        texts.addView(desc);
        row.addView(texts);

        row.addView(preview(act, ui, theme));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick.run();
            }
        });
        return row;
    }

    /**
     * 一块小样品：用目标主题自己的令牌画「页面底 + 一张卡 + 一个大数字」，
     * 圆角、描边、底色三样都能一眼看出差别。
     */
    private static View preview(Activity act, Ui ui, M3.Theme theme) {
        boolean dk = theme.fixedDark || M3.dark(act);
        final int[] t = dk ? theme.dark : theme.light;
        final M3.Style st = theme.style;

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pageBg = new GradientDrawable();
        pageBg.setColor(t[M3.SURFACE]);
        pageBg.setCornerRadius(ui.dp(st.innerRadius));
        box.setBackground(pageBg);
        box.setPadding(ui.dp(5), ui.dp(5), ui.dp(5), ui.dp(5));
        LinearLayout.LayoutParams blp = ui.wrap();
        blp.leftMargin = ui.dp(10);
        box.setLayoutParams(blp);

        // 卡内一块：卡片底色 + 该主题的描边策略
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(t[M3.SURFACE_CONTAINER_LOW]);
        cardBg.setCornerRadius(ui.dp(st.cardRadius * 0.5f));
        if (st.accentBorder) {
            cardBg.setStroke(Math.max(1, ui.dp(st.borderWidth)),
                    (t[M3.PRIMARY] & 0x00FFFFFF) | 0x52000000);
        } else {
            cardBg.setStroke(Math.max(1, ui.dp(st.borderWidth)), t[M3.OUTLINE_VARIANT]);
        }
        LinearLayout card = new LinearLayout(act);
        card.setBackground(cardBg);
        card.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6));
        if (st.elevationDp > 0) {
            card.setElevation(ui.dp(st.elevationDp * 0.5f));
        }

        TextView num = new TextView(act);
        num.setText("93.6");
        num.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        num.setTextColor(t[M3.ON_SURFACE]);
        num.setIncludeFontPadding(false);
        num.setTypeface(num.getTypeface(), android.graphics.Typeface.BOLD);
        if (st.monoNum) {
            num.setFontFeatureSettings("tnum");
        }
        card.addView(num);
        box.addView(card);

        // 第二块：卡内块底色，用来体现「三档底色」的第三层
        LinearLayout chip = new LinearLayout(act);
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(t[M3.SURFACE_CONTAINER_HIGH]);
        chipBg.setCornerRadius(ui.dp(st.innerRadius * 0.5f));
        chip.setBackground(chipBg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ui.dp(30), ui.dp(5));
        clp.topMargin = ui.dp(4);
        box.addView(chip, clp);

        return box;
    }
}
