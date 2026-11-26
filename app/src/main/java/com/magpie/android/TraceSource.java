package com.magpie.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 实时路由面板的数据源：轮询 {@code /api/gateway/trace}。
 *
 * <p>这个接口本身就是长轮询：带上 {@code after=<seq>} 和 {@code wait=} 之后，
 * 网关会一直挂住请求，直到有新的路由事件才回。所以这里不需要高频轮询，
 * 一次请求最多挂 25 秒，回来了立刻再接一次。
 *
 * <p>返回的 `Route` 里我们要的是：
 * <ul>
 *   <li>{@code order[]} —— 这次请求的候选账号，按优先级排好（就是网页上那排）</li>
 *   <li>{@code tries[]} —— 已经尝试过的，带 status / 耗时</li>
 *   <li>{@code group} —— 走的是哪个路由组，名字 + 路由方式 + 成员</li>
 *   <li>{@code done}/{@code status} —— 请求结束了没有</li>
 *   <li>{@code totals} —— 请求数 / 改道数 / 错误数，顶部那三个数字</li>
 * </ul>
 *
 * <p><b>并发安全</b>：stop 时旧 worker 可能还阻塞在 socket 读里（interrupt 不一定让它
 * 立刻返回），紧接着 start 又会起新 worker。这里用「代际令牌 gen」区分：每次
 * start/stop 都 +1，旧线程醒来发现代际不符就退出，绝不重复投递、也不会把 seq 写回
 * 覆盖新代际。seq 用 AtomicLong，且只在代际一致时更新。
 */
final class TraceSource {


    private volatile boolean running;
    /** 长轮询读取起点；只在代际一致时更新。 */
    private final AtomicLong seq = new AtomicLong(0);
    /** 代际令牌：每次 start/stop 自增，旧线程据此判断自己是否过期。 */
    private volatile int gen;
    private Thread worker;

    interface Sink {
        /** 一批路由到了（可能为空），外加累计数字。 */
        void onTrace(JSONArray routes, JSONObject totals);

        /** 读不到（网关没起来、密钥不对…）。 */
        void onError(String why);
    }

    void start(NativeHome host, Sink sink) {
        if (running) {
            return;
        }
        running = true;
        final int myGen = ++gen;
        // seq 归零放 start：切回路由页从「当前」重新读，不带上一次的值。
        seq.set(0);
        Thread t = new Thread(() -> loop(host, sink, myGen), "magpie-trace");
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    /**
     * 停下。用代际令牌让正在阻塞的旧线程失效；不再在这里改 seq（归零统一放 start）。
     */
    void stop() {
        running = false;
        gen++;
        Thread w = worker;
        worker = null;
        if (w != null) {
            w.interrupt();
        }
    }

    private void loop(NativeHome host, Sink sink, int myGen) {
        while (running && gen == myGen) {
            String path = "/api/gateway/trace?after=" + seq.get() + "&wait=1";
            MagpieApi.Result r = MagpieApi.get(host, path, MagpieApi.CONNECT_MS, MagpieApi.READ_POLL_MS);
            if (!running || gen != myGen) {
                return;
            }
            if (!r.ok()) {
                sink.onError(r.why());
                // 失败别打转，歇一会儿再来
                sleep(3000);
                continue;
            }
            JSONObject o = r.json();
            if (o == null) {
                sleep(1000);
                continue;
            }
            long nextSeq = o.optLong("seq", seq.get());
            JSONArray routes = NativeData.arr(o, "routes");
            JSONObject totals = NativeData.obj(o, "totals");
            // 回调前再确认一次代际：旧线程不再往 UI 投递
            if (!running || gen != myGen) {
                return;
            }
            sink.onTrace(routes, totals);
            // seq 没往前走说明只是超时回来了，立刻再接一次即可
            if (nextSeq > 0) {
                seq.set(nextSeq);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
