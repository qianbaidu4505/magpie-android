package com.magpie.android;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脱敏页（原生 UI，底栏第 4 个 tab）：照 magpie 网页「脱敏」页 1:1 复刻。
 *
 * <p>上半是汇总卡（没让厂商看到的：总数 / 不同值数 / 请求数 / 按天柱图 /
 * top agents 分流摘要），下半是「按请求」列表。数据来自：
 * <ul>
 *   <li>{@code GET /api/masking/summary?days=} —— 汇总</li>
 *   <li>{@code GET /api/masking/records?limit=40} —— 请求列表</li>
 *   <li>{@code GET /api/state?last=1} —— 脱敏开关状态</li>
 *   <li>{@code GET /api/masking/sent?key=} —— 单条详情（厂商看到的样子）</li>
 * </ul>
 */
final class NativePageMasking extends NativePage {

    private static final int[] DAYS = {7, 30};

    private LinearLayout summaryBody;
    private LinearLayout listBody;
    private final TextView[] dayBtns = new TextView[2];
    private int days = 7;
    /** 筛选状态：空串 = 不过滤。 */
    private String filterAgent = "";
    private String filterProvider = "";
    private String filterFacet = ""; // ""=全部 / secret / rule / model / blocked
    /** 列表模式：req=按请求，val=按值。 */
    private String mode = "req";
    /** 汇总缓存（筛选项和流向图共用）。 */
    private JSONObject lastSummary;
    /** 记录分页：more 为 true 时可继续拉。 */
    private String lastKey = "";
    private boolean more;

    NativePageMasking(NativeHome host) {
        super(host);
    }

    @Override
    String name() {
        return "脱敏";
    }

    @Override
    void load() {
        host.bg(() -> {
            MagpieApi.Result sum = MagpieApi.get(host,
                    "/api/masking/summary?days=" + days, 6000, 15000);
            MagpieApi.Result recs = MagpieApi.get(host,
                    recordsQuery(40), 6000, 15000);
            MagpieApi.Result st = MagpieApi.stateLast(host);
            JSONObject sumJson = sum.ok() ? sum.json() : null;
            JSONObject recJson = recs.ok() ? recs.json() : null;
            // 脱敏开关的真实判定（照内核 maskOn()）：redact 开 / 个人信息开 /
            // 有自定义脱敏词，任一即视为开启；不是 settings.mask
            boolean on = false;
            JSONObject s = st.ok() ? st.json() : null;
            if (s != null) {
                JSONObject settings = s.optJSONObject("settings");
                if (settings != null) {
                    on = NativeData.flag(settings, "redact")
                            || NativeData.flag(settings, "redactPersonal")
                            || settings.optJSONArray("redactWords") != null
                            && settings.optJSONArray("redactWords").length() > 0;
                }
            }
            lastSummary = sumJson;
            final JSONObject fSum = sumJson;
            final JSONArray fRecs = recJson == null ? null
                    : NativeData.arr(recJson, "records");
            if (fRecs != null && fRecs.length() > 0) {
                lastKey = NativeData.str(NativeData.at(fRecs, fRecs.length() - 1), "key", "");
            } else {
                lastKey = "";
            }
            more = recJson != null && recJson.optBoolean("more", false);
            final boolean fOn = on;
            final String sumWhy = sum.ok() ? null : sum.why();
            final String recWhy = recs.ok() ? null : recs.why();
            host.post(() -> {
                rebuildKeepingScroll(() -> {
                    content.removeAllViews();
                    content.addView(summaryCard(fSum, fOn, sumWhy));
                    content.addView(recordsCard(fRecs, recWhy));
                });
                done();
            });
        });
    }

    // ------------------------------------------------------------------
    // 汇总卡
    // ------------------------------------------------------------------

    private View summaryCard(JSONObject sum, boolean on, String why) {
        LinearLayout card = ui.cardWithMargin();
        LinearLayout body = ui.column();
        card.addView(body);
        summaryBody = body;

        if (why != null && sum == null) {
            TextView e = ui.text("读不到脱敏汇总：" + why, 12, M3.ERROR, false);
            e.setPadding(0, ui.dp(8), 0, 0);
            body.addView(e);
            return card;
        }

        long n = sum == null ? 0 : NativeData.lng(sum, "n");
        long distinct = sum == null ? 0 : NativeData.lng(sum, "distinct");
        long requests = sum == null ? 0 : NativeData.lng(sum, "requests");

        LinearLayout head = ui.row();
        LinearLayout titles = ui.column();
        titles.addView(ui.text("没让厂商看到的", 17, M3.ON_SURFACE, true));
        TextView sub = ui.text(on
                        ? "每个请求离开这台手机前在本机脱敏，回复里再换回原样"
                        : "脱敏现在是关的：请求原样发往厂商",
                12.5f, M3.ON_SURFACE_VARIANT, false);
        sub.setLayoutParams(ui.matchWrap(4));
        titles.addView(sub);
        head.addView(titles, ui.weight(1));
        head.addView(ui.pill(on ? "已开启" : "已关闭",
                on ? M3.TERTIARY_CONTAINER : M3.SURFACE_CONTAINER_HIGH,
                on ? M3.ON_TERTIARY_CONTAINER : M3.ON_SURFACE));
        body.addView(head);

        // 标题旁的「脱敏设置」入口：点开设置面板（开关 + 脱敏词）
        TextView settingsBtn = ui.text("脱敏设置 >", 12.5f, M3.PRIMARY, true);
        settingsBtn.setPadding(0, ui.dp(2), 0, 0);
        settingsBtn.setOnClickListener(v -> showMaskSettings());
        // 放在 head 下面一行右对齐，紧贴标题区
        LinearLayout settingsRow = ui.row();
        LinearLayout.LayoutParams srlp = ui.matchWrap(2);
        settingsRow.setLayoutParams(srlp);
        settingsRow.addView(ui.text("", 1, M3.ON_SURFACE, false), ui.weight(1));
        settingsRow.addView(settingsBtn);
        body.addView(settingsRow);

        // 周期切换：7 天 / 30 天
        LinearLayout dayRow = ui.row();
        dayRow.setLayoutParams(ui.matchWrap(10));
        dayRow.addView(ui.text("统计范围", 12, M3.ON_SURFACE_VARIANT, false), ui.weight(1));
        for (int i = 0; i < DAYS.length; i++) {
            final int idx = i;
            TextView b = ui.text(DAYS[i] + " 天", 12, M3.ON_SURFACE_VARIANT, false);
            b.setGravity(Gravity.CENTER);
            b.setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6));
            if (days == DAYS[idx]) {
                b.setBackground(ui.roundStroke(ui.col(M3.PRIMARY_CONTAINER), 999,
                        ui.col(M3.PRIMARY), 1));
                b.setTextColor(ui.col(M3.PRIMARY));
                b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            } else {
                b.setBackground(ui.round(ui.col(M3.SURFACE_CONTAINER_HIGH), 999));
            }
            b.setOnClickListener(v -> {
                days = DAYS[idx];
                refresh(true);
            });
            dayBtns[idx] = b;
            LinearLayout.LayoutParams blp = ui.wrap();
            if (i > 0) {
                blp.leftMargin = ui.dp(6);
            }
            dayRow.addView(b, blp);
        }
        body.addView(dayRow);

        // 大数字
        TextView big = ui.text(NativeData.count(n), 40, M3.ON_SURFACE, true);
        big.setTypeface(Typeface.MONOSPACE);
        body.addView(big);
        TextView cap = ui.text("处已脱敏", 13, M3.ON_SURFACE_VARIANT, false);
        cap.setPadding(0, 0, 0, ui.dp(2));
        body.addView(cap);
        LinearLayout meta = ui.row();
        meta.addView(ui.text(NativeData.count(distinct) + " 不同的值", 12.5f,
                M3.ON_SURFACE_VARIANT, false), ui.weight(1));
        meta.addView(ui.text(NativeData.count(requests) + " 请求", 12.5f,
                M3.ON_SURFACE_VARIANT, false));
        body.addView(meta);

        // 按天柱图
        JSONArray dayArr = sum == null ? null : NativeData.arr(sum, "days");
        if (dayArr != null && dayArr.length() > 0) {
            body.addView(barRow(dayArr));
            LinearLayout lbl = ui.row();
            TextView a = ui.text(firstDayLabel(dayArr), 10.5f, M3.ON_SURFACE_VARIANT, false);
            a.setPadding(0, ui.dp(3), 0, 0);
            lbl.addView(a, ui.weight(1));
            TextView b = ui.text("今天", 10.5f, M3.PRIMARY, true);
            b.setPadding(0, ui.dp(3), 0, 0);
            lbl.addView(b);
            body.addView(lbl);
        }

        // 流向图（简化桑基）：左 agent → 右 provider，连线粗细按量比例
        JSONArray agents = sum == null ? null : NativeData.arr(sum, "agents");
        JSONArray providers = sum == null ? null : NativeData.arr(sum, "providers");
        if (agents != null && agents.length() > 0 && providers != null
                && providers.length() > 0) {
            body.addView(ui.divider());
            body.addView(flowGraph(agents, providers));
            TextView flowCap = ui.text("magpie 在这台手机上脱敏", 10.5f,
                    M3.ON_SURFACE_VARIANT, false);
            flowCap.setGravity(Gravity.CENTER);
            flowCap.setPadding(0, ui.dp(6), 0, ui.dp(2));
            body.addView(flowCap);
        }

        // 各 agent 检查统计
        JSONObject by = sum == null ? null : sum.optJSONObject("by");
        if (by != null) {
            LinearLayout checks = ui.column();
            for (int i = 0; i < agents.length() && i < 4; i++) {
                JSONObject a = NativeData.at(agents, i);
                if (a == null) {
                    continue;
                }
                String name = NativeData.str(a, "name",
                        NativeData.str(a, "id", "?"));
                LinearLayout row = ui.row();
                row.addView(ui.text(NativeData.pretty(name), 12.5f,
                        M3.ON_SURFACE, false), ui.weight(1));
                row.addView(ui.text("检查了 " + NativeData.count(NativeData.lng(a, "requests"))
                                + " 个 · " + NativeData.count(NativeData.lng(a, "n"))
                                + " 个有脱敏", 11, M3.ON_SURFACE_VARIANT, false));
                checks.addView(row);
            }
            if (checks.getChildCount() > 0) {
                body.addView(ui.divider());
                body.addView(checks);
            }
        }
        return card;
    }

    /**
     * 简化流向图：左列 agent 节点，右列 provider 节点，中间连线的粗细
     * 按该 agent 的脱敏量占比分配。自绘 Canvas，只画直线（不做曲线束）。
     */
    private View flowGraph(JSONArray agents, JSONArray providers) {
        return new android.view.View(ui.ctx) {
            @Override
            protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                android.graphics.Paint line = new android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG);
                line.setStyle(android.graphics.Paint.Style.STROKE);
                android.graphics.Paint text = new android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG);
                text.setColor(ui.col(M3.ON_SURFACE));
                text.setTextSize(ui.dp(12));
                android.graphics.Paint dim = new android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG);
                dim.setColor(ui.col(M3.PRIMARY));
                dim.setTextSize(ui.dp(11));
                dim.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

                int w = getWidth();
                int h = getHeight();
                if (w == 0 || h == 0) {
                    return;
                }
                int padX = ui.dp(10);
                int midX = w / 2;

                // 节点纵坐标：均匀分布
                int an = Math.min(agents.length(), 5);
                int pn = Math.min(providers.length(), 5);
                long totalA = 0;
                long totalP = 0;
                for (int i = 0; i < an; i++) {
                    totalA += NativeData.lng(NativeData.at(agents, i), "n");
                }
                for (int i = 0; i < pn; i++) {
                    totalP += NativeData.lng(NativeData.at(providers, i), "n");
                }
                if (totalA <= 0 || totalP <= 0) {
                    return;
                }
                float[] ay = new float[an];
                float[] py = new float[pn];
                for (int i = 0; i < an; i++) {
                    ay[i] = h * (i + 1) / (an + 1f);
                }
                for (int i = 0; i < pn; i++) {
                    py[i] = h * (i + 1) / (pn + 1f);
                }

                // 连线：每个 agent 连到所有 provider，粗细按量占比（简化：均分到 providers）
                // 真实按 agent->provider 的量没有单独接口，用 provider 量占比画右侧，
                // agent 量占比画左侧，中间汇聚到 magpie 节点
                int magY = h / 2;
                line.setColor((ui.col(M3.PRIMARY) & 0x00FFFFFF) | 0x33000000);
                for (int i = 0; i < an; i++) {
                    float share = (float) (NativeData.lng(NativeData.at(agents, i), "n") / (double) totalA);
                    line.setStrokeWidth(Math.max(ui.dp(1), ui.dp(10) * share));
                    canvas.drawLine(padX + ui.dp(4), ay[i], midX, magY, line);
                }
                for (int i = 0; i < pn; i++) {
                    float share = (float) (NativeData.lng(NativeData.at(providers, i), "n") / (double) totalP);
                    line.setStrokeWidth(Math.max(ui.dp(1), ui.dp(10) * share));
                    canvas.drawLine(midX, magY, w - padX - ui.dp(4), py[i], line);
                }

                // 中间 magpie 圆点
                android.graphics.Paint bird = new android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG);
                bird.setColor(ui.col(M3.PRIMARY_CONTAINER));
                canvas.drawCircle(midX, magY, ui.dp(16), bird);
                bird.setColor(ui.col(M3.PRIMARY));
                bird.setStyle(android.graphics.Paint.Style.STROKE);
                bird.setStrokeWidth(ui.dp(1.5f));
                canvas.drawCircle(midX, magY, ui.dp(16), bird);

                // 标签：左 agent 名+量，右 provider 名+量
                text.setTextAlign(android.graphics.Paint.Align.LEFT);
                dim.setTextAlign(android.graphics.Paint.Align.LEFT);
                for (int i = 0; i < an; i++) {
                    JSONObject a = NativeData.at(agents, i);
                    String name = NativeData.str(a, "name", NativeData.str(a, "id", "?"));
                    canvas.drawText(NativeData.pretty(name), padX, ay[i] - ui.dp(2), text);
                    canvas.drawText(NativeData.count(NativeData.lng(a, "n")),
                            padX, ay[i] + ui.dp(12), dim);
                }
                for (int i = 0; i < pn; i++) {
                    JSONObject p = NativeData.at(providers, i);
                    String name = NativeData.str(p, "name", NativeData.str(p, "id", "?"));
                    float tw = text.measureText(NativeData.pretty(name));
                    float nw = dim.measureText(NativeData.count(NativeData.lng(p, "n")));
                    float right = w - padX;
                    text.setTextAlign(android.graphics.Paint.Align.RIGHT);
                    dim.setTextAlign(android.graphics.Paint.Align.RIGHT);
                    canvas.drawText(NativeData.pretty(name), right,
                            py[i] - ui.dp(2), text);
                    canvas.drawText(NativeData.count(NativeData.lng(p, "n")),
                            right, py[i] + ui.dp(12), dim);
                }
                text.setTextAlign(android.graphics.Paint.Align.CENTER);
                dim.setTextAlign(android.graphics.Paint.Align.CENTER);
                canvas.drawText("脱敏", midX, magY + ui.dp(4), dim);
            }

            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int an = Math.min(agents.length(), 5);
                int pn = Math.min(providers.length(), 5);
                int rows = Math.max(an, pn);
                setMeasuredDimension(android.view.View.MeasureSpec
                                .getSize(widthSpec),
                        Math.max(ui.dp(120), rows * ui.dp(52)));
            }
        };
    }

    /** 按天小柱图：末根（今天）高亮。 */
    private View barRow(JSONArray daysArr) {
        LinearLayout row = new LinearLayout(ui.ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(56));
        lp.topMargin = ui.dp(14);
        row.setLayoutParams(lp);

        long max = 1;
        float[] vals = new float[daysArr.length()];
        for (int i = 0; i < daysArr.length(); i++) {
            JSONObject d = NativeData.at(daysArr, i);
            long v = d == null ? 0 : NativeData.lng(d, "n");
            vals[i] = v;
            max = Math.max(max, v);
        }
        for (int i = 0; i < daysArr.length(); i++) {
            View bar = new View(ui.ctx);
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(ui.dp(2));
            gd.setColor(i == daysArr.length() - 1
                    ? ui.col(M3.PRIMARY) : ui.col(M3.SURFACE_CONTAINER_HIGHEST));
            bar.setBackground(gd);
            int h = Math.max(ui.dp(2), Math.round(ui.dp(52) * vals[i] / max));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, h, 1f);
            blp.rightMargin = ui.dp(2);
            row.addView(bar, blp);
        }
        return row;
    }

    private String firstDayLabel(JSONArray daysArr) {
        JSONObject d = NativeData.at(daysArr, 0);
        if (d == null) {
            return "";
        }
        String day = NativeData.str(d, "day", NativeData.str(d, "t", ""));
        if (day.length() >= 10) {
            String[] p = day.substring(5, 10).split("-");
            return p[0] + "月" + p[1] + "日";
        }
        return "最早";
    }

    // ------------------------------------------------------------------
    // 记录列表
    // ------------------------------------------------------------------

    /** 拼接记录列表查询：limit + 筛选参数（agent/provider/facet/before）。 */
    private String recordsQuery(int limit) {
        StringBuilder q = new StringBuilder("/api/masking/records?limit=" + limit);
        if (filterAgent != null && !filterAgent.isEmpty()) {
            q.append("&agent=").append(android.net.Uri.encode(filterAgent));
        }
        if (filterProvider != null && !filterProvider.isEmpty()) {
            q.append("&provider=").append(android.net.Uri.encode(filterProvider));
        }
        if ("secret".equals(filterFacet)) {
            q.append("&kind=API_KEY,PRIVATE_KEY,TOKEN,PASSWORD,SECRET");
        } else if ("rule".equals(filterFacet) || "model".equals(filterFacet)) {
            q.append("&by=").append(filterFacet);
        } else if ("blocked".equals(filterFacet)) {
            q.append("&blocked=1");
        }
        return q.toString();
    }

    /** 筛选栏：按请求/按值 切换 + 类型筛选 + 智能体/提供方下拉。 */
    private View filterBar() {
        LinearLayout bar = new LinearLayout(ui.ctx);
        bar.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = ui.matchWrap(8);
        bar.setLayoutParams(lp);

        // 第一行：按请求 / 按值 + 类型筛选胶囊（全部/机密/规则/本地模型/拦截）
        LinearLayout row1 = ui.row();
        TextView req = modePill("按请求", "req".equals(mode));
        req.setOnClickListener(v -> { mode = "req"; refresh(true); });
        row1.addView(req);
        LinearLayout.LayoutParams rlp1 = (LinearLayout.LayoutParams) req.getLayoutParams();
        rlp1.rightMargin = ui.dp(6);
        TextView val = modePill("按值", "val".equals(mode));
        val.setOnClickListener(v -> { mode = "val"; refresh(true); });
        row1.addView(val, ui.weight(1));

        // 类型筛选：跟随网页 facet 定义
        String[][] facets = {{"", "全部"}, {"secret", "机密"},
                {"rule", "规则"}, {"model", "本地模型"}, {"blocked", "拦截"}};
        for (String[] f : facets) {
            long n = facetCount(f[0]);
            if (!f[0].isEmpty() && n == 0 && !filterFacet.equals(f[0])) {
                continue;
            }
            TextView b = modePill(f[1] + (f[0].isEmpty() || n == 0 ? "" : " " + n),
                    filterFacet.equals(f[0]));
            b.setOnClickListener(v -> {
                filterFacet = filterFacet.equals(f[0]) ? "" : f[0];
                refresh(true);
            });
            LinearLayout.LayoutParams blp = ui.wrap();
            blp.leftMargin = ui.dp(6);
            row1.addView(b, blp);
        }
        bar.addView(row1);

        // 第二行：智能体 / 提供方 下拉
        LinearLayout row2 = ui.row();
        TextView agentDrop = dropdown("智能体: " + displayName(filterAgent, "agent"),
                () -> pickFrom("agent"));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.rightMargin = ui.dp(6);
        row2.addView(agentDrop, dlp);
        TextView provDrop = dropdown("提供方: " + displayName(filterProvider, "provider"),
                () -> pickFrom("provider"));
        row2.addView(provDrop);
        bar.addView(row2);
        return bar;
    }

    /** 筛选胶囊样式。 */
    private TextView modePill(String label, boolean on) {
        TextView t = ui.text(label, 12, on ? M3.PRIMARY : M3.ON_SURFACE_VARIANT, on);
        t.setGravity(Gravity.CENTER);
        t.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6));
        if (on) {
            t.setBackground(ui.roundStroke(ui.col(M3.PRIMARY_CONTAINER), 999,
                    ui.col(M3.PRIMARY), 1));
        } else {
            t.setBackground(ui.round(ui.col(M3.SURFACE_CONTAINER_HIGH), 999));
        }
        return t;
    }

    /** 筛选下拉按钮样式。 */
    private TextView dropdown(String label, Runnable onClick) {
        TextView t = ui.text(label + " ▾", 12, M3.ON_SURFACE, false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        GradientDrawable bg = ui.roundStroke(ui.col(M3.SURFACE_CONTAINER_HIGH), 12,
                ui.col(M3.OUTLINE_VARIANT), 1);
        t.setBackground(bg);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    /** 筛选显示名：空 = 所有。 */
    private String displayName(String id, String kind) {
        if (id == null || id.isEmpty()) {
            return "所有";
        }
        return NativeData.pretty(id);
    }

    /** 弹列表选择：智能体 / 提供方。 */
    private void pickFrom(String kind) {
        JSONArray opts = summaryOptions(kind);
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.List<String> vals = new java.util.ArrayList<>();
        names.add("所有");
        vals.add("");
        if (opts != null) {
            for (int i = 0; i < opts.length(); i++) {
                JSONObject o = NativeData.at(opts, i);
                if (o == null) {
                    continue;
                }
                String name = NativeData.str(o, "name", NativeData.str(o, "id", "?"));
                names.add(NativeData.pretty(name));
                vals.add(name);
            }
        }
        new AlertDialog.Builder(host)
                .setTitle(kind.equals("agent") ? "选择智能体" : "选择提供方")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    String v = which < vals.size() ? vals.get(which) : "";
                    if (kind.equals("agent")) {
                        filterAgent = v;
                    } else {
                        filterProvider = v;
                    }
                    refresh(true);
                })
                .setNegativeButton("关闭", null)
                .create().show();
    }

    /** summary 里 agents/providers 数组。 */
    private JSONArray summaryOptions(String kind) {
        if (lastSummary == null) {
            return null;
        }
        return NativeData.arr(lastSummary, kind.equals("agent") ? "agents" : "providers");
    }

    /** facet 计数（跟网页 MASK_FACETS 一致）。 */
    private long facetCount(String id) {
        if (lastSummary == null) {
            return 0;
        }
        if (id.isEmpty()) {
            return NativeData.lng(lastSummary, "n");
        }
        if ("secret".equals(id)) {
            JSONArray kinds = NativeData.arr(lastSummary, "kinds");
            java.util.Set<String> secret = new java.util.HashSet<>(java.util.Arrays.asList(
                    "API_KEY", "PRIVATE_KEY", "TOKEN", "PASSWORD", "SECRET"));
            long n = 0;
            for (int i = 0; i < kinds.length(); i++) {
                JSONObject k = NativeData.at(kinds, i);
                if (k != null && secret.contains(NativeData.str(k, "name", k.optString("k", "")))) {
                    n += NativeData.lng(k, "n");
                }
            }
            return n;
        }
        JSONObject by = lastSummary.optJSONObject("by");
        if ("rule".equals(id) || "model".equals(id)) {
            return by == null ? 0 : NativeData.lng(by, id);
        }
        if ("blocked".equals(id)) {
            return NativeData.lng(lastSummary, "blocked");
        }
        return 0;
    }

    /** 按值视图：脱敏最多的值排行（横条 + 次数）。 */
    private View valueList() {
        LinearLayout list = ui.column();
        JSONArray values = lastSummary == null ? null
                : NativeData.arr(lastSummary, "values");
        if (values == null || values.length() == 0) {
            TextView e = ui.text("还没有脱敏值。", 12, M3.ON_SURFACE_VARIANT, false);
            e.setPadding(0, ui.dp(8), 0, 0);
            list.addView(e);
            return list;
        }
        long max = 1;
        for (int i = 0; i < values.length(); i++) {
            JSONObject v = NativeData.at(values, i);
            if (v != null) {
                max = Math.max(max, NativeData.lng(v, "n"));
            }
        }
        int shown = 0;
        for (int i = 0; i < values.length() && shown < 20; i++) {
            JSONObject v = NativeData.at(values, i);
            if (v == null) {
                continue;
            }
            // facet 过滤：机密只显示机密类；rule/model 按 by 字段
            String kind = NativeData.str(v, "kind", "");
            String by = NativeData.str(v, "by", "");
            if ("secret".equals(filterFacet) && !isSecretKind(kind)) {
                continue;
            }
            if (("rule".equals(filterFacet) || "model".equals(filterFacet))
                    && !filterFacet.equals(by)) {
                continue;
            }
            if ("blocked".equals(filterFacet)) {
                continue;
            }
            shown++;
            list.addView(valueRow(v, max, i + 1));
        }
        if (shown == 0) {
            TextView e = ui.text("没有匹配当前筛选的值。", 12, M3.ON_SURFACE_VARIANT, false);
            e.setPadding(0, ui.dp(8), 0, 0);
            list.addView(e);
        }
        return list;
    }

    private boolean isSecretKind(String kind) {
        return java.util.Arrays.asList("API_KEY", "PRIVATE_KEY", "TOKEN",
                "PASSWORD", "SECRET").contains(kind);
    }

    /** 一行值排行：序号 + 形状摘要 + 类型 + 条 + 次数。 */
    private View valueRow(JSONObject v, long max, int index) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(2), ui.dp(7), ui.dp(2), ui.dp(7));

        TextView idx = ui.mono(String.format(java.util.Locale.US, "%02d", index),
                10.5f, M3.ON_SURFACE_VARIANT);
        idx.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(26),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(idx);

        // 形状摘要（首尾字符 + 长度），不显示原文
        String shape = maskShape(NativeData.str(v, "h", ""),
                NativeData.lng(v, "len"));
        TextView sh = ui.text(shape, 12, M3.ON_SURFACE, false);
        sh.setSingleLine(true);
        sh.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        row.addView(sh, ui.weight(1));

        TextView kind = ui.text(kindName(NativeData.str(v, "kind", "")), 10.5f,
                M3.ON_SURFACE_VARIANT, false);
        kind.setPadding(ui.dp(6), 0, 0, 0);
        row.addView(kind);

        // 比例条
        View barWrap = barFill(NativeData.lng(v, "n"), max);
        row.addView(barWrap, new LinearLayout.LayoutParams(ui.dp(60), ui.dp(6)));

        TextView n = ui.text(String.valueOf(NativeData.lng(v, "n")), 12,
                M3.PRIMARY, true);
        n.setPadding(ui.dp(8), 0, 0, 0);
        row.addView(n);
        return row;
    }

    private View barFill(long n, long max) {
        LinearLayout wrap = new LinearLayout(ui.ctx);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        View fill = new View(ui.ctx);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(ui.dp(3));
        gd.setColor(ui.col(M3.PRIMARY));
        fill.setBackground(gd);
        int w = Math.max(ui.dp(3), Math.round(ui.dp(58) * n / Math.max(1, max)));
        wrap.addView(fill, new LinearLayout.LayoutParams(w, ui.dp(6)));
        return wrap;
    }

    /** 值形状：首 2 字 + … + 末 2 字 + 长度（照内核 maskShape 语义）。 */
    private String maskShape(String head, long len) {
        String h = head == null ? "" : head;
        if (h.length() > 4) {
            h = h.substring(0, 2) + "…" + h.substring(h.length() - 2);
        }
        return h + " (" + len + ")";
    }

    private View recordsCard(JSONArray recs, String why) {
        LinearLayout card = ui.cardWithMargin();
        LinearLayout head = ui.row();
        head.addView(ui.text(mode.equals("val") ? "按值" : "按请求", 15,
                M3.ON_SURFACE, true), ui.weight(1));
        head.addView(ui.text("最近 40 条", 11, M3.ON_SURFACE_VARIANT, false));
        card.addView(head);

        // 筛选栏：按请求/按值 + 类型筛选 + 智能体/提供方下拉
        card.addView(filterBar());

        if (why != null && recs == null) {
            TextView e = ui.text("读不到脱敏记录：" + why + "（点我重试）", 12,
                    M3.ERROR, false);
            e.setPadding(0, ui.dp(8), 0, 0);
            e.setOnClickListener(v -> refresh(true));
            card.addView(e);
            return card;
        }
        if (mode.equals("val")) {
            card.addView(valueList());
            return card;
        }
        if (recs == null || recs.length() == 0) {
            TextView e = ui.text("还没有脱敏记录。", 12, M3.ON_SURFACE_VARIANT, false);
            e.setPadding(0, ui.dp(8), 0, 0);
            card.addView(e);
            return card;
        }

        String lastDay = "";
        for (int i = 0; i < recs.length(); i++) {
            JSONObject r = NativeData.at(recs, i);
            if (r == null) {
                continue;
            }
            String day = dayName(NativeData.lng(r, "t"));
            if (!day.equals(lastDay)) {
                lastDay = day;
                TextView g = ui.text(day, 12, M3.ON_SURFACE_VARIANT, true);
                g.setPadding(ui.dp(2), ui.dp(10), 0, ui.dp(4));
                card.addView(g);
            }
            card.addView(recordRow(r));
        }
        return card;
    }

    private String dayName(long t) {
        if (t <= 0) {
            return "";
        }
        return new java.text.SimpleDateFormat("M月d日 E",
                java.util.Locale.CHINESE).format(new java.util.Date(t));
    }

    /** 一条请求小卡：时间 · agent → 厂商 · 数量；模型 + 类型摘要。 */
    private View recordRow(JSONObject r) {
        LinearLayout card = ui.column();
        GradientDrawable bg = ui.glowStroke(ui.col(M3.SURFACE_CONTAINER_LOW), 16,
                ui.col(M3.OUTLINE_VARIANT), 1);
        ui.glowCard(card, bg);
        card.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ui.dp(7);
        card.setLayoutParams(lp);

        LinearLayout l1 = ui.row();
        TextView time = ui.mono(clock(NativeData.lng(r, "t")), 10.5f,
                M3.ON_SURFACE_VARIANT);
        time.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(44),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        l1.addView(time);

        String agent = NativeData.str(r, "agent", "");
        String provider = NativeData.str(r, "provider", "");
        StringBuilder route = new StringBuilder();
        if (!agent.isEmpty()) {
            route.append(NativeData.pretty(agent));
        }
        route.append(" → ");
        route.append(provider.isEmpty() ? "厂商" : NativeData.pretty(provider));
        TextView rt = ui.text(route.toString(), 12, M3.ON_SURFACE, false);
        rt.setSingleLine(true);
        rt.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        l1.addView(rt, ui.weight(1));
        l1.addView(ui.text(String.valueOf(NativeData.lng(r, "n")), 12.5f,
                M3.PRIMARY, true));
        card.addView(l1);

        // 第二行：模型 + 类型摘要
        StringBuilder meta = new StringBuilder();
        String model = NativeData.str(r, "model", "");
        if (!model.isEmpty()) {
            meta.append(model);
        }
        JSONArray vals = NativeData.arr(r, "values");
        Map<String, Long> kinds = new LinkedHashMap<>();
        for (int i = 0; i < vals.length(); i++) {
            JSONObject v = NativeData.at(vals, i);
            if (v != null) {
                String k = NativeData.str(v, "k", "other");
                kinds.put(k, kinds.getOrDefault(k, 0L) + NativeData.lng(v, "n"));
            }
        }
        int shown = 0;
        for (Map.Entry<String, Long> e : kinds.entrySet()) {
            if (shown >= 3) {
                meta.append(" · +").append(kinds.size() - 3).append(" 类");
                break;
            }
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append(kindName(e.getKey())).append(' ').append(e.getValue());
            shown++;
        }
        if (NativeData.flag(r, "blocked")) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append("已拦截");
        }
        if (meta.length() > 0) {
            TextView m = ui.text(meta.toString(), 11, M3.ON_SURFACE_VARIANT, false);
            m.setSingleLine(true);
            m.setEllipsize(TextUtils.TruncateAt.END);
            m.setLayoutParams(ui.matchWrap(2));
            card.addView(m);
        }

        card.setOnClickListener(v -> showOne(r));
        return card;
    }

    private String clock(long t) {
        if (t <= 0) {
            return "--:--";
        }
        return new java.text.SimpleDateFormat("HH:mm",
                java.util.Locale.US).format(new java.util.Date(t));
    }

    private String kindName(String k) {
        if (k == null) {
            return "其它";
        }
        switch (k) {
            case "api-key":
            case "apikey":
                return "密钥";
            case "bearer":
            case "token":
                return "令牌";
            case "password":
            case "pwd":
                return "密码";
            case "email":
                return "邮箱";
            case "phone":
                return "手机号";
            case "id":
            case "idcard":
                return "证件号";
            case "card":
            case "bankcard":
                return "银行卡";
            case "url":
                return "网址";
            case "path":
                return "路径";
            case "command":
                return "命令";
            case "rule":
                return "规则词";
            case "model":
                return "本地模型";
            default:
                return k;
        }
    }

    /** 单条详情弹窗：厂商看到的样子（已脱敏全文）。 */
    private void showOne(JSONObject r) {
        final String key = NativeData.str(r, "key", "");
        if (key.isEmpty()) {
            return;
        }
        android.app.Dialog dlg = new android.app.Dialog(host, R.style.Theme_Magpie_Sheet);
        LinearLayout root = ui.column();
        GradientDrawable bg = ui.round(ui.col(M3.SURFACE_CONTAINER_LOW), 28);
        bg.setCornerRadii(new float[]{
                ui.dp(28), ui.dp(28), ui.dp(28), ui.dp(28), 0, 0, 0, 0});
        root.setBackground(bg);
        root.setPadding(ui.dp(16), ui.dp(14), ui.dp(16),
                ui.dp(16) + MainActivity.barHeightOf(host, "navigation_bar_height"));

        root.addView(ui.text("脱敏详情", 21, M3.ON_SURFACE, true));
        TextView sub = ui.text(
                NativeData.pretty(NativeData.str(r, "agent", "")) + " → "
                        + (NativeData.str(r, "provider", "").isEmpty()
                        ? "厂商" : NativeData.pretty(NativeData.str(r, "provider", ""))),
                12.5f, M3.ON_SURFACE_VARIANT, false);
        sub.setPadding(ui.dp(4), ui.dp(2), 0, ui.dp(10));
        root.addView(sub);

        ScrollView sc = new ScrollView(host);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout body = ui.column();
        sc.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView loading = ui.text("正在读取原文…", 12, M3.ON_SURFACE_VARIANT, false);
        body.addView(loading);

        dlg.setContentView(root);
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.BOTTOM);
            int maxH = (int) (host.getResources().getDisplayMetrics().heightPixels * 0.88f);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.min(maxH, host.getResources().getDisplayMetrics().heightPixels));
        }
        dlg.show();

        host.bg(() -> {
            MagpieApi.Result sent = MagpieApi.get(host,
                    "/api/masking/sent?key=" + android.net.Uri.encode(key),
                    8000, 15000);
            String shown = null;
            if (sent.ok() && sent.body != null) {
                try {
                    JSONObject o = new JSONObject(sent.body);
                    shown = o.has("body") ? o.getString("body") : sent.body;
                } catch (Exception ignored) {
                    shown = sent.body;
                }
            }
            final String fShown = shown == null ? null
                    : (shown.length() > 4000
                    ? shown.substring(0, 4000) + "\n…（已截断，网页里可看全文）" : shown);
            final String err = sent.ok() ? null : sent.why();
            host.post(() -> {
                body.removeAllViews();
                if (fShown == null) {
                    body.addView(ui.text("读不到厂商收到的内容：" + err, 12, M3.ERROR, false));
                    return;
                }
                body.addView(ui.text("厂商看到的样子（已脱敏）", 13.5f, M3.ON_SURFACE, true));
                TextView t = ui.mono(fShown, 10.5f, M3.ON_SURFACE_VARIANT);
                t.setTextIsSelectable(true);
                t.setLayoutParams(ui.matchWrap(8));
                body.addView(t);
            });
        });
    }

    // ------------------------------------------------------------------
    // 脱敏设置面板（开关 + 脱敏词）
    // ------------------------------------------------------------------

    /**
     * 设置面板：主开关 / 个人信息两个开关 + 脱敏词增删。
     *
     * <p>保存走 POST /api/settings（整份 prefs 原样回传、只改目标字段），
     * 与网页 savePrefs 的行为一致——这是内核唯一接受写设置的方式，
     * 不带全量会把其它设置清掉。
     */
    private void showMaskSettings() {
        android.app.Dialog dlg = new android.app.Dialog(host, R.style.Theme_Magpie_Sheet);
        LinearLayout root = ui.column();
        GradientDrawable bg = ui.round(ui.col(M3.SURFACE_CONTAINER_LOW), 28);
        bg.setCornerRadii(new float[]{
                ui.dp(28), ui.dp(28), ui.dp(28), ui.dp(28), 0, 0, 0, 0});
        root.setBackground(bg);
        root.setPadding(ui.dp(16), ui.dp(14), ui.dp(16),
                ui.dp(16) + MainActivity.barHeightOf(host, "navigation_bar_height"));

        root.addView(ui.text("脱敏设置", 21, M3.ON_SURFACE, true));
        TextView sub = ui.text("改完立刻保存到 magpie，下一个请求就生效", 12,
                M3.ON_SURFACE_VARIANT, false);
        sub.setPadding(ui.dp(4), ui.dp(2), 0, ui.dp(10));
        root.addView(sub);

        LinearLayout rows = ui.column();
        root.addView(rows);

        TextView status = ui.text("", 11.5f, M3.ON_SURFACE_VARIANT, false);
        status.setPadding(ui.dp(4), ui.dp(8), 0, 0);

        // 先拉当前 settings，再画
        TextView loading = ui.text("正在读取设置…", 12, M3.ON_SURFACE_VARIANT, false);
        rows.addView(loading);

        dlg.setContentView(root);
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.BOTTOM);
            int maxH = (int) (host.getResources().getDisplayMetrics().heightPixels * 0.82f);
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.min(maxH, host.getResources().getDisplayMetrics().heightPixels));
        }
        dlg.show();

        host.bg(() -> {
            MagpieApi.Result st = MagpieApi.stateLast(host);
            JSONObject settings = null;
            if (st.ok() && st.json() != null) {
                settings = st.json().optJSONObject("settings");
            }
            final JSONObject fSettings = settings;
            final String err = st.ok() ? null : st.why();
            host.post(() -> {
                rows.removeAllViews();
                if (fSettings == null) {
                    TextView e = ui.text("读不到设置：" + err, 12, M3.ERROR, false);
                    e.setPadding(0, ui.dp(8), 0, 0);
                    rows.addView(e);
                    return;
                }
                drawMaskSettings(dlg, rows, status, fSettings);
            });
        });
    }

    /** 设置面板内容：两个开关行 + 脱敏词列表 + 新增输入。 */
    private void drawMaskSettings(android.app.Dialog dlg, LinearLayout rows,
                                  TextView status, JSONObject settings) {
        // ---- 开关行：主开关 ----
        rows.addView(toggleRow("密钥脱敏",
                "API Key、私钥、Token 和密码以占位符发给厂商，回复里再还原",
                NativeData.flag(settings, "redact"),
                on -> saveMaskPrefs(settings, "redact", on, status)));

        // ---- 开关行：个人信息 ----
        rows.addView(toggleRow("脱敏个人信息",
                "邮箱、手机号、身份证号和银行卡号也一并脱敏",
                NativeData.flag(settings, "redactPersonal"),
                on -> saveMaskPrefs(settings, "redactPersonal", on, status)));

        // ---- 脱敏词 ----
        LinearLayout wordsHead = ui.row();
        LinearLayout.LayoutParams hlp = ui.matchWrap(10);
        hlp.topMargin = ui.dp(10);
        wordsHead.setLayoutParams(hlp);
        LinearLayout wt = ui.column();
        wt.addView(ui.text("脱敏词", 13.5f, M3.ON_SURFACE, true));
        TextView wh = ui.text("不想让厂商看到的词，发出去前换掉，回复里还原", 11,
                M3.ON_SURFACE_VARIANT, false);
        wt.addView(wh);
        wordsHead.addView(wt, ui.weight(1));
        TextView add = ui.pill("添加", M3.SECONDARY_CONTAINER, M3.ON_SECONDARY_CONTAINER);
        add.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6));
        wordsHead.addView(add);
        rows.addView(wordsHead);

        LinearLayout wordList = ui.column();
        rows.addView(wordList);
        android.widget.EditText input = new android.widget.EditText(host);
        input.setSingleLine(true);
        input.setHint("输入一个词");
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        input.setTextColor(ui.col(M3.ON_SURFACE));
        input.setHintTextColor(ui.col(M3.ON_SURFACE_VARIANT));
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(ui.col(M3.SURFACE_CONTAINER_HIGHEST));
        ibg.setCornerRadius(ui.dp(12));
        ibg.setStroke(ui.dp(1), ui.col(M3.OUTLINE_VARIANT));
        input.setBackground(ibg);
        input.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        LinearLayout.LayoutParams ilp = ui.matchWrap(6);
        input.setLayoutParams(ilp);
        input.setVisibility(android.view.View.GONE);
        rows.addView(input);

        // 画现有脱敏词
        Runnable[] redraw = new Runnable[1];
        redraw[0] = () -> {
            wordList.removeAllViews();
            org.json.JSONArray words = settings.optJSONArray("redactWords");
            if (words == null || words.length() == 0) {
                TextView none = ui.text("还没有脱敏词", 12, M3.ON_SURFACE_VARIANT, false);
                none.setPadding(ui.dp(4), ui.dp(6), 0, ui.dp(6));
                wordList.addView(none);
                return;
            }
            for (int i = 0; i < words.length(); i++) {
                final String word = words.optString(i, "");
                if (word.isEmpty()) {
                    continue;
                }
                LinearLayout row = ui.row();
                GradientDrawable rb = ui.round(ui.col(M3.SURFACE_CONTAINER), 12);
                row.setBackground(rb);
                row.setPadding(ui.dp(12), ui.dp(8), ui.dp(8), ui.dp(8));
                LinearLayout.LayoutParams rlp = ui.matchWrap(4);
                row.setLayoutParams(rlp);
                TextView tv = ui.text(word, 12.5f, M3.ON_SURFACE, false);
                tv.setSingleLine(true);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                row.addView(tv, ui.weight(1));
                TextView del = ui.text("删除", 11.5f, M3.ERROR, true);
                del.setPadding(ui.dp(8), ui.dp(4), ui.dp(4), ui.dp(4));
                del.setOnClickListener(v -> {
                    org.json.JSONArray next = new org.json.JSONArray();
                    for (int j = 0; j < words.length(); j++) {
                        String w = words.optString(j, "");
                        if (!w.isEmpty() && !w.equals(word)) {
                            next.put(w);
                        }
                    }
                    try {
                        settings.put("redactWords", next);
                    } catch (Exception ignored) {
                    }
                    saveMaskPrefs(settings, null, false, status);
                    redraw[0].run();
                });
                row.addView(del);
                wordList.addView(row);
            }
        };
        redraw[0].run();

        add.setOnClickListener(v -> {
            boolean showing = input.getVisibility() == android.view.View.VISIBLE;
            input.setVisibility(showing ? android.view.View.GONE : android.view.View.VISIBLE);
            if (!showing) {
                input.requestFocus();
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            String word = input.getText().toString().trim();
            if (word.isEmpty()) {
                return false;
            }
            org.json.JSONArray words = settings.optJSONArray("redactWords");
            org.json.JSONArray next = new org.json.JSONArray();
            if (words != null) {
                for (int j = 0; j < words.length(); j++) {
                    String w = words.optString(j, "");
                    if (!w.isEmpty()) {
                        next.put(w);
                    }
                }
            }
            next.put(word);
            try {
                settings.put("redactWords", next);
            } catch (Exception ignored) {
            }
            input.setText("");
            input.setVisibility(android.view.View.GONE);
            saveMaskPrefs(settings, null, false, status);
            redraw[0].run();
            return true;
        });
    }

    /** 一个开关行：标题 + 说明 + 右侧开关胶囊。 */
    private View toggleRow(String title, String sub, boolean on,
                           java.util.function.Consumer<Boolean> onChange) {
        LinearLayout row = ui.row();
        GradientDrawable rb = ui.glowStroke(ui.col(M3.SURFACE_CONTAINER_LOW), 16,
                ui.col(M3.OUTLINE_VARIANT), 1);
        ui.glowCard(row, rb);
        row.setPadding(ui.dp(14), ui.dp(11), ui.dp(14), ui.dp(11));
        LinearLayout.LayoutParams lp = ui.matchWrap(8);
        row.setLayoutParams(lp);

        LinearLayout texts = ui.column();
        texts.addView(ui.text(title, 14, M3.ON_SURFACE, true));
        TextView s = ui.text(sub, 11, M3.ON_SURFACE_VARIANT, false);
        s.setLineSpacing(ui.dp(1), 1f);
        s.setLayoutParams(ui.matchWrap(2));
        texts.addView(s);
        row.addView(texts, ui.weight(1));

        TextView t = ui.pill(on ? "开" : "关",
                on ? M3.TERTIARY_CONTAINER : M3.SURFACE_CONTAINER_HIGH,
                on ? M3.ON_TERTIARY_CONTAINER : M3.ON_SURFACE);
        t.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
        t.setOnClickListener(v -> onChange.accept(!on));
        row.addView(t);
        return row;
    }

    /**
     * 保存脱敏设置：POST /api/settings，整份 settings 原样回传、只改目标字段。
     * 与内核网页的 savePrefs 行为一致。
     */
    private void saveMaskPrefs(JSONObject settings, String key, boolean on,
                               TextView status) {
        if (key != null) {
            try {
                settings.put(key, on);
            } catch (Exception ignored) {
            }
        }
        status.setText("保存中…");
        host.bg(() -> {
            MagpieApi.Result r = MagpieApi.post(host, "/api/settings",
                    settings.toString(), 20000);
            host.post(() -> {
                if (r.ok()) {
                    status.setText("已保存，下一个请求生效");
                    refresh(true); // 刷新开关状态胶囊
                } else {
                    status.setText("保存失败：" + r.why());
                }
            });
        });
    }
}
