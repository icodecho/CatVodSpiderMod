package com.github.catvod.bean.openlist;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Drive {

    @SerializedName("name")
    private String name;
    @SerializedName("server")
    private String server;
    @SerializedName("startPage")
    private String startPage;
    @SerializedName("realStartPage")
    private String realStartPage;
    @SerializedName("showAll")
    private Boolean showAll;
    @SerializedName("search")
    private Boolean search;
    @SerializedName("username")
    private String username;
    @SerializedName("password")
    private String password;
    @SerializedName("params")
    private JsonObject params;
    @SerializedName("headers")
    private Map<String, String> headers;

    private transient String token;
    private transient boolean v3;
    private transient String title;

    public static List<Drive> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Drive.class).getType();
        return new Gson().fromJson(str, listType);
    }

    public Drive(String name) {
        this.name = name;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public String getServer() {
        String host = TextUtils.isEmpty(server) ? "" : server;
        while (host.endsWith("/")) host = host.substring(0, host.length() - 1);
        return host;
    }

    public String getRoot() {
        if (!TextUtils.isEmpty(realStartPage)) return realStartPage;
        return TextUtils.isEmpty(startPage) ? "/" : startPage;
    }

    public boolean showAll() {
        return showAll != null && showAll;
    }

    public boolean searchable() {
        return search != null && search;
    }

    public String getTitle() {
        return TextUtils.isEmpty(title) ? getName() : title;
    }

    public boolean isV3() {
        return v3;
    }

    public Class toType() {
        return new Class(getName() + "$" + getRoot(), getName(), "1");
    }

    public String settingsApi() {
        return getServer() + "/api/public/settings";
    }

    public String loginApi() {
        return getServer() + "/api/auth/login";
    }

    public String listApi() {
        return getServer() + (v3 ? "/api/fs/list" : "/api/public/path");
    }

    public String getApi() {
        return getServer() + (v3 ? "/api/fs/get" : "/api/public/path");
    }

    public String searchApi() {
        return getServer() + (v3 ? "/api/fs/search" : "/api/public/search");
    }

    public String rawUrl(String path) {
        return getServer() + "/d" + Uri.encode(path, "/");
    }

    public synchronized boolean login() {
        try {
            if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) return false;
            JSONObject params = new JSONObject();
            params.put("username", username);
            params.put("password", password);
            JSONObject json = new JSONObject(OkHttp.post(loginApi(), params.toString()));
            if (json.optInt("code") != 200) return false;
            token = json.getJSONObject("data").optString("token");
            return !TextUtils.isEmpty(token);
        } catch (Exception e) {
            return false;
        }
    }

    public Drive check() {
        if (title != null) return this;
        try {
            JsonElement data = Json.parse(OkHttp.string(settingsApi(), getHeader())).getAsJsonObject().get("data");
            if (data != null && data.isJsonObject()) {
                v3 = true;
                JsonElement name = data.getAsJsonObject().get("title");
                if (name != null && !name.isJsonNull()) title = name.getAsString();
            } else {
                v3 = false;
            }
        } catch (Throwable e) {
            v3 = true;
        }
        title = title == null ? "" : title;
        login();
        return this;
    }

    public String findPass(String path) {
        String pass = TextUtils.isEmpty(password) ? "" : password;
        if (params == null) return pass;
        String best = null;
        for (String key : params.keySet()) {
            if (path.startsWith(key) && (best == null || key.length() > best.length())) best = key;
        }
        if (best == null) return pass;
        JsonElement value = params.get(best);
        if (value.isJsonObject()) {
            JsonElement item = value.getAsJsonObject().get("password");
            if (item != null && !item.isJsonNull()) return item.getAsString();
        } else if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        return pass;
    }

    public HashMap<String, String> getHeader() {
        HashMap<String, String> map = new HashMap<>();
        map.put("User-Agent", Util.CHROME);
        if (!TextUtils.isEmpty(token)) map.put("Authorization", token);
        if (headers != null) for (Map.Entry<String, String> entry : headers.entrySet()) map.put(entry.getKey(), entry.getValue());
        return map;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Drive)) return false;
        return getName().equals(((Drive) obj).getName());
    }
}
