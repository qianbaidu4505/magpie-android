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
    static final int COUNT = 33;

    static final String[] ROLE_NAMES = {"primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer", "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer", "error", "onError", "errorContainer", "onErrorContainer", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant", "outline", "outlineVariant", "surfaceDim", "surfaceBright", "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest", "inverseSurface", "inverseOnSurface", "inversePrimary", "scrim"};

    /** 种子色：设为「跟随系统」以外的风格。最后一个是设计稿配色。 */
    static final String[] SEED_NAMES = {"青绿·设计稿"};
    static final int[] SEEDS = {0xFF2DD4BF};

    static final int[][] LIGHT = {
        // 青绿·设计稿（浅色·柔化）
        {0xFF0E938A, 0xFF003731, 0xFFB8FFF4, 0xFF003731, 0xFF4A5D62, 0xFFF0F4F6, 0xFFD6DEE2, 0xFF3A4A4F, 0xFF3A6478, 0xFFF0F4F6, 0xFFB7E3FF, 0xFF26536A, 0xFFA83836, 0xFFF0F4F6, 0xFFFA746F, 0xFF6E0A12, 0xFFE6ECEF, 0xFF1B262B, 0xFFD2DBDF, 0xFF5A6B72, 0xFF8B9BA2, 0xFFD6DEE2, 0xFFDCE3E6, 0xFFF0F4F6, 0xFFE6ECEF, 0xFFE6ECEF, 0xFFE6ECEF, 0xFFEBF0F2, 0xFFDCE3E6, 0xFF1B262B, 0xFFE6ECEF, 0xFF7CD5CB, 0xFF000000},
    };

    static final int[][] DARK = {
        // 青绿·设计稿（深色）
        {0xFF2EE6D0, 0xFF003731, 0xFF00504A, 0xFFB8FFF4, 0xFF9DB4B8, 0xFF123544, 0xFF254C5C, 0xFFC4E4F2, 0xFFA5C9D8, 0xFF123544, 0xFF254C5C, 0xFFC4E4F2, 0xFFFA746F, 0xFF490006, 0xFF871F21, 0xFFFF9993, 0xFF0D1F24, 0xFFFFFFFF, 0xFF122830, 0xFF9DB4B8, 0xFF64748B, 0x662DD4BF, 0xFF0B1A1E, 0xFF1E363C, 0xFF0B1A1E, 0xFF122830, 0xFF162E36, 0xFF1A343C, 0xFF1E3A42, 0xFFFFFFFF, 0xFF0D1F24, 0xFF14B8A6, 0xFF000000},
    };

    private M3() {
    }

    /** 是不是设计稿主题：现在只留这一套，永远返回 true。 */
    static boolean design(Context ctx) {
        return true;
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

    /** 当前生效的令牌表：只剩设计稿一套，按深/浅色取。 */
    static int[] current(Context ctx) {
        return of(ctx, 0);
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

    /** 8 套固定色板里选中的那一套；浅色/深色各一份。 */
    static int[] of(Context ctx, int index) {
        int i = Math.max(0, Math.min(SEEDS.length - 1, index));
        return dark(ctx) ? DARK[i] : LIGHT[i];
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
