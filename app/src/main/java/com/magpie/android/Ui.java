package com.magpie.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 原生 M3 组件工厂。
 *
 * <p>项目里没有 AndroidX、没有 Material 组件库，所有观感都靠这里现搭：
 * 一块圆角底 + 一组 {@link M3} 令牌。颜色永远从令牌表取，不写死任何色值 ——
 * 换种子色、莫奈取色、深色模式就自动跟着变。
 *
 * 尺寸口径：卡片圆角 20dp、卡内边距 16dp、卡片间距 10dp、正文 14sp。
 */
final class Ui {

    final Context ctx;
    final int[] c;
    final float density;
    /**
     * 当前主题的风格参数：圆角、描边、阴影、字号、网格线全从这里取。
     * 页面上任何几何常数都必须读它，别再写死数字，否则切主题会半新半旧。
     */
    final M3.Style style;
    /** 是不是「带主色描边」的主题：卡片换发光描边、大圆角、独立 2×2 网格。 */
    final boolean design;

    Ui(Context ctx) {
        this.ctx = ctx;
        this.c = M3.current(ctx);
        this.density = ctx.getResources().getDisplayMetrics().density;
        this.style = M3.style(ctx);
        this.design = this.style.accentBorder;
    }

    // ------------------------------------------------------------------
    // 尺寸与颜色
    // ------------------------------------------------------------------

    int dp(float v) {
        return Math.round(v * density);
    }

    int col(int role) {
        return c[role];
    }

    // ------------------------------------------------------------------
    // 基础图形
    // ------------------------------------------------------------------

    /**
     * 一个矢量图标（Lucide，24×24、2px 描边），按 M3 role 染色。
     *
     * <p>为什么换掉字符符号：以前菜单/底栏用的是 `♡ ⚙ ⚑ ⌘ ⟳` 这些 Unicode
     * 字符，不同 ROM 字体渲染粗细/大小/基线全不一样，看起来廉价且不齐。
     * Lucide 是 24×24 网格、统一 2px 描边的开源图标集（ISC 许可），跟
     * M3 卡片的视觉语言天然契合。
     *
     * @param resId drawable 资源（res/drawable/ic_*.xml）
     * @param role  M3 颜色角色（染色）
     * @param sizeDp 图标边长（dp）
     */
    android.widget.ImageView icon(int resId, int role, float sizeDp) {
        android.widget.ImageView iv = new android.widget.ImageView(ctx);
        iv.setImageResource(resId);
        iv.setColorFilter(c[role]);
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        int s = dp(sizeDp);
        iv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(s, s));
        return iv;
    }

    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    GradientDrawable roundStroke(int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable d = round(color, radiusDp);
        d.setStroke(Math.max(1, dp(strokeDp)), strokeColor);
        return d;
    }

    /**
     * 发光描边：设计稿主题下用主色低透明描边（加粗、更亮、有发光感），
     * 其它主题退回普通描边。所有内嵌小卡/菜单/胶囊统一走这里。
     */
    GradientDrawable glowStroke(int color, float radiusDp, int fallbackStroke, float fallbackDp) {
        if (design) {
            int stroke = (c[M3.PRIMARY] & 0x00FFFFFF) | 0x52000000;
            return roundStroke(color, radiusDp, stroke, style.borderWidth);
        }
        return roundStroke(color, radiusDp, fallbackStroke, style.borderWidth > 0
                ? style.borderWidth : fallbackDp);
    }

    /**
     * 弹窗统一底：跟概览页的统计卡一个模子 —— 玻璃白底 + 主色低透明发光
     * 描边 + 24dp 大圆角。所有 AlertDialog 的背景都走这里，保证弹窗和
     * 页面卡片长得像一家人。
     */
    GradientDrawable dialogBg() {
        return glowStroke(c[M3.SURFACE_CONTAINER_LOW], style.cardRadius,
                c[M3.OUTLINE_VARIANT], 1f);
    }

    /** 弹窗文字按钮统一底：透明底 + 主色细描边胶囊（点起来像一颗卡）。 */
    GradientDrawable dialogBtnBg() {
        int stroke = (c[M3.PRIMARY] & 0x00FFFFFF) | 0x52000000;
        return roundStroke(0x00000000, 999f, stroke, 1.2f);
    }

    /** 给一个可点卡片挂上发光底 + 悬浮阴影。 */
    void glowCard(View v, GradientDrawable bg) {
        v.setBackground(bg);
        if (style.elevationDp > 0) {
            v.setElevation(dp(style.elevationDp * 0.75f));
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                v.setOutlineAmbientShadowColor(0x22000000);
                v.setOutlineSpotShadowColor(0x33000000);
            }
        } else {
            v.setElevation(0);
        }
    }

    /** 卡片底：surface container low + 1dp outline variant 描边。 */
    GradientDrawable cardBg() {
        if (design) {
            // 经典：卡片用玻璃底 + 青色发光描边，描边加粗，圆角更大更软。
            // 描边用主色的低透明版本，视觉上像发光的边缘。
            int stroke = (c[M3.PRIMARY] & 0x00FFFFFF) | 0x52000000;
            return roundStroke(c[M3.SURFACE_CONTAINER_LOW], style.cardRadius,
                    stroke, style.borderWidth);
        }
        // 紧凑：中性发丝线，靠三档底色（页面/卡片/卡内块）分层，不靠阴影。
        return roundStroke(c[M3.SURFACE_CONTAINER_LOW], style.cardRadius,
                c[M3.OUTLINE_VARIANT], style.borderWidth);
    }

    /** 点得动的卡片：叠一层涟漪。 */
    void clickable(View v, GradientDrawable bg) {
        int ripple = c[M3.ON_SURFACE] & 0x22FFFFFF;
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), bg, null));
        v.setClickable(true);
        v.setFocusable(true);
    }

    // ------------------------------------------------------------------
    // 卡片
    // ------------------------------------------------------------------

    LinearLayout card() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(cardBg());
        int p = dp(16);
        box.setPadding(p, p, p, p);
        if (style.elevationDp > 0) {
            // 悬浮感：经典主题的卡片要有柔和阴影，从背景里浮起来
            box.setElevation(dp(style.elevationDp));
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                box.setOutlineAmbientShadowColor(0x22000000);
                box.setOutlineSpotShadowColor(0x33000000);
            }
        } else {
            box.setElevation(0);
        }
        return box;
    }

    /** 一张卡片，带外边距（左右 14dp、上下 5dp，拼出 10dp 的卡间距）。 */
    LinearLayout cardWithMargin() {
        LinearLayout box = card();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(14), dp(5), dp(14), dp(5));
        box.setLayoutParams(lp);
        return box;
    }

    // ------------------------------------------------------------------
    // 统一排版组件（所有设置页/实验室页共用，保证视觉一致）
    // ------------------------------------------------------------------

    /**
     * 带图标的卡片标题：图标底块 + 标题 + 一行说明。
     * 所有页面的卡片头都用这个，别各写各的。
     */
    View cardTitle(int iconRes, String title, String sub) {
        LinearLayout row = row();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout iconBox = new LinearLayout(ctx);
        iconBox.setGravity(android.view.Gravity.CENTER);
        iconBox.setBackground(round(c[M3.SECONDARY_CONTAINER], 12));
        iconBox.addView(icon(iconRes, M3.ON_SECONDARY_CONTAINER, 18));
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(10);
        row.addView(texts, tlp);
        texts.addView(text(title, 14.5f, M3.ON_SURFACE, true));
        if (sub != null && !sub.isEmpty()) {
            TextView s = text(sub, 11, M3.ON_SURFACE_VARIANT, false);
            s.setLayoutParams(matchWrap(2));
            texts.addView(s);
        }
        return row;
    }

    /** 页面顶部说明条：primary 容器色底 + 图标 + 说明文字（比灰字醒目又不吵）。 */
    View banner(int iconRes, String text) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = glowStroke(c[M3.PRIMARY_CONTAINER], 18f,
                c[M3.OUTLINE_VARIANT], 1f);
        box.setBackground(bg);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout head = row();
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        if (iconRes != 0) {
            head.addView(icon(iconRes, M3.ON_PRIMARY_CONTAINER, 18));
        }
        TextView t = text(text, 11.5f, M3.ON_PRIMARY_CONTAINER, false);
        t.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (iconRes != 0) {
            tlp.leftMargin = dp(8);
        }
        head.addView(t, tlp);
        box.addView(head);
        return box;
    }

    /**
     * 设置行：左边「标题 + 说明」，右边挂任意 view（开关/值胶囊/按钮）。
     * 行与行之间自带一条细分隔线（最后一行由调用方决定要不要）。
     */
    View settingRow(String label, String sub, View right) {
        LinearLayout row = row();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(texts, tlp);
        texts.addView(text(label, 13, c[M3.ON_SURFACE], false));
        if (sub != null && !sub.isEmpty()) {
            TextView s = text(sub, 10.5f, M3.ON_SURFACE_VARIANT, false);
            s.setLineSpacing(dp(1), 1f);
            s.setLayoutParams(matchWrap(2));
            texts.addView(s);
        }
        if (right != null) {
            row.addView(right);
        }
        return row;
    }

    /** 一条细分隔线（设置行之间用）。注意：与页面上带上下边距的 divider() 区分。 */
    View thinDivider() {
        View v = new View(ctx);
        v.setBackgroundColor(c[M3.OUTLINE_VARIANT]);
        v.setAlpha(0.5f);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    /** 可点的值胶囊：显示当前值，点一下改。 */
    TextView valuePill(String s) {
        TextView v = pill(s, M3.SURFACE_CONTAINER_HIGH, M3.ON_SURFACE);
        v.setPadding(dp(12), dp(5), dp(12), dp(5));
        v.setBackground(roundStroke(c[M3.SURFACE_CONTAINER_HIGH], 999f,
                c[M3.OUTLINE_VARIANT], 1f));
        return v;
    }

    /** 一个分组的标题（带图标的彩色小标题）。 */
    View groupTitle(int iconRes, String s) {
        LinearLayout row = row();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(16), 0, dp(6));
        row.setLayoutParams(lp);
        if (iconRes != 0) {
            row.addView(icon(iconRes, M3.PRIMARY, 15));
            TextView t = text(s, 12.5f, M3.PRIMARY, true);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.leftMargin = dp(6);
            row.addView(t, tlp);
        } else {
            row.addView(text(s, 12.5f, M3.PRIMARY, true));
        }
        return row;
    }

    /** 一张设置卡（自带内外边距，直接塞内容）。 */
    LinearLayout settingCard() {
        LinearLayout c2 = card();
        LinearLayout.LayoutParams lp = matchWrap(10);
        c2.setLayoutParams(lp);
        c2.setPadding(dp(16), dp(12), dp(16), dp(12));
        return c2;
    }

    /** 卡片标题行：左边标题，右边可选的说明文字。 */
    LinearLayout cardHeader(String title, String trailing) {
        LinearLayout row = row();
        TextView t = text(title, 15, M3.ON_SURFACE, true);
        row.addView(t, weight(1));
        if (trailing != null && !trailing.isEmpty()) {
            TextView s = text(trailing, 12, M3.ON_SURFACE_VARIANT, false);
            row.addView(s);
        }
        return row;
    }

    // ------------------------------------------------------------------
    // 文字
    // ------------------------------------------------------------------

    TextView text(String s, float sp, int role, boolean bold) {
        return textColor(s, sp, c[role], bold);
    }

    /**
     * 按直接的 ARGB 值造字。
     *
     * <p>{@link #text} 的第三个参数是「令牌下标」，不是颜色值 —— 把算好的颜色
     * 传进去会越界（踩过：闪退在 ArrayIndexOutOfBoundsException）。
     * 任何已经算出来的颜色都走这个方法。
     */
    TextView textColor(String s, float sp, int argb, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s == null ? "" : s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(argb);
        if (bold) {
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        }
        t.setIncludeFontPadding(false);
        return t;
    }

    /**
     * 一个「大数字」TextView：字号和等宽都跟随主题。
     *
     * <p>tnum（tabular numbers）让每个数字占同样宽度，数字跳动时不会左右抖。
     * 只有紧凑主题开，经典主题保持原来的无衬线观感。
     */
    TextView bigNum(String s) {
        TextView t = text(s, style.bigNum, M3.ON_SURFACE, true);
        t.setSingleLine(true);
        t.setMaxLines(1);
        if (style.monoNum) {
            t.setFontFeatureSettings("tnum");
        }
        return t;
    }

    TextView title(String s) {
        return text(s, 20, M3.ON_SURFACE, true);
    }

    /** 小标题：页面里分组用的那种，全大写风格的灰字。 */
    TextView section(String s) {
        TextView t = text(s, 12, M3.ON_SURFACE_VARIANT, true);
        t.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(20), dp(16), dp(20), dp(4));
        t.setLayoutParams(lp);
        return t;
    }

    TextView mono(String s, float sp, int role) {
        TextView t = text(s, sp, role, false);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        return t;
    }

    // ------------------------------------------------------------------
    // 布局小工具
    // ------------------------------------------------------------------

    LinearLayout row() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams matchWrap(int topDp) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(topDp);
        return lp;
    }

    View spacer(int hDp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(hDp)));
        return v;
    }

    View divider() {
        View v = new View(ctx);
        v.setBackgroundColor(design ? 0x00000000 : c[M3.OUTLINE_VARIANT]);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(10);
        v.setLayoutParams(lp);
        return v;
    }

    /** 顶栏那种小胶囊标签。 */
    TextView pill(String s, int bgRole, int fgRole) {
        // 加大加饱满：字号 13、内边距 16/8（爸爸 2026-11-23 统一胶囊样式）
        TextView t = text(s, 13, fgRole, true);
        t.setBackground(round(c[bgRole], 999));
        int px = dp(16), py = dp(8);
        t.setPadding(px, py, px, py);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** 一个状态圆点。 */
    View dot(int colorRole, int sizeDp) {
        View v = new View(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(c[colorRole]);
        v.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        v.setLayoutParams(lp);
        return v;
    }

    // ------------------------------------------------------------------
    // 数字块
    // ------------------------------------------------------------------

    /**
     * 一个大数字 + 下面一行小字，用来铺「今日 token / 请求 / 费用」那排。
     * 四列并排时数字会自动缩字号，不会把卡片撑破。
     */
    View stat(String value, String label, int valueRole, float weight) {
        LinearLayout box = column();
        box.setGravity(Gravity.START);
        TextView v = text(value, 19, valueRole, true);
        v.setSingleLine(true);
        v.setMaxLines(1);
        TextView l = text(label, 11, M3.ON_SURFACE_VARIANT, false);
        l.setSingleLine(true);
        l.setLayoutParams(matchWrap(3));
        box.addView(v);
        box.addView(l);
        box.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        return box;
    }

    /**
     * 大滑动开关（Material 风格的独立开关，不是 Switch 复合控件）。
     *
     * <p>长 52dp 高 30dp 的胶囊轨道 + 一个 24dp 圆钮，开=主色轨道钮在右，
     * 关=灰轨道钮在左。为什么自绘：系统的 Switch 字号大时轨道会被文字撑开，
     * 放在标题旁边比例也不对；这个小开关就是给「放在一行右侧」用的。
     */
    View toggleSwitch(boolean on) {
        final float R = 15f;          // 轨道半高
        LinearLayout track = new LinearLayout(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(on ? c[M3.TERTIARY] : c[M3.OUTLINE_VARIANT]);
        bg.setCornerRadius(dp(R));
        track.setBackground(bg);
        int w = dp(52), h = dp(30);
        int pad = dp(3);
        track.setPadding(pad, pad, pad, pad);
        track.setGravity(on ? Gravity.END : Gravity.START);
        track.setClickable(true);
        track.setFocusable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        // 涟漪裁成圆角
        android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF), bg, bg);
        track.setBackground(ripple);
        track.setLayoutParams(lp);

        View knob = new View(ctx);
        GradientDrawable kb = new GradientDrawable();
        kb.setColor(0xFFFFFFFF);
        kb.setShape(GradientDrawable.OVAL);
        kb.setColor(on ? 0xFFFFFFFF : 0xFFF2F3F5);
        knob.setBackground(kb);
        knob.setElevation(dp(2));
        track.addView(knob, new LinearLayout.LayoutParams(dp(24), dp(24)));
        return track;
    }

    /** 带底色的数字块（bigStat 的卡片化版本）。 */
    View statTile(String value, String label, int bgRole, int fgRole) {
        LinearLayout box = column();
        box.setBackground(round(c[bgRole], 16));
        int p = dp(12);
        box.setPadding(p, p, p, p);
        TextView v = text(value, 22, fgRole, true);
        v.setSingleLine(true);
        TextView l = text(label, 11, fgRole, false);
        l.setAlpha(0.75f);
        l.setSingleLine(true);
        l.setLayoutParams(matchWrap(2));
        box.addView(v);
        box.addView(l);
        return box;
    }

    // ------------------------------------------------------------------
    // 进度条（额度用）
    // ------------------------------------------------------------------

    /**
     * 一条细进度条。remaining 是「还剩多少」（0..100）。剩余越低越危险，
     * 颜色从主色 → 三级色 → 错误色过渡，一眼能看出哪个账号快没了。
     */
    View quotaBar(double remaining) {
        return quotaBar(remaining, remaining);
    }

    /**
     * 一条细进度条，**进度和颜色分开**。
     *
     * <p>为什么需要两个参数：额度卡上写的是「剩余多少」，条却要画「已用多少」
     * （越满越危险）。只传一个数只能二选一，所以这里把“画多满”和“什么颜色”
     * 拆开：{@code fillPct} 是条的长度（0..100），{@code remainingPct} 只用来
     * 选颜色（快用完了变红）。
     */
    View quotaBar(double fillPct, double remainingPct) {
        LinearLayout wrapBox = new LinearLayout(ctx);
        wrapBox.setOrientation(LinearLayout.VERTICAL);
        wrapBox.setLayoutParams(matchWrap(8));

        ProgressBar pb = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(1000);
        pb.setProgress((int) Math.round(Math.max(0, Math.min(1, fillPct / 100.0)) * 1000));
        int tint = quotaColor(remainingPct);
        pb.setProgressTintList(ColorStateList.valueOf(tint));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(c[M3.SURFACE_CONTAINER_HIGHEST]));
        pb.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(7)));
        wrapBox.addView(pb);
        return wrapBox;
    }

    int quotaColor(double remaining) {
        // 余量语义：充足绿 / <20% 预警黄 / 耗尽红
        if (remaining <= 0) {
            return c[M3.ERROR];
        }
        if (remaining < 20) {
            return c[M3.QUOTA_WARN];
        }
        return c[M3.TERTIARY];
    }

    // ------------------------------------------------------------------
    // 横向滑动条（额度卡、排行）
    // ------------------------------------------------------------------

    HorizontalScrollView hScroll(View content) {
        HorizontalScrollView h = new HorizontalScrollView(ctx);
        h.setHorizontalScrollBarEnabled(false);
        h.setClipToPadding(false);
        h.setPadding(dp(14), 0, dp(14), 0);
        // 默认会把焦点交给第一个子 View 并自动滚过去 —— 一排卡片就会自己
        // 滑到中间，看起来像左边被截掉了。整棵子树的焦点一律挡掉。
        h.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        h.setFocusable(false);
        h.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return h;
    }

    // ------------------------------------------------------------------
    // 迷你柱状图
    // ------------------------------------------------------------------

    /**
     * 用量趋势：一根根柱子，高度按最大值归一。
     *
     * <p>自己画比塞 30 个 View 便宜得多，也不怕窄屏。
     */
    static final class BarChart extends View {

        private float[] values = new float[0];
        private String[] labels = new String[0];
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int color, emptyColor, labelColor;
        private boolean showLabels;
        private float density;

        BarChart(Context ctx) {
            super(ctx);
            density = ctx.getResources().getDisplayMetrics().density;
            barPaint.setStyle(Paint.Style.FILL);
            emptyPaint.setStyle(Paint.Style.FILL);
            labelPaint.setTextSize(9f * density);
            labelPaint.setTextAlign(Paint.Align.CENTER);
        }

        void style(int barColor, int empty, int labels2) {
            color = barColor;
            emptyColor = empty;
            labelColor = labels2;
            barPaint.setColor(barColor);
            emptyPaint.setColor(empty);
            labelPaint.setColor(labels2);
        }

        void setData(float[] v, String[] l, boolean withLabels) {
            values = v == null ? new float[0] : v;
            labels = l == null ? new String[0] : l;
            showLabels = withLabels;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec);
            setMeasuredDimension(w, Math.round((showLabels ? 92 : 70) * density));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (values.length == 0) {
                return;
            }
            float w = getWidth();
            float h = getHeight();
            float labelH = showLabels ? 14 * density : 0;
            float chartH = h - labelH;
            float gap = values.length > 24 ? 1f * density : 3f * density;
            float bw = Math.max(2f * density, (w - gap * (values.length - 1)) / values.length);
            float max = 0;
            for (float v : values) {
                max = Math.max(max, v);
            }
            if (max <= 0) {
                max = 1;
            }
            float radius = Math.min(bw / 2f, 4f * density);
            for (int i = 0; i < values.length; i++) {
                float left = i * (bw + gap);
                float ratio = values[i] / max;
                float bh = Math.max(2f * density, ratio * (chartH - 4 * density));
                r.set(left, chartH - bh, left + bw, chartH);
                barPaint.setColor(values[i] > 0 ? color : emptyColor);
                canvas.drawRoundRect(r, radius, radius, barPaint);
            }
            if (showLabels) {
                // 只画首、中、尾三个标签，多了挤成一团
                int[] idx = labels.length == 0 ? new int[0]
                        : new int[]{0, values.length / 2, values.length - 1};
                for (int i : idx) {
                    if (i < 0 || i >= labels.length || labels[i] == null) {
                        continue;
                    }
                    float cx = i * (bw + gap) + bw / 2f;
                    cx = Math.max(16 * density, Math.min(w - 16 * density, cx));
                    canvas.drawText(labels[i], cx, h - 2 * density, labelPaint);
                }
            }
        }
    }

    BarChart barChart() {
        BarChart b = new BarChart(ctx);
        b.style(c[M3.PRIMARY], c[M3.SURFACE_CONTAINER_HIGHEST], c[M3.ON_SURFACE_VARIANT]);
        return b;
    }

    // ------------------------------------------------------------------
    // 渐变面积折线图（设计稿那张）
    // ------------------------------------------------------------------

    /**
     * 平滑曲线 + 向下渐变填充的面积图，左侧带 Y 轴刻度与虚线网格。
     *
     * <p>用三次贝塞尔把相邻点连成平滑曲线（控制点取两点水平中点，
     * 这是最常用也最不容易过冲的做法），下面用 LinearGradient 从强调色
     * 渐隐到透明。
     */
    static final class AreaChart extends View {

        private float[] values = new float[0];
        private String[] labels = new String[0];
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path line = new android.graphics.Path();
        private final android.graphics.Path area = new android.graphics.Path();
        private int accent, grid, text, surface;
        private float density;

        AreaChart(Context ctx) {
            super(ctx);
            density = ctx.getResources().getDisplayMetrics().density;
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(2f * density);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            fillPaint.setStyle(Paint.Style.FILL);
            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setStrokeWidth(1f);
            gridPaint.setPathEffect(new android.graphics.DashPathEffect(
                    new float[]{3f * density, 4f * density}, 0));
            textPaint.setTextSize(9f * density);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            dotPaint.setStyle(Paint.Style.FILL);
        }

        void style(int accentColor, int gridColor, int textColor, int surfaceColor) {
            accent = accentColor;
            grid = gridColor;
            text = textColor;
            surface = surfaceColor;
            linePaint.setColor(accentColor);
            gridPaint.setColor(gridColor);
            textPaint.setColor(textColor);
            dotPaint.setColor(accentColor);
            invalidate();
        }

        void setData(float[] v, String[] l) {
            values = v == null ? new float[0] : v;
            labels = l == null ? new String[0] : l;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec),
                    Math.round(150 * density));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float padL = 26 * density, padR = 8 * density;
            float padT = 10 * density, padB = 18 * density;
            float cw = w - padL - padR, ch = h - padT - padB;
            if (cw <= 0 || ch <= 0) {
                return;
            }

            // ---- Y 轴刻度与虚线网格（0 / 25% / 50% / 75% / 100%）----
            float max = 0;
            for (float v : values) {
                max = Math.max(max, v);
            }
            if (max <= 0) {
                max = 1;
            }
            for (int i = 0; i <= 4; i++) {
                float y = padT + ch * i / 4f;
                canvas.drawLine(padL, y, w - padR, y, gridPaint);
                float val = max * (4 - i) / 4f;
                canvas.drawText(shortNum(val), padL - 4 * density,
                        y + 3 * density, textPaint);
            }

            if (values.length == 0) {
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("暂无数据", w / 2f, padT + ch / 2f, textPaint);
                textPaint.setTextAlign(Paint.Align.RIGHT);
                return;
            }

            // ---- 计算各点坐标 ----
            int n = values.length;
            float step = n > 1 ? cw / (n - 1) : 0;
            float[] xs = new float[n];
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = padL + step * i;
                ys[i] = padT + ch * (1f - values[i] / max);
            }

            // ---- 平滑曲线 ----
            line.reset();
            line.moveTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) {
                float mx = (xs[i - 1] + xs[i]) / 2f;
                line.cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i]);
            }

            // ---- 面积填充（从曲线到底部）----
            area.reset();
            area.addPath(line);
            area.lineTo(xs[n - 1], padT + ch);
            area.lineTo(xs[0], padT + ch);
            area.close();
            fillPaint.setShader(new android.graphics.LinearGradient(
                    0, padT, 0, padT + ch,
                    (accent & 0x00FFFFFF) | 0x55000000,
                    (accent & 0x00FFFFFF) | 0x00000000,
                    android.graphics.Shader.TileMode.CLAMP));
            canvas.drawPath(area, fillPaint);
            fillPaint.setShader(null);

            // ---- 曲线 ----
            canvas.drawPath(line, linePaint);

            // ---- 末端高亮点 ----
            if (n > 0) {
                canvas.drawCircle(xs[n - 1], ys[n - 1], 3.5f * density, dotPaint);
                dotPaint.setColor(surface);
                canvas.drawCircle(xs[n - 1], ys[n - 1], 1.6f * density, dotPaint);
                dotPaint.setColor(accent);
            }

            // ---- X 轴标签：首 / 中 / 尾 ----
            if (labels.length > 0) {
                textPaint.setTextAlign(Paint.Align.CENTER);
                int[] idx = {0, n / 2, n - 1};
                for (int i : idx) {
                    if (i < 0 || i >= labels.length || labels[i] == null) {
                        continue;
                    }
                    float cx = Math.max(20 * density, Math.min(w - 20 * density, xs[i]));
                    canvas.drawText(labels[i], cx, h - 4 * density, textPaint);
                }
                textPaint.setTextAlign(Paint.Align.RIGHT);
            }
        }

        /** Y 轴刻度用的小数字：1234 → 1.2k，1200000 → 1.2M。 */
        private String shortNum(float v) {
            if (v >= 1_000_000) {
                return trim(v / 1_000_000f) + "M";
            }
            if (v >= 1000) {
                return trim(v / 1000f) + "k";
            }
            return String.valueOf(Math.round(v));
        }

        private String trim(float v) {
            if (v >= 100) {
                return String.valueOf(Math.round(v));
            }
            String s = String.format(java.util.Locale.US, "%.1f", v);
            return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
        }
    }

    AreaChart areaChart() {
        AreaChart a = new AreaChart(ctx);
        a.style(c[M3.PRIMARY], c[M3.OUTLINE_VARIANT], c[M3.ON_SURFACE_VARIANT],
                c[M3.SURFACE_CONTAINER_LOW]);
        return a;
    }
}
