package com.magpie.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * 悬浮球 + 隐形保活小窗口。
 *
 * <p><b>为什么需要</b>：实测多个用户在 MIUI/澎湃上设了「无限制」仍会在息屏后
 * 被系统掐掉网关（AI 请求中断）。加一个覆盖在其它应用之上的悬浮窗后就不会了——
 * 因为系统对「有可见悬浮窗的应用」的冻结判定更宽松（WindowsManager 图层
 * 参与可见性计算，uid 不算后台）。这是绕开国产 ROM 激进的冻结策略的常见手段。
 *
 * <p><b>两个窗口</b>：
 * <ul>
 *   <li><b>悬浮球</b>：可拖动的圆形按钮，点击打开 Magpie，大小/图标可配，
 *       图标能选相册图片、也能设成透明（只剩一个透明热区）。</li>
 *   <li><b>保活窗口</b>：1dp×1dp 近乎不可见的小窗（大小可调），只为让
 *       系统认为「这个应用在屏幕上有东西」。参考同类做法。</li>
 * </ul>
 *
 * <p><b>权限</b>：需要 SYSTEM_ALERT_WINDOW（用户手动授予「显示在其他应用上层」）。
 *
 * <p><b>性能</b>：两个窗口都用 FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCH_MODAL，
 * 不拦截正常触摸（悬浮球自己除外），GPU 合成开销可忽略。
 */
final class FloatingBall {

    private static final String TAG = "MagpieBall";

    private static WindowManager wm;
    private static View ballView;
    private static View keepAliveView;
    private static WindowManager.LayoutParams ballLp;
    private static WindowManager.LayoutParams keepLp;

    private FloatingBall() {
    }

    /** 有没有悬浮窗权限。 */
    static boolean canDraw(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return Settings.canDrawOverlays(ctx);
    }

    /** 跳系统设置让用户授权。 */
    static void requestPermission(Activity act) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + act.getPackageName()));
            act.startActivity(i);
        } catch (Exception e) {
            ToastCompat.show(act, "打不开授权页，请手动到设置里允许「显示在其他应用上层」");
        }
    }

    /** 当前是否在显示。 */
    static boolean isShowing() {
        return ballView != null;
    }

    /**
     * 显示悬浮球（+ 保活窗口）。
     *
     * @return null 成功，否则错误说明
     */
    static String show(Context ctx) {
        if (ballView != null) {
            return null;
        }
        if (!canDraw(ctx)) {
            return "没有悬浮窗权限";
        }
        try {
            wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return "拿不到 WindowManager";
            }

            // ---- 保活小窗口：1dp（可调），完全透明、不拦截触摸 ----
            int keepDp = Prefs.ballKeepAliveDp(ctx);
            keepAliveView = new View(ctx);
            keepAliveView.setBackgroundColor(Color.TRANSPARENT);
            keepLp = new WindowManager.LayoutParams(
                    Math.max(1, dp(ctx, keepDp)), Math.max(1, dp(ctx, keepDp)),
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            keepLp.gravity = Gravity.TOP | Gravity.START;
            keepLp.x = 0;
            keepLp.y = 0;
            wm.addView(keepAliveView, keepLp);

            // ---- 悬浮球 ----
            int size = Prefs.ballSizeDp(ctx);
            ballView = buildBall(ctx, size);
            ballLp = new WindowManager.LayoutParams(
                    dp(ctx, size), dp(ctx, size),
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            ballLp.gravity = Gravity.TOP | Gravity.START;
            ballLp.x = Prefs.ballX(ctx);
            ballLp.y = Prefs.ballY(ctx);
            wm.addView(ballView, ballLp);

            Log.i(TAG, "悬浮球已显示 size=" + size + "dp keep=" + keepDp + "dp");
            return null;
        } catch (Exception e) {
            Log.w(TAG, "show failed: " + e);
            hide();
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    static void hide() {
        try {
            if (wm != null) {
                if (ballView != null) {
                    wm.removeView(ballView);
                }
                if (keepAliveView != null) {
                    wm.removeView(keepAliveView);
                }
            }
        } catch (Exception ignored) {
        }
        ballView = null;
        keepAliveView = null;
        ballLp = null;
        keepLp = null;
    }

    /** 悬浮球视图：圆形底 + 图标（图片/自身 logo/透明）。可拖动，点击开 App。 */
    private static View buildBall(final Context ctx, int sizeDp) {
        ImageView iv = new ImageView(ctx);
        int mode = Prefs.ballIconMode(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (mode == Prefs.BALL_ICON_TRANSPARENT) {
            // 完全透明：只剩热区，视觉上不可见
            bg.setColor(Color.TRANSPARENT);
        } else {
            bg.setColor(ctx.getResources().getColor(android.R.color.white));
            bg.setAlpha(230);
        }
        iv.setBackground(bg);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });

        if (mode == Prefs.BALL_ICON_CUSTOM) {
            String path = Prefs.ballIconPath(ctx);
            try {
                iv.setImageURI(Uri.parse(path));
            } catch (Exception e) {
                iv.setImageResource(R.drawable.ic_magpie_bird);
            }
        } else if (mode == Prefs.BALL_ICON_TRANSPARENT) {
            iv.setImageDrawable(null);
        } else {
            iv.setImageResource(R.drawable.ic_magpie_bird);
        }

        // 拖动 + 点击
        iv.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = ballLp.x;
                        startY = ballLp.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dx = (int) (e.getRawX() - touchX);
                        int dy = (int) (e.getRawY() - touchY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            moved = true;
                        }
                        ballLp.x = startX + dx;
                        ballLp.y = startY + dy;
                        try {
                            wm.updateViewLayout(ballView, ballLp);
                        } catch (Exception ignored) {
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            Prefs.setBallPos(ctx, ballLp.x, ballLp.y);
                        } else {
                            // 点击：打开 App
                            try {
                                Intent i = new Intent(ctx, NativeHome.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                ctx.startActivity(i);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                }
                return false;
            }
        });
        return iv;
    }

    /** 参数改了以后刷新（大小/图标）。 */
    static void refresh(Context ctx) {
        if (!isShowing()) {
            return;
        }
        hide();
        show(ctx);
    }

    private static int overlayType() {
        // API 26+ 用 TYPE_APPLICATION_OVERLAY，以下用 TYPE_PHONE（都有权限）
        if (Build.VERSION.SDK_INT >= 26) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    /** Toast 包一层，避免到处写。 */
    private static final class ToastCompat {
        static void show(Context ctx, String s) {
            android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
