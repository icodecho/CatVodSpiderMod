package com.github.catvod.bean.openlist;

import android.text.TextUtils;

import com.github.catvod.utils.Image;
import com.github.catvod.utils.Util;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class Item {

    private static final Pattern MEDIA = Pattern.compile("\\.(dff|dsf|mp3|aac|wav|wma|cda|flac|m4a|mid|mka|mp2|mpa|mpc|ape|ofr|ogg|ra|wv|tta|ac3|dts|tak|webm|wmv|mpeg|mov|ram|swf|mp4|avi|rm|rmvb|flv|mpg|mkv|m3u8|ts|3gp|asf|strm)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBTITLE = Pattern.compile("\\.(srt|ass|scc|stl|ttml)$", Pattern.CASE_INSENSITIVE);

    @SerializedName("name")
    private String name;
    @SerializedName(value = "parent", alternate = {"path"})
    private String parent;
    @SerializedName("type")
    private int type;
    @SerializedName("is_dir")
    private boolean isDir;
    @SerializedName("size")
    private long size;
    @SerializedName(value = "thumb", alternate = {"thumbnail"})
    private String thumb;
    @SerializedName(value = "updated_at", alternate = {"modified", "time_str"})
    private String updated;

    public static List<Item> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Item.class).getType();
        return new Gson().fromJson(str, listType);
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public String getParent() {
        return TextUtils.isEmpty(parent) ? "" : parent;
    }

    public long getSize() {
        return size;
    }

    public String getUpdated() {
        return TextUtils.isEmpty(updated) ? "" : updated;
    }

    public boolean isFolder() {
        return type == 1 || isDir;
    }

    public boolean isMedia() {
        return !isFolder() && MEDIA.matcher(getName()).find();
    }

    public boolean isSub() {
        return !isFolder() && SUBTITLE.matcher(getName()).find();
    }

    public String getPic() {
        return TextUtils.isEmpty(thumb) && isFolder() ? Image.FOLDER : thumb;
    }

    public String getDate() {
        try {
            String[] split = getUpdated().split("T");
            return split[0] + (split.length > 1 ? " " + split[1].split("Z|\\.")[0] : "");
        } catch (Exception e) {
            return "";
        }
    }

    public String getRemark() {
        String date = getDate();
        String day = date.contains(" ") || date.contains("T") ? date.split("[ T]")[0] : date;
        String shortDay = day.length() > 5 ? day.substring(5) : day;
        return shortDay + "\t" + Util.getSize(getSize());
    }

    public String getDisplayName() {
        return getName().replace("$", "").replace("#", "");
    }

    public boolean matches(String keyword) {
        return getName().toLowerCase(Locale.getDefault()).contains(keyword.toLowerCase(Locale.getDefault()));
    }
}
