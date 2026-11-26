package com.magpie.android;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

/**
 * 壳的「允许局域网访问」与 magpie 自己的 {@code settings.LAN} 对齐。
 *
 * <p>为什么需要这一层：这两个「局域网」本来是两件事，但用户看到的是同一个开关。
 * <ul>
 *   <li>壳这一侧：{@link Prefs#lan} 决定启动子进程时的 {@code MAGPIE_ADDR}
 *       （{@code 0.0.0.0} 还是 {@code 127.0.0.1}），也就是**监听的绑定地址**</li>
 *   <li>magpie 那一侧：{@code settings.json} 的 {@code lan}，由
 *       {@code POST /api/settings/lan} 改，决定**要不要对外服务**、
 *       以及顺手生成那个默认的 {@code Magpie} 网关密钥</li>
 * </ul>
 *
 * <p>以前只写壳这一半：网关确实会绑到 0.0.0.0，但 magpie 网页里的
 * 「局域网共享」还是关的，也不生成密钥 —— 于是要两边各点一次，而且远端设备
 * 没有可用的 key。这个类把壳当成一个开关，写的时候两处一起写。
 */
final class LanSync {

    private static final String TAG = "MagpieLan";

    /** 串行执行器：所有对 magpie 的局域网写请求都在这一条线程里排队，避免快速 on→off 乱序。 */
    private static final java.util.concurrent.ScheduledExecutorService SERIAL =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "magpie-lan-set");
                t.setDaemon(true);
                return t;
            });
    /** 最近一次想要的开关值（去抖：同一时刻多次切换只发最后一次）。 */
    private static volatile boolean latest;
    /** 是否已有一个待发的去抖任务。 */
    private static volatile boolean pending;

    private LanSync() {
    }

    /**
     * 读 magpie 现在的 {@code settings.LAN}。
     *
     * @return true/false；网关没起来或读不到时返回 null（调用方别据此改状态）
     */
    static Boolean current(Context ctx) {
        MagpieApi.Result r = MagpieApi.get(ctx, "/api/settings?last=1",
                MagpieApi.CONNECT_MS, MagpieApi.READ_MS);
        if (!r.ok()) {
            return null;
        }
        JSONObject o = r.json();
        if (o == null) {
            return null;
        }
        // settings.LAN 带 omitempty：关着的时候字段根本不在 JSON 里
        return o.optBoolean("lan", false);
    }

    /**
     * 用户在壳里拨了开关：先落壳这一侧（下次启动生效），
     * 再异步写 magpie 那一侧（立刻生效，并生成默认密钥）。
     */
    static void set(final Context ctx, final boolean on) {
        Prefs.setLan(ctx, on);
        latest = on;
        synchronized (LanSync.class) {
            if (pending) {
                return; // 已有一次待发：同刻多次切换只发最后一次
            }
            pending = true;
        }
        final Context app = ctx.getApplicationContext();
        SERIAL.schedule(() -> {
            synchronized (LanSync.class) {
                pending = false;
            }
            final boolean want = latest;
            try {
                MagpieApi.Result r = MagpieApi.post(app, "/api/settings/lan",
                        "{\"on\":" + want + ",\"newKey\":false}", MagpieApi.WRITE_MS);
                if (!r.ok()) {
                    Log.w(TAG, "同步 magpie 的局域网开关失败: " + r.why());
                } else {
                    Log.i(TAG, "已同步 magpie 局域网共享 = " + want);
                }
            } catch (Exception e) {
                Log.w(TAG, "同步 magpie 局域网开关异常: " + e.getMessage());
            }
        }, 150, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 网关就绪后调一次：把壳里的值推给 magpie（幂等，值一样就不发请求）。
     *
     * <p>为什么还要这一步：开关有可能是在网关没起来时拨的（比如开机前就设过），
     * 那时 POST 会失败；等网关起来了补一次，两边就一致了。
     */
    static void pushIfNeeded(final Context ctx) {
        final boolean want = Prefs.lan(ctx);
        final Context app = ctx.getApplicationContext();
        // 走同一条串行执行器：与用户拨开关的写请求排队，避免相互覆盖/乱序。
        SERIAL.execute(() -> {
            Boolean now = current(app);
            if (now == null || now == want) {
                return;
            }
            MagpieApi.Result r = MagpieApi.post(app, "/api/settings/lan",
                    "{\"on\":" + want + ",\"newKey\":false}", MagpieApi.WRITE_MS);
            Log.i(TAG, "网关就绪后对齐局域网开关 " + now + " → " + want
                    + (r.ok() ? " 成功" : " 失败：" + r.why()));
        });
    }
}
