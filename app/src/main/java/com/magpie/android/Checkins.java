package com.magpie.android;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * 签到：上游各家「每日签到」在壳里的入口。
 *
 * <p>上游没有总开关——每家供应商一个（WorkBuddy / Trae CN / MiniMax Code /
 * Qoder 四家内置 + 插件型若干），各走各的 API。这里把开关与手动签到
 * 收拢成一个模块，账号页与弹层共用。
 *
 * <p>API 对照（上游 v0.1.4738）：
 * <ul>
 *   <li>开关：POST /api/settings/&lt;vendor&gt;-checkin {on}；插件型
 *       POST /api/settings/plugin-checkin {provider, on}</li>
 *   <li>立即签：POST /api/usage/&lt;vendor&gt;-checkin；插件型
 *       POST /api/usage/plugin-checkin {provider}，返回 WorkBuddyCheckin[]</li>
 *   <li>今日状态：/api/state 的 workbuddyCheckins / traeCheckins /
 *       minimaxCheckins / qoderCheckins（outcome claimed|done=已签）与
 *       checkinPlugins[i].checkins</li>
 * </ul>
 *
 * <p>「今天」按北京时间算（上游的 Beijing day）：供应商的签到日以
 * Asia/Shanghai 为准，不跟手机时区走。
 */
final class Checkins {

    private static final String TAG = "MagpieCheckin";

    /** 内置四家的 API 名与 state 字段名。 */
    static final String[][] BUILTIN = {
            {"workbuddy", "workbuddyCheckins"},
            {"trae", "traeCheckins"},
            {"minimax", "minimaxCheckins"},
            {"qoder", "qoderCheckins"},
    };

    static final String[] VENDOR_TITLES = {
            "WorkBuddy", "Trae CN", "MiniMax Code", "Qoder",
    };

    private Checkins() {
    }

    /** 供应商 id（provider id 或 by）→ 它属于哪家签到（内置 vendor 名或 "plugin:<id>"）。 */
    static String vendorOf(String providerId) {
        String p = providerId == null ? "" : providerId.toLowerCase(Locale.US);
        // 内置四家 & 它们的变体 id（上游 PLUGIN_CHECKIN_INHERITS 同款映射）
        switch (p) {
            case "workbuddy":
            case "workbuddy-ai":
                return "workbuddy";
            case "trae":
            case "trae-cn":
                return "trae";
            case "minimax-code":
            case "minimax-code-global":
            case "minimax":
                return "minimax";
            case "qoder":
            case "qoder-cn":
                return "qoder";
            default:
                return "plugin:" + providerId;
        }
    }

    /** 内置 vendor 的 API 名（workbuddy/trae/minimax/qoder → 同名；plugin 型返回 null）。 */
    static String builtinApi(String vendor) {
        for (String[] b : BUILTIN) {
            if (b[0].equals(vendor)) {
                return b[0];
            }
        }
        return null;
    }

    /** 北京时间今天，yyyy-MM-dd。 */
    static String todayBeijing() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return f.format(new Date());
    }

    /**
     * 这家供应商今天的签到状态。
     *
     * @return "done"=已签（claimed/done），"due"=今天还没签，
     *         "none"=这家没有可签的账号，"off"=自动签到关着（仍可手动签）
     */
    static String statusOf(JSONObject state, String providerId) {
        String vendor = vendorOf(providerId);
        String api = builtinApi(vendor);
        String today = todayBeijing();
        try {
            JSONArray arr = null;
            if (api != null) {
                for (String[] b : BUILTIN) {
                    if (b[0].equals(api)) {
                        arr = state.optJSONArray(b[1]);
                    }
                }
            } else {
                JSONArray ps = state.optJSONArray("checkinPlugins");
                if (ps != null) {
                    String id = vendor.substring("plugin:".length());
                    for (int i = 0; i < ps.length(); i++) {
                        JSONObject pc = NativeData.at(ps, i);
                        if (pc != null && id.equals(NativeData.str(pc, "id", ""))) {
                            arr = pc.optJSONArray("checkins");
                            break;
                        }
                    }
                }
            }
            if (arr == null || arr.length() == 0) {
                return "none";
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = NativeData.at(arr, i);
                if (c == null) {
                    continue;
                }
                String day = NativeData.str(c, "day", "");
                String outcome = NativeData.str(c, "outcome", "");
                boolean again = NativeData.flag(c, "again");
                if (today.equals(day) && !"failed".equals(outcome) && !again) {
                    return "done";
                }
            }
            return "due";
        } catch (Exception e) {
            Log.w(TAG, "statusOf: " + e.getMessage());
            return "none";
        }
    }

    /** 立即签到这家。返回 null=成功（结果已写进 state），否则错误文案。 */
    static String checkinNow(Context ctx, String providerId, JSONObject[] stateOut) {
        String vendor = vendorOf(providerId);
        String api = builtinApi(vendor);
        String path;
        String body = null;
        if (api != null) {
            path = "/api/usage/" + api + "-checkin";
        } else {
            path = "/api/usage/plugin-checkin";
            try {
                body = new JSONObject().put("provider",
                        vendor.substring("plugin:".length())).toString();
            } catch (Exception e) {
                return e.getMessage();
            }
        }
        MagpieApi.Result r = MagpieApi.post(ctx, path, body, MagpieApi.WRITE_MS);
        if (!r.ok()) {
            return r.why();
        }
        // 签完重读 state（今天状态变了）
        MagpieApi.Result s = MagpieApi.stateLast(ctx);
        if (s.ok() && s.json() != null) {
            stateOut[0] = s.json();
        }
        return null;
    }

    /** 读自动签到开关（内置四家 + 插件型）。返回 {vendor: on}。 */
    static Map<String, Boolean> switches(JSONObject state) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        try {
            JSONObject st = state.optJSONObject("settings");
            for (String[] b : BUILTIN) {
                String key = b[0] + "Checkin";
                boolean on = st != null && st.optBoolean(key, false);
                out.put(b[0], on);
            }
            JSONObject pc = st == null ? null : st.optJSONObject("pluginCheckins");
            JSONArray ps = state.optJSONArray("checkinPlugins");
            if (ps != null) {
                for (int i = 0; i < ps.length(); i++) {
                    JSONObject p = NativeData.at(ps, i);
                    if (p == null) {
                        continue;
                    }
                    String id = NativeData.str(p, "id", "");
                    // 上游规则：显式 false 优先，其次 checkinPlugins.on，
                    // 都没有时内置四家的插件跟内置开关走
                    boolean on;
                    if (pc != null && pc.has(id)) {
                        on = pc.optBoolean(id, false);
                    } else {
                        on = p.optBoolean("on", false);
                    }
                    out.put("plugin:" + id, on);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "switches: " + e.getMessage());
        }
        return out;
    }

    /** 设自动签到开关。返回 null=成功。 */
    static String setSwitch(Context ctx, String vendor, boolean on, JSONObject[] stateOut) {
        try {
            String path;
            String body;
            if (vendor.startsWith("plugin:")) {
                path = "/api/settings/plugin-checkin";
                body = new JSONObject()
                        .put("provider", vendor.substring("plugin:".length()))
                        .put("on", on).toString();
            } else {
                path = "/api/settings/" + vendor + "-checkin";
                body = new JSONObject().put("on", on).toString();
            }
            MagpieApi.Result r = MagpieApi.post(ctx, path, body, MagpieApi.WRITE_MS);
            if (!r.ok()) {
                return r.why();
            }
            // 返回的是 settingsState；开关状态以 /api/state?last=1 为准重读
            MagpieApi.Result s = MagpieApi.stateLast(ctx);
            if (s.ok() && s.json() != null) {
                stateOut[0] = s.json();
            }
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /** 这家有没有能签的账号（checkinAccounts 里它的 by=vendor 有记录）。 */
    static boolean hasAccounts(JSONObject state, String providerId) {
        String vendor = vendorOf(providerId);
        JSONArray arr = state.optJSONArray("checkinAccounts");
        if (arr == null) {
            return false;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = NativeData.at(arr, i);
            if (a == null) {
                continue;
            }
            String by = NativeData.str(a, "by", "");
            // 上游 WorkBuddy 的 by 是空字符串（checkiner.by 注释："" for WorkBuddy），
            // 其余各家才是 workbuddy 之外的名字；两种都认
            if ("workbuddy".equals(vendor)) {
                if (by.isEmpty() || "workbuddy".equals(by)) {
                    return true;
                }
            } else if (vendor.equals(by)) {
                return true;
            }
        }
        return false;
    }
}
