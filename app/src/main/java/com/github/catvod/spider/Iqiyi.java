package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 爱奇艺少儿/纪录片爬虫（Java 版）
 * 移植自 drpy 规则 iqiyi-child.js（奇珍异兽[官]）
 */
public class Iqiyi extends Spider {

    private static final String RECOMMEND = "https://pcw-api.iqiyi.com/search/recommend/list?channel_id=%s&data_type=1&page_id=%s&ret_num=24";
    private static final String VIDEOLIST = "https://pcw-api.iqiyi.com/search/video/videolists?channel_id=%s&data_type=1&pageNum=%s&pageSize=24";
    private static final String DETAIL = "https://pcw-api.iqiyi.com/video/video/videoinfowithuser/%s?agent_type=1&authcookie=&subkey=%s&subscribe=1";
    private static final String AVLIST = "https://pcw-api.iqiyi.com/albums/album/avlistinfo?aid=%s&size=200&page=%d";
    private static final String SVLIST = "https://pcw-api.iqiyi.com/album/source/svlistinfo?cid=6&sourceid=%s&timelist=%s";
    private static final String SEARCH = "https://search.video.iqiyi.com/o?if=html5&key=%s&pageNum=%s&pos=1&pageSize=24&site=iqiyi";

    private HashMap<String, String> getHeader() {
        HashMap<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        return header;
    }

    private List<Filter> getFilter(int channel) {
        List<Filter> items = new ArrayList<>();
        items.add(new Filter("mode", "综合排序", Arrays.asList(
                new Filter.Value("全部", ""), new Filter.Value("热播榜", "11"), new Filter.Value("好评榜", "8"), new Filter.Value("新上线", "4"))));
        if (channel == 3) {
            items.add(new Filter("three_category_id", "类型", Arrays.asList(
                    new Filter.Value("全部", ""), new Filter.Value("人文", "70"), new Filter.Value("历史", "74"), new Filter.Value("军事", "72"),
                    new Filter.Value("自然", "33933"), new Filter.Value("探险", "73"), new Filter.Value("社会", "71"), new Filter.Value("美食", "33908"), new Filter.Value("科技", "28119"))));
            items.add(new Filter("region", "地区", Arrays.asList(
                    new Filter.Value("全部", ""), new Filter.Value("国内", "20323"), new Filter.Value("国外", "20324"))));
            items.add(new Filter("year", "全部年份", getYears()));
        }
        items.add(new Filter("is_purchase", "全部资费", Arrays.asList(
                new Filter.Value("全部", ""), new Filter.Value("免费", "0"), new Filter.Value("会员", "1"), new Filter.Value("付费", "2"))));
        return items;
    }

    private List<Filter.Value> getYears() {
        return Arrays.asList(new Filter.Value("全部", ""), new Filter.Value("2026", "2026"), new Filter.Value("2025", "2025"),
                new Filter.Value("2024", "2024"), new Filter.Value("2023", "2023"), new Filter.Value("2022", "2022"));
    }

    @Override
    public void init(Context context, String extend) {
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = Arrays.asList(new Class("15", "少儿"), new Class("3", "纪录片"));
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        for (Class item : classes) filters.put(item.getTypeId(), getFilter(Integer.parseInt(item.getTypeId())));
        return Result.string(classes, filters);
    }

    @Override
    public String homeVideoContent() {
        return Result.string(recommend("15", "1").list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        String url = String.format(tid.equals("1") || tid.equals("4") ? VIDEOLIST : RECOMMEND, tid, pg);
        String region = extend.containsKey("region") ? extend.get("region") : "";
        String three = extend.containsKey("three_category_id") ? extend.get("three_category_id") : "";
        // 原规则：region 值注入 three_category_id，region 本身不作为参数传递
        if (!TextUtils.isEmpty(region)) three = region;
        StringBuilder sb = new StringBuilder(url);
        if (!TextUtils.isEmpty(three)) sb.append("&three_category_id=").append(three);
        if (extend.containsKey("year") && !TextUtils.isEmpty(extend.get("year"))) sb.append("&market_release_date_level=").append(extend.get("year"));
        if (extend.containsKey("mode") && !TextUtils.isEmpty(extend.get("mode"))) sb.append("&mode=").append(extend.get("mode"));
        if (extend.containsKey("is_purchase") && !TextUtils.isEmpty(extend.get("is_purchase"))) sb.append("&is_purchase=").append(extend.get("is_purchase"));
        return pageResult(fetch(sb.toString()), pg);
    }

    private Recommend recommend(String channel, String pg) {
        Recommend result = new Recommend();
        try {
            JSONObject data = fetch(String.format(RECOMMEND, channel, pg));
            JSONArray list = data.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    String vid = item.optString("albumId", item.optString("tvId"));
                    if (TextUtils.isEmpty(vid) || "0".equals(vid)) continue;
                    result.list.add(new Vod(vid, item.optString("name"), pic(item.optString("imageUrl"), "_390_520"), remark(item)));
                }
            }
            result.hasNext = data.optInt("has_next") == 1;
        } catch (Exception ignored) {
        }
        return result;
    }

    private String remark(JSONObject item) {
        int channelId = item.optInt("channelId");
        String score = item.optDouble("score", 0) > 0 ? item.optDouble("score", 0) + "分\t" : "";
        if (channelId == 1) {
            return score + item.optString("duration");
        } else if (channelId == 2 || channelId == 4 || channelId == 35 || channelId == 15 || channelId == 37) {
            int latest = item.optInt("latestOrder");
            int total = item.optInt("videoCount");
            if (latest == total && latest > 0) return score + latest + "集全";
            if (total > 0 && latest > 0) return score + latest + "/" + total + "集";
            if (latest > 0) return "更新至 " + latest + "集";
            return item.optString("focus");
        } else if (channelId == 6) {
            return item.optString("period") + "期";
        }
        int latest = item.optInt("latestOrder");
        if (latest > 0) return "更新至 第" + latest + "期";
        if (!TextUtils.isEmpty(item.optString("period"))) return item.optString("period");
        return item.optString("focus");
    }

    private String pic(String url, String size) {
        return TextUtils.isEmpty(url) ? "" : url.replace(".jpg", size + ".jpg?caplist=jpg,webp");
    }

    private JSONObject fetch(String url) {
        try {
            return new JSONObject(OkHttp.string(url, getHeader())).getJSONObject("data");
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String pageResult(JSONObject data, String pg) {
        List<Vod> list = new ArrayList<>();
        boolean hasNext = false;
        try {
            JSONArray array = data.optJSONArray("list");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    String vid = item.optString("albumId", item.optString("tvId"));
                    if (TextUtils.isEmpty(vid) || "0".equals(vid)) continue;
                    list.add(new Vod(vid, item.optString("name"), pic(item.optString("imageUrl"), "_390_520"), remark(item)));
                }
            }
            hasNext = data.optInt("has_next") == 1;
        } catch (Exception ignored) {
        }
        int page = parseInt(pg, 1);
        return Result.get().vod(list).page(page, hasNext ? page + 1 : page, 24, 0).string();
    }

    private static int parseInt(String text, int def) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        String id = ids.get(0);
        JSONObject json = fetch(String.format(DETAIL, id, id));
        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(json.optString("name"));
        int channelId = json.optInt("channelId");
        String size = "579_772";
        try {
            size = json.optJSONArray("imageSize").getString(12);
        } catch (Exception ignored) {
        }
        vod.setVodPic(pic(json.optString("imageUrl"), "_" + size));
        List<String> typeNames = new ArrayList<>();
        JSONArray categories = json.optJSONArray("categories");
        if (categories != null) for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category != null && !TextUtils.isEmpty(category.optString("name"))) typeNames.add(category.optString("name"));
        }
        vod.setTypeName(TextUtils.join(",", typeNames));
        List<String> actors = new ArrayList<>();
        JSONObject people = json.optJSONObject("people");
        JSONArray mainCharactor = people == null ? null : people.optJSONArray("main_charactor");
        if (mainCharactor != null) for (int i = 0; i < mainCharactor.length(); i++) {
            JSONObject actor = mainCharactor.optJSONObject(i);
            if (actor != null && !TextUtils.isEmpty(actor.optString("name"))) actors.add(actor.optString("name"));
        }
        vod.setVodActor(TextUtils.join(",", actors));
        vod.setVodContent(json.optString("description"));
        vod.setVodArea((TextUtils.isEmpty(json.optString("focus")) ? "" : json.optString("focus") + "\n") + "资费：" + (json.optInt("payMark") == 1 ? "VIP" : "免费"));
        String remarks = "类型: " + TextUtils.join("\t", typeNames) + "　评分：" + json.optDouble("score", 0);
        int latest = json.optInt("latestOrder");
        if (latest > 0) remarks += "\n更新至：第" + latest + "集(期)/共" + json.optInt("videoCount") + "集(期)";
        vod.setVodRemarks(remarks);
        List<String> episodes = new ArrayList<>();
        try {
            if (channelId == 1) {
                episodes.add((TextUtils.isEmpty(json.optString("shortTitle")) ? "播放" : json.optString("shortTitle")) + "$" + json.optString("playUrl"));
            } else if (channelId == 6) {
                String qs = json.optString("period").split("-")[0];
                JSONObject data = fetch(String.format(SVLIST, json.optString("albumId"), qs));
                JSONArray playData = data.optJSONArray(qs);
                appendEpisodes(episodes, playData == null ? new JSONArray() : playData);
            } else {
                JSONObject data = fetch(String.format(AVLIST, json.optString("albumId"), 1));
                int total = data.optInt("total");
                appendEpisodes(episodes, data.optJSONArray("epsodelist"));
                for (int page = 2; page <= total / 200 + 1; page++) {
                    data = fetch(String.format(AVLIST, json.optString("albumId"), page));
                    appendEpisodes(episodes, data.optJSONArray("epsodelist"));
                }
            }
        } catch (Exception ignored) {
        }
        vod.setVodPlayFrom("qiyi");
        vod.setVodPlayUrl(TextUtils.join("#", episodes));
        return Result.string(vod);
    }

    private void appendEpisodes(List<String> episodes, JSONArray list) {
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            String title = item.optString("shortTitle");
            if (TextUtils.isEmpty(title)) title = "第" + item.optInt("order") + "集";
            String url = item.optString("playUrl");
            if (TextUtils.isEmpty(url)) continue;
            episodes.add(title + "$" + url);
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        List<Vod> list = new ArrayList<>();
        try {
            JSONObject data = fetch(String.format(SEARCH, URLEncoder.encode(key, "UTF-8"), pg));
            JSONArray docinfos = data.optJSONArray("docinfos");
            if (docinfos != null) {
                for (int i = 0; i < docinfos.length(); i++) {
                    JSONObject doc = docinfos.optJSONObject(i);
                    JSONObject album = doc == null ? null : doc.optJSONObject("albumDocInfo");
                    if (album == null) continue;
                    String vid = album.optString("albumId");
                    if (TextUtils.isEmpty(vid) || "0".equals(vid)) continue;
                    list.add(new Vod(vid, album.optString("albumTitle"), album.optString("albumVImage"), album.optString("tvFocus")));
                }
            }
        } catch (Exception ignored) {
        }
        return Result.get().vod(list).string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        String page = id.contains("?") ? id.substring(0, id.indexOf("?")) : id;
        HashMap<String, String> header = getHeader();
        try {
            HashMap<String, String> mobile = new HashMap<>();
            mobile.put("User-Agent", "okhttp/3.14.9");
            mobile.put("Content-Type", "application/x-www-form-urlencoded");
            JSONObject json = new JSONObject(OkHttp.string(page, mobile));
            String url = json.optString("url");
            if (url.contains("qiyi.com")) return Result.get().url(url).header(header).string();
        } catch (Exception ignored) {
        }
        return Result.get().parse().jx().url(page).header(header).string();
    }

    private static class Recommend {
        final List<Vod> list = new ArrayList<>();
        boolean hasNext;
    }
}
