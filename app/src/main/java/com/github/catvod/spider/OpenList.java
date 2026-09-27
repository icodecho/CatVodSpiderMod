package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.openlist.Drive;
import com.github.catvod.bean.openlist.Item;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Image;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * OpenList/AList v2/v3 网盘爬虫（Java 版）
 * 移植自 openlist_spider.js (lisaac/openlist-tvbox, hjdhnx/dr_py)
 * ext 支持 JSON 数组或远程 URL（可带 ;limit 后缀限制搜索条数）
 */
public class OpenList extends Spider {

    private List<Drive> drives;
    private String showMode = "single";
    private String detailOrder = "name";
    private int limitSearch = 200;

    private static class Entry {
        final Item item;
        String id;

        Entry(Item item, String id) {
            this.item = item;
            this.id = id;
        }
    }

    private void fetchRule(String ext) {
        if (drives != null && !drives.isEmpty()) return;
        if (ext.startsWith("http")) {
            String[] split = ext.split(";");
            ext = OkHttp.string(split[0]);
            if (split.length > 1) limitSearch = parseInt(split[1], limitSearch);
        }
        drives = Drive.arrayFrom(ext);
        if (drives == null) drives = new ArrayList<>();
    }

    @Override
    public void init(Context context, String extend) {
        fetchRule(extend);
    }

    private Drive getDrive(String name) {
        int index = drives.indexOf(new Drive(name));
        return drives.get(Math.max(index, 0)).check();
    }

    private static String stripMarkers(String tid) {
        return tid.replace("#all#", "").replace("#search#", "");
    }

    private static String nameOf(String tid) {
        int index = tid.indexOf("$");
        return index > 0 ? tid.substring(0, index) : tid;
    }

    private static String pathOf(String tid) {
        int index = tid.indexOf("$");
        return index > 0 ? tid.substring(index + 1) : "/";
    }

    private static int parseInt(String text, int def) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private List<Filter> getFilter() {
        List<Filter> items = new ArrayList<>();
        items.add(new Filter("order", "排序", Arrays.asList(
                new Filter.Value("名称⬆️", "vod_name_asc"), new Filter.Value("名称⬇️", "vod_name_desc"),
                new Filter.Value("中英⬆️", "vod_cn_asc"), new Filter.Value("中英⬇️", "vod_cn_desc"),
                new Filter.Value("时间⬆️", "vod_time_asc"), new Filter.Value("时间⬇️", "vod_time_desc"),
                new Filter.Value("大小⬆️", "vod_size_asc"), new Filter.Value("大小⬇️", "vod_size_desc"),
                new Filter.Value("无", "none"))));
        items.add(new Filter("show", "播放展示", Arrays.asList(new Filter.Value("单集", "single"), new Filter.Value("全集", "all"))));
        return items;
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        for (Drive drive : drives) classes.add(drive.check().toType());
        for (Class item : classes) filters.put(item.getTypeId(), getFilter());
        return Result.string(classes, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        if (extend.containsKey("show") && !TextUtils.isEmpty(extend.get("show"))) showMode = extend.get("show");
        String orid = stripMarkers(tid);
        Drive drive = getDrive(nameOf(orid));
        List<Entry> entries = getEntries(drive, pathOf(orid));
        sortEntries(extend.containsKey("order") ? extend.get("order") : "", entries);
        List<Vod> list = new ArrayList<>();
        for (Entry entry : entries) list.add(toVod(entry));
        return Result.get().vod(list).page(1, 1, list.size(), list.size()).string();
    }

    private Vod toVod(Entry entry) {
        Item item = entry.item;
        String remark = item.getRemark() + (item.isFolder() ? " 文件夹" : "");
        if (entry.id.contains("@@@")) remark += "🏷️";
        return new Vod(entry.id, item.getDisplayName(), item.getPic(), remark, item.isFolder());
    }

    private void sortEntries(String order, List<Entry> entries) {
        if (TextUtils.isEmpty(order)) {
            if (!"none".equals(detailOrder)) sort("name", "asc", entries);
            return;
        }
        if (order.contains("none")) {
            detailOrder = "none";
            return;
        }
        int split = order.lastIndexOf('_');
        String key = order.substring(0, split);
        String asc = order.substring(split + 1);
        detailOrder = key.contains("cn") ? "cn" : key.contains("time") ? "time" : key.contains("size") ? "size" : "name";
        sort(key.replace("vod_", ""), asc, entries);
    }

    private void sort(String key, String order, List<Entry> entries) {
        boolean desc = "desc".equals(order);
        Comparator<Entry> comparator;
        switch (key) {
            case "cn":
                comparator = (a, b) -> Collator.getInstance(Locale.CHINA).compare(a.item.getDisplayName(), b.item.getDisplayName());
                break;
            case "time":
                comparator = Comparator.comparing(o -> o.item.getUpdated());
                break;
            case "size":
                comparator = Comparator.comparingLong(o -> o.item.getSize());
                break;
            default:
                comparator = (a, b) -> naturalCompare(a.item.getDisplayName(), b.item.getDisplayName());
                break;
        }
        Collections.sort(entries, desc ? comparator.reversed() : comparator);
    }

    private static int naturalCompare(String a, String b) {
        int i = 0, j = 0, la = a.length(), lb = b.length();
        while (i < la && j < lb) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < la && Character.isDigit(a.charAt(i))) i++;
                while (j < lb && Character.isDigit(b.charAt(j))) j++;
                long na = Long.parseLong(a.substring(si, i));
                long nb = Long.parseLong(b.substring(sj, j));
                if (na != nb) return na > nb ? 1 : -1;
            } else {
                if (ca != cb) return ca > cb ? 1 : -1;
                i++;
                j++;
            }
        }
        return (la - i) - (lb - j);
    }

    private String post(Drive drive, String url, String path, boolean retry) {
        try {
            JSONObject params = new JSONObject();
            params.put("path", path);
            params.put("password", drive.findPass(path));
            String response = OkHttp.post(url, params.toString(), drive.getHeader()).getBody();
            if (retry && (response.contains("\"code\":401") || response.contains("\"code\":403")) && drive.login()) return post(drive, url, path, false);
            return response;
        } catch (Exception e) {
            return "";
        }
    }

    private List<Item> getItems(Drive drive, String path) {
        try {
            JSONObject data = new JSONObject(post(drive, drive.listApi(), path, true)).getJSONObject("data");
            JSONArray array = data.has("content") ? data.getJSONArray("content") : data.getJSONArray("files");
            List<Item> items = Item.arrayFrom(array.toString());
            return items == null ? new ArrayList<>() : items;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Entry> getEntries(Drive drive, String path) {
        String dir = path.endsWith("/") ? path : path + "/";
        List<Entry> entries = new ArrayList<>();
        List<String> subs = new ArrayList<>();
        List<Entry> videos = new ArrayList<>();
        for (Item item : getItems(drive, path)) {
            if (item.isSub()) subs.add(item.getName());
            if (!drive.showAll() && !item.isFolder() && !item.isMedia()) continue;
            Entry entry = new Entry(item, drive.getName() + "$" + dir + item.getName() + (item.isFolder() ? "/" : ""));
            if ("all".equals(showMode)) entry.id += "#all#";
            entries.add(entry);
            if (item.isMedia()) videos.add(entry);
        }
        attachSubs(videos, subs);
        return entries;
    }

    private void attachSubs(List<Entry> videos, List<String> subs) {
        if (subs.isEmpty() || videos.isEmpty()) return;
        if (videos.size() == 1) {
            String best = "";
            int score = -1;
            for (String sub : subs) {
                int value = (sub.contains("chs") ? 100 : 0) + similarity(sub, videos.get(0).item.getDisplayName());
                if (value > score) {
                    score = value;
                    best = sub;
                }
            }
            videos.get(0).id += "@@@" + best;
        } else {
            for (Entry entry : videos) {
                for (String sub : subs) {
                    if (similarity(sub, entry.item.getDisplayName()) > 60) {
                        entry.id += "@@@" + sub;
                        break;
                    }
                }
            }
        }
    }

    private static int similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 100 : 100 - 100 * levenshtein(a, b) / max;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }

    @Override
    public String detailContent(List<String> ids) {
        String otid = ids.get(0);
        boolean isSearch = otid.contains("#search#");
        String tid = stripMarkers(otid);
        Drive drive = getDrive(nameOf(tid));
        String path = pathOf(tid).split("@@@")[0];
        String name = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        name = name.contains("/") ? name.substring(name.lastIndexOf("/") + 1) : name;
        if (name.isEmpty()) name = drive.getName();
        if (isSearch) name += "[搜]";
        String dir = path.endsWith("/") ? path : path.substring(0, path.lastIndexOf("/") + 1);
        List<String> playUrls = new ArrayList<>();
        for (Entry entry : getEntries(drive, dir)) {
            if (entry.item.isFolder() || !entry.item.isMedia()) continue;
            playUrls.add(entry.item.getDisplayName() + "$" + entry.id.substring(entry.id.indexOf("$") + 1).replace("#all#", ""));
        }
        Vod vod = new Vod();
        vod.setVodId(otid);
        vod.setVodName(name);
        vod.setTypeName("文件夹");
        vod.setVodPic(Image.FOLDER);
        vod.setVodContent(otid);
        vod.setVodPlayFrom(drive.getName());
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));
        vod.setVodRemarks(drive.getTitle());
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        Drive drive = null;
        for (Drive item : drives) if (item.searchable()) {
            drive = item.check();
            break;
        }
        if (drive == null || TextUtils.isEmpty(key)) return Result.string(new ArrayList<Vod>());
        List<Vod> list = new ArrayList<>();
        try {
            JSONObject params = new JSONObject();
            params.put("keywords", key.replace(" ", "+"));
            params.put("page", 1);
            params.put("per_page", 50);
            params.put("scope", 1);
            JSONObject data = new JSONObject(OkHttp.post(drive.searchApi(), params.toString(), drive.getHeader()).getBody()).getJSONObject("data");
            JSONArray array = data.has("content") ? data.getJSONArray("content") : data.optJSONArray("files");
            List<Item> items = array == null ? null : Item.arrayFrom(array.toString());
            if (items != null) {
                for (Item item : items) {
                    if (list.size() >= limitSearch) break;
                    String parent = item.getParent();
                    String id = drive.getName() + "$" + (parent.endsWith("/") ? parent : parent + "/") + item.getName();
                    list.add(new Vod(id, item.getDisplayName(), Image.FOLDER, drive.getName(), item.isFolder()));
                }
            }
        } catch (Exception ignored) {
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        String[] parts = id.split("@@@");
        Drive drive = getDrive(flag);
        String url = getRaw(drive, parts[0]);
        if (url.endsWith(".strm")) url = OkHttp.string(url).trim();
        List<Sub> subs = new ArrayList<>();
        if (parts.length > 1) {
            String dir = parts[0].contains("/") ? parts[0].substring(0, parts[0].lastIndexOf("/") + 1) : "/";
            subs.add(Sub.create().name(Util.removeExt(parts[1])).ext(Util.getExt(parts[1])).url(drive.rawUrl(dir + parts[1])));
        }
        return Result.get().url(url).subs(subs).string();
    }

    private String getRaw(Drive drive, String path) {
        try {
            JSONObject data = new JSONObject(post(drive, drive.getApi(), path, true)).getJSONObject("data");
            String raw = drive.isV3() ? data.optString("raw_url") : data.getJSONArray("files").getJSONObject(0).optString("url");
            return TextUtils.isEmpty(raw) ? drive.rawUrl(path) : raw;
        } catch (Exception e) {
            return drive.rawUrl(path);
        }
    }
}
