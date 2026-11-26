package com.magpie.android;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.view.View;

/**
 * 主题切换的过渡，专治「切主题会卡一下」。
 *
 * <p>为什么卡：切主题是 {@code act.recreate()} 整页重建，概览页几百个 View 要
 * 重新搭一遍（主线程几百毫秒的重活），重建那一瞬间窗口还会露出默认底色
 * （深色主题尤其闪一下白）。用户看到的就是「卡一下 + 闪一下」。
 *
 * <p>这里不改「重建」这个事实（几百个 View 省不掉），而是把这段重活藏起来：
 * <ol>
 *   <li>先把当前内容淡出（约 120ms）。淡出期间露出的是「新主题」的页面底色——
 *       窗口底色已提前铺成新主题 surface，所以不白闪、不露系统黑底，
 *       整块屏幕就是很自然地「变成本主题的底色」。</li>
 *   <li>内容透明后再 {@code recreate()} 重建。</li>
 *   <li>新 Activity 的 onCreate 末尾调 {@link #fadeInIfPending}，把新内容
 *       从 0 淡入（约 200ms）。</li>
 * </ol>
 * 于是重建这段 CPU 卡顿被「底色 + 淡入」盖住，观感是平滑过渡，而不是白屏卡顿。
 *
 * <p>用静态标志跨 Activity 重建传递「这次要淡入」——同进程 recreate 安全。
 */
final class ThemeSwitch {

    private ThemeSwitch() {
    }

    /** 本次 recreate 是否来自主题切换（是则新界面淡入）。 */
    private static volatile boolean pendingFadeIn = false;

    /** 把窗口底色铺成当前（新）主题的页面底色，避免重建瞬间闪白/闪黑。 */
    static void paintWindow(Activity act) {
        try {
            int bg = M3.current(act)[M3.SURFACE];
            act.getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        } catch (Exception ignored) {
        }
    }

    /**
     * 切换主题专用：淡出当前内容 → recreate。
     * 调用前必须已经 {@code M3.setThemeIndex(...)} 生效，这样铺的是新主题底色。
     */
    static void recreate(final Activity act) {
        pendingFadeIn = true;
        paintWindow(act);
        View content = act.findViewById(android.R.id.content);
        if (content == null) {
            act.recreate();
            return;
        }
        content.animate().alpha(0f).setDuration(120).withEndAction(new Runnable() {
            @Override
            public void run() {
                act.recreate();
            }
        }).start();
    }

    /** onCreate 的 setContentView 之后调用：若这次重建来自主题切换，做一次淡入。 */
    static void fadeInIfPending(Activity act) {
        if (!pendingFadeIn) {
            return;
        }
        pendingFadeIn = false;
        View content = act.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(200).start();
    }
}
