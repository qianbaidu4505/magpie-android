package com.magpie.android;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/**
 * Material 3 色彩令牌。
 *
 * <p>八套固定色板由 Material Color Utilities 的 {@code SchemeTonalSpot}
 * (SPEC 2025, contrast 0) 生成：主色沿用种子色的色相，饱和度中等；
 * 次级、三级各自降饱和 / 偏 60° 色相，中性色只留极低的彩度。
 * 生成脚本不进 App，运行时只查表 —— 手机上没有色彩空间换算的负担。
 */
final class M3 {

    static final int PRIMARY = 0;
    static final int ON_PRIMARY = 1;
    static final int PRIMARY_CONTAINER = 2;
    static final int ON_PRIMARY_CONTAINER = 3;
    static final int SECONDARY = 4;
    static final int ON_SECONDARY = 5;
    static final int SECONDARY_CONTAINER = 6;
    static final int ON_SECONDARY_CONTAINER = 7;
    static final int TERTIARY = 8;
    static final int ON_TERTIARY = 9;
    static final int TERTIARY_CONTAINER = 10;
    static final int ON_TERTIARY_CONTAINER = 11;
    static final int ERROR = 12;
    static final int ON_ERROR = 13;
    static final int ERROR_CONTAINER = 14;
    static final int ON_ERROR_CONTAINER = 15;
    static final int SURFACE = 16;
    static final int ON_SURFACE = 17;
    static final int SURFACE_VARIANT = 18;
    static final int ON_SURFACE_VARIANT = 19;
    static final int OUTLINE = 20;
    static final int OUTLINE_VARIANT = 21;
    static final int SURFACE_DIM = 22;
    static final int SURFACE_BRIGHT = 23;
    static final int SURFACE_CONTAINER_LOWEST = 24;
    static final int SURFACE_CONTAINER_LOW = 25;
    static final int SURFACE_CONTAINER = 26;
    static final int SURFACE_CONTAINER_HIGH = 27;
    static final int SURFACE_CONTAINER_HIGHEST = 28;
    static final int INVERSE_SURFACE = 29;
    static final int INVERSE_ON_SURFACE = 30;
    static final int INVERSE_PRIMARY = 31;
    static final int SCRIM = 32;
    /** 余量预警黄（<20%）；余量充足用 TERTIARY 绿，耗尽用 ERROR 红。 */
    static final int QUOTA_WARN = 33;
    static final int COUNT = 34;

    static final String[] ROLE_NAMES = {"primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer", "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer", "error", "onError", "errorContainer", "onErrorContainer", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant", "outline", "outlineVariant", "surfaceDim", "surfaceBright", "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest", "inverseSurface", "inverseOnSurface", "inversePrimary", "scrim", "quotaWarn"};

    /** 种子色：设为「跟随系统」以外的风格。最后一个是设计稿配色。 */
    static final String[] SEED_NAMES = {"经典", "紧凑"};
    static final int[] SEEDS = {0xFF3498DB, 0xFF5DADE2};

    static final int[][] LIGHT = {
        // 青绿·设计稿（浅色·柔化）
        {0xFF3498DB, 0xFFFFFFFF, 0xFFD6EAF8, 0xFF1B4F72, 0xFF5D6D7E, 0xFFFFFFFF, 0xFFD5DBDB, 0xFF2E3B47, 0xFF27AE60, 0xFFFFFFFF, 0xFFD5F5E3, 0xFF1E8449, 0xFFE74C3C, 0xFFFFFFFF, 0xFFFDEDEC, 0xFF943126, 0xFFF4F5F7, 0xFF222222, 0xFFDDE1E7, 0xFF444444, 0xFFAEB6BF, 0xFFDDE1E7, 0xFFE1E4E8, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFF7F8FA, 0xFFEEF0F3, 0xFFE7EAEE, 0xFF222222, 0xFFF4F5F7, 0xFF85C1E9, 0xFF000000, 0xFFF39C12},
        // 紧凑（浅色）: 描边更淡、底色更冷
        {0xFF3498DB, 0xFFFFFFFF, 0xFFD6EAF8, 0xFF1B4F72, 0xFF5D6D7E, 0xFFFFFFFF,
                0xFFE4E8ED, 0xFF2E3B47, 0xFF27AE60, 0xFFFFFFFF, 0xFFD5F5E3, 0xFF1E8449,
                0xFFE74C3C, 0xFFFFFFFF, 0xFFFDEDEC, 0xFF943126, 0xFFF7F8FA, 0xFF1C1E21,
                0xFFE4E8ED, 0xFF4A4F57, 0xFFB3BAC2, 0xFFDCE0E5, 0xFFEDEFF2, 0xFFFFFFFF,
                0xFFFFFFFF, 0xFFFFFFFF, 0xFFF9FAFB, 0xFFF1F3F5, 0xFFEAEDF0, 0xFF1C1E21,
                0xFFF7F8FA, 0xFF85C1E9, 0xFF000000, 0xFFF39C12},
        // 终端（浅色）: 纸白底 + 墨黑字，边线极细
        {0xFF1F6F4A, 0xFFFFFFFF, 0xFFDCF0E4, 0xFF0B3B26, 0xFF55606B, 0xFFFFFFFF,
                0xFFE6EBEF, 0xFF22303A, 0xFF1F8A54, 0xFFFFFFFF, 0xFFDCF0E4, 0xFF0B3B26,
                0xFFC0392B, 0xFFFFFFFF, 0xFFF8E2DF, 0xFF7A1D14, 0xFFFCFCFC, 0xFF14181C,
                0xFFE6EBEF, 0xFF3E4750, 0xFFB8C0C8, 0xFFDFE4E9, 0xFFF2F4F6, 0xFFFFFFFF,
                0xFFFFFFFF, 0xFFFFFFFF, 0xFFFCFCFC, 0xFFF3F5F7, 0xFFEBEEF1, 0xFF14181C,
                0xFFFCFCFC, 0xFF8FC7A8, 0xFF000000, 0xFFC98A18},
        // 墨·Paper（浅色）: 暖灰绿底 + 白卡，无描边，深绿强调（找正规会话 spec.md §2，WCAG 修正版）
        {0xFF0B7A5A, 0xFFFFFFFF, 0xFFE7F2EE, 0xFF0B3B2C, 0xFF5F6A65, 0xFFFFFFFF,
                0xFFEDF1EE, 0xFF0E1211, 0xFF0B7A5A, 0xFFFFFFFF, 0xFFE7F2EE, 0xFF0B3B2C,
                0xFFC23B2E, 0xFFFFFFFF, 0xFFF8E2DF, 0xFF7A1D14, 0xFFF4F6F4, 0xFF0E1211,
                0xFFEDF1EE, 0xFF5F6A65, 0xFF8A948F, 0xFFE3E8E4, 0xFFE2E6E3, 0xFFFFFFFF,
                0xFFFFFFFF, 0xFFFFFFFF, 0xFFF7F9F7, 0xFFEDF1EE, 0xFFE3E8E4, 0xFF0E1211,
                0xFFF4F6F4, 0xFF0B7A5A, 0xFF000000, 0xFF96600F},
    };

    static final int[][] DARK = {
        // 青绿·设计稿（深色）
        {0xFF5DADE2, 0xFF0B141B, 0xFF1B3A52, 0xFFD6EAF8, 0xFF85929E, 0xFF0B141B, 0xFF2C343C, 0xFFD5DBDB, 0xFF2ECC71, 0xFF0B141B, 0xFF1D4D34, 0xFFD5F5E3, 0xFFE74C3C, 0xFF0B141B, 0xFF4A2327, 0xFFFDEDEC, 0xFF17191C, 0xFFEAECEE, 0xFF2A2E33, 0xFFA6AFB8, 0xFF5D6670, 0xFF3A3F45, 0xFF101214, 0xFF22262B, 0xFF101214, 0xFF1B1E22, 0xFF202329, 0xFF262A30, 0xFF2C3138, 0xFFEAECEE, 0xFF17191C, 0xFF5DADE2, 0xFF000000, 0xFFF5B041},
        // 紧凑（深色）: 页面 #0D1014 / 卡片 #1A1F26 / 卡内块 #20262E，三档拉开
        {0xFF5DADE2, 0xFF0A1420, 0xFF1B3A52, 0xFFD6EAF8, 0xFF85929E, 0xFF0A1420,
                0xFF262D36, 0xFFD5DBDB, 0xFF3FB950, 0xFF07130A, 0xFF1D4D34, 0xFFD5F5E3,
                0xFFF85149, 0xFF1A0708, 0xFF4A2327, 0xFFFDEDEC, 0xFF0D1014, 0xFFE8EAED,
                0xFF262D36, 0xFF9AA4AF, 0xFF525B66, 0xFF2A3037, 0xFF08090C, 0xFF1E242B,
                0xFF0A0C10, 0xFF1A1F26, 0xFF20262E, 0xFF262D36, 0xFF2C343D, 0xFFE8EAED,
                0xFF0D1014, 0xFF5DADE2, 0xFF000000, 0xFFD29922},
        // 终端（深色）: 纯黑 #000 底 / #0C0F12 块 / 4dp 硬角 / 单色青绿
        {0xFF4ECB8C, 0xFF000000, 0xFF10331F, 0xFFCFF0DD, 0xFF7A858F, 0xFF000000,
                0xFF16191D, 0xFFC2CBD3, 0xFF4ECB8C, 0xFF000000, 0xFF10331F, 0xFFCFF0DD,
                0xFFEF5F5F, 0xFF000000, 0xFF3A1616, 0xFFF7DADA, 0xFF000000, 0xFFD8DEE4,
                0xFF16191D, 0xFF8A949E, 0xFF3A424A, 0xFF1C2126, 0xFF000000, 0xFF0C0F12,
                0xFF000000, 0xFF0C0F12, 0xFF11151A, 0xFF161A1F, 0xFF1B2025, 0xFFD8DEE4,
                0xFF000000, 0xFF4ECB8C, 0xFF000000, 0xFFB07D14},
        // 墨·Ink（深色）: 深墨 #0A0C0D 底 / #121618 卡 / 单一信号色 #35E1A3，数据优先（spec.md §2，a11y 修正版）
        {0xFF35E1A3, 0xFF0A0C0D, 0xFF17322B, 0xFFECF2EF, 0xFF98A29D, 0xFF0A0C0D,
                0xFF1A1F22, 0xFFECF2EF, 0xFF35E1A3, 0xFF0A0C0D, 0xFF17322B, 0xFFECF2EF,
                0xFFFF6E60, 0xFF0A0C0D, 0xFF3A1A16, 0xFFF7DADA, 0xFF0A0C0D, 0xFFECF2EF,
                0xFF1A1F22, 0xFF98A29D, 0xFF3A424A, 0xFF232A2D, 0xFF07090A, 0xFF121618,
                0xFF0E1113, 0xFF121618, 0xFF161B1F, 0xFF1A1F22, 0xFF20262B, 0xFFECF2EF,
                0xFF0A0C0D, 0xFF127F58, 0xFF000000, 0xFFF5B85C},
    };


    /**
     * 一套界面风格：令牌表之外的手感参数。
     *
     * <p>为什么和颜色分开：颜色是「查表」，风格是「几何」。用户想要的不只是
     * 换个色，还要圆角、阴影、字号、有没有分割线一起跟着变，所以两样装在
     * 一个 Theme 里，切的时候一次全换。
     */
    static final class Style {
        /** 卡片圆角（dp）。 */
        final float cardRadius;
        /** 卡内小块的圆角（dp）。 */
        final float innerRadius;
        /** 描边宽度（dp）。 */
        final float borderWidth;
        /** true=主色低透明描边（发光感），false=中性发丝线。 */
        final boolean accentBorder;
        /** 卡片悬浮高度（dp），0 = 不悬浮。 */
        final int elevationDp;
        /** 大数字字号（sp）。 */
        final float bigNum;
        /** true=数字用等宽字体（tnum），跳数字时不抖。 */
        final boolean monoNum;
        /** true=四宫格并成一整张卡，false=四张独立卡。 */
        final boolean mergedGrid;

        Style(float cardRadius, float innerRadius, float borderWidth, boolean accentBorder,
              int elevationDp, float bigNum, boolean monoNum, boolean mergedGrid) {
            this.cardRadius = cardRadius;
            this.innerRadius = innerRadius;
            this.borderWidth = borderWidth;
            this.accentBorder = accentBorder;
            this.elevationDp = elevationDp;
            this.bigNum = bigNum;
            this.monoNum = monoNum;
            this.mergedGrid = mergedGrid;
        }
    }

    /** 一套完整主题：深浅两套令牌 + 一份风格参数。 */
    static final class Theme {
        final String key;
        final String name;
        final String desc;
        final int[] light;
        final int[] dark;
        final Style style;
        /** true=固定暗色：无视「明暗」设置，永远用 dark 色板（终端、墨这类暗色系设计）。 */
        final boolean fixedDark;

        Theme(String key, String name, String desc, int[] light, int[] dark, Style style,
              boolean fixedDark) {
            this.key = key;
            this.name = name;
            this.desc = desc;
            this.light = light;
            this.dark = dark;
            this.style = style;
            this.fixedDark = fixedDark;
        }
    }

    /** 玻璃：四张独立卡、主色发光描边、大圆角、柔和阴影。 */
    static final Style STYLE_GLASS = new Style(24f, 18f, 1.5f, true, 8, 30f, false, false);
    /** 原版：一整张大卡 + 分割线，就是最早那版的样子。 */
    static final Style STYLE_ORIGINAL = new Style(20f, 14f, 1f, false, 0, 28f, true, true);
    /** 终端：纯黑、4dp 硬角、等宽、无任何装饰。 */
    static final Style STYLE_TERMINAL = new Style(4f, 3f, 1f, false, 0, 26f, true, true);
    /**
     * 墨：深墨底 + 单一信号色，数据优先（「找正规」会话的 Ink &amp; Signal 方案）。
     * 卡片 16dp 圆角、无阴影；描边压到最暗的发丝线（#232A2D 压 #121618，
     * 对比度 1.25，视觉上等于无描边，但保留 1dp 让主题选择行的选中态有边）。
     * 数字全部等宽 + tnum。
     */
    static final Style STYLE_INK = new Style(16f, 12f, 1f, false, 0, 27f, true, false);

    /**
     * 可选主题。下标存在 Prefs.THEME_STYLE 里，切换后整页重建。
     *
     * <p>第一位即默认：玻璃 = 改动前的原样，老用户升级不会被换皮。
     * 「原版」是更早那版一整张大卡的布局，摆在第二位方便对比。
     */
    static final Theme[] THEMES = {
            new Theme("glass", "玻璃", "四张独立卡 · 主色发光描边 · 大圆角 · 柔和阴影",
                    LIGHT[0], DARK[0], STYLE_GLASS, false),
            new Theme("original", "原版", "一整张大卡 · 分割线分组 · 等宽数字 · 无阴影",
                    LIGHT[1], DARK[1], STYLE_ORIGINAL, false),
            new Theme("terminal", "终端", "纯黑底 · 4dp 硬角 · 单色青绿 · 固定暗色",
                    LIGHT[2], DARK[2], STYLE_TERMINAL, true),
            new Theme("ink", "墨", "深墨底 · 单一信号色 · 等宽数字 · 无描边 · 固定暗色",
                    LIGHT[3], DARK[3], STYLE_INK, true),
    };

    /**
     * 默认主题：玻璃（也就是改动之前的样子）。
     *
     * <p>老用户升级上来不该被换皮，所以 THEMES 里虽然把「原版」排在第一位
     * （方便一眼看到最朴素的那个），默认值仍然指回玻璃。
     */
    static final int DEFAULT_THEME = 1;

    /** 当前主题下标，越界退回默认那套。 */
    static int themeIndex(Context ctx) {
        int i = Prefs.themeStyle(ctx);
        return i < 0 || i >= THEMES.length ? DEFAULT_THEME : i;
    }

    static void setThemeIndex(Context ctx, int i) {
        Prefs.setThemeStyle(ctx, i < 0 || i >= THEMES.length ? DEFAULT_THEME : i);
    }

    static Theme theme(Context ctx) {
        return THEMES[themeIndex(ctx)];
    }

    static Style style(Context ctx) {
        return theme(ctx).style;
    }

    private M3() {
    }

    /**
     * 当前主题是不是「带主色描边」那一类。
     *
     * <p>以前这是个写死 {@code true} 的开关，页面里 31 处 {@code ui.design}
     * 靠它分叉（四张独立卡 vs 一整张卡）。现在它跟随主题走：只有「玻璃」
     * 是 true，原版和终端都是 false —— 所以那 31 处一行都不用改，
     * 切主题就自动换布局。
     */
    static boolean design(Context ctx) {
        return style(ctx).accentBorder;
    }

    /**
     * 当前是否深色：优先看用户设的主题模式。
     * 0=跟随系统，1=深色，2=浅色。
     */
    static boolean dark(Context ctx) {
        int mode = Prefs.themeMode(ctx);
        if (mode == 1) {
            return true;
        }
        if (mode == 2) {
            return false;
        }
        return (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 当前生效的令牌表：按主题取那一套，再按深/浅色分。固定暗色主题无视明暗设置。 */
    static int[] current(Context ctx) {
        Theme t = theme(ctx);
        return (t.fixedDark || dark(ctx)) ? t.dark : t.light;
    }

    /** 种子色圆点的显示色：深色模式略微提亮，否则原色。 */
    static int seedColor(Context ctx, int index) {
        int base = SEEDS[Math.max(0, Math.min(SEEDS.length - 1, index))];
        if (!dark(ctx)) {
            return base;
        }
        return Color.rgb(
                Math.round(Color.red(base) + (255 - Color.red(base)) * 0.18f),
                Math.round(Color.green(base) + (255 - Color.green(base)) * 0.18f),
                Math.round(Color.blue(base) + (255 - Color.blue(base)) * 0.18f));
    }

    /** 选中的种子色圆点上，勾的对比色。 */
    static int onSeed(Context ctx, int index) {
        int color = seedColor(ctx, index);
        double lum = (0.299 * Color.red(color) + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        return lum > 0.55 ? 0xFF1B1B1B : 0xFFFFFFFF;
    }

    /** 按主题下标取令牌表；浅色/深色各一份，越界退回第 0 套。固定暗色主题恒取 dark。 */
    static int[] of(Context ctx, int index) {
        int i = Math.max(0, Math.min(THEMES.length - 1, index));
        Theme t = THEMES[i];
        return (t.fixedDark || dark(ctx)) ? t.dark : t.light;
    }

    /**
     * 莫奈取色：Android 12 起系统自己就按 Material You（TonalSpot）出了
     * accent1/2/3 与 neutral1/2 五条色阶，直接按色调取就等价于一套
     * 系统算好的方案，不用在手机上重算 HCT。
     */
    static int[] fromSystem(Context ctx) {
        boolean dk = dark(ctx);
        int[] out = new int[COUNT];
        int[] fill = {0};
        String[] a1 = {"system_accent1_%d", "system_accent2_%d", "system_accent3_%d"};
        // primary / secondary / tertiary 三组
        int[][] groups = {{PRIMARY, ON_PRIMARY, PRIMARY_CONTAINER, ON_PRIMARY_CONTAINER},
                {SECONDARY, ON_SECONDARY, SECONDARY_CONTAINER, ON_SECONDARY_CONTAINER},
                {TERTIARY, ON_TERTIARY, TERTIARY_CONTAINER, ON_TERTIARY_CONTAINER}};
        int[] tones = dk ? new int[]{80, 20, 30, 90} : new int[]{40, 100, 90, 10};
        for (int g = 0; g < 3; g++) {
            for (int k = 0; k < 4; k++) {
                int c = sys(ctx, a1[g].replace("%d", String.valueOf(tones[k])));
                if (c == 0) {
                    return null; // 系统没给这套色阶（Android 11 及以下）
                }
                out[groups[g][k]] = c;
            }
        }
        int[] tonesSurface = dk ? new int[]{6, 90, 80, 60, 30, 12, 17, 22, 24, 4, 10}
                : new int[]{99, 10, 30, 50, 80, 96, 94, 92, 90, 100, 87};
        int[] roles = {SURFACE, ON_SURFACE, ON_SURFACE_VARIANT, OUTLINE, OUTLINE_VARIANT,
                SURFACE_CONTAINER_LOW, SURFACE_CONTAINER, SURFACE_CONTAINER_HIGH,
                SURFACE_CONTAINER_HIGHEST, SURFACE_CONTAINER_LOWEST, SURFACE_DIM};
        for (int k = 0; k < roles.length; k++) {
            int c = sys(ctx, (k == 2 || k == 3 || k == 4 ? "system_neutral2_%d" : "system_neutral1_%d")
                    .replace("%d", String.valueOf(tonesSurface[k])));
            if (c == 0) {
                return null;
            }
            out[roles[k]] = c;
        }
        out[SURFACE_VARIANT] = out[SURFACE_CONTAINER_HIGH];
        out[SURFACE_BRIGHT] = sys(ctx, (dk ? "system_neutral1_24" : "system_neutral1_98"));
        out[INVERSE_SURFACE] = out[ON_SURFACE];
        out[INVERSE_ON_SURFACE] = out[SURFACE];
        out[INVERSE_PRIMARY] = sys(ctx, (dk ? "system_accent1_40" : "system_accent1_80"));
        out[ERROR] = sys(ctx, (dk ? "system_error_80" : "system_error_40"));
        out[ON_ERROR] = sys(ctx, (dk ? "system_error_20" : "system_error_100"));
        out[ERROR_CONTAINER] = sys(ctx, (dk ? "system_error_30" : "system_error_90"));
        out[ON_ERROR_CONTAINER] = sys(ctx, (dk ? "system_error_90" : "system_error_10"));
        out[SCRIM] = 0xFF000000;
        out[QUOTA_WARN] = dk ? 0xFFF5B041 : 0xFFF39C12;
        fill[0]++;
        return out;
    }

    private static int sys(Context ctx, String name) {
        int id = ctx.getResources().getIdentifier(name, "color", "android");
        if (id == 0) {
            return 0;
        }
        try {
            return ctx.getResources().getColor(id);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 文字压在某个底色上时，挑对比度更高的那个前景令牌。 */
    static int on(int color) {
        double lum = (0.299 * android.graphics.Color.red(color)
                + 0.587 * android.graphics.Color.green(color)
                + 0.114 * android.graphics.Color.blue(color)) / 255.0;
        return lum < 0.5 ? 0xFFFFFFFF : 0xFF000000;
    }
}
