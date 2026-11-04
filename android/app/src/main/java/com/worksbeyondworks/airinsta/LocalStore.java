package com.worksbeyondworks.airinsta;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

final class LocalStore {
    private final SharedPreferences prefs;
    LocalStore(Context context) { prefs = context.getSharedPreferences("airinsta", Context.MODE_PRIVATE); }
    boolean flag(String key) { return prefs.getBoolean(key, false); }
    void flag(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
    ArrayList<JSONObject> list(String key) {
        ArrayList<JSONObject> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(key, "[]"));
            for (int i=0;i<array.length();i++) if (array.optJSONObject(i)!=null) items.add(array.optJSONObject(i));
        } catch (Exception ignored) { }
        return items;
    }
    void write(String key, ArrayList<JSONObject> values) {
        JSONArray array = new JSONArray();
        for (JSONObject item:values) array.put(item);
        prefs.edit().putString(key, array.toString()).apply();
    }
    boolean saved(String source) {
        for (JSONObject item:list("saved")) if (source.equals(item.optString("source"))) return true;
        return false;
    }
    private JSONObject entry(MediaApi.Post post) throws Exception {
        return new JSONObject().put("source",post.source).put("username",post.username)
            .put("caption",post.caption.substring(0,Math.min(200,post.caption.length())))
            .put("count",post.items.size()).put("time",System.currentTimeMillis());
    }
    void recent(MediaApi.Post post) {
        try {
            ArrayList<JSONObject> items=list("recent");
            items.removeIf(x->post.source.equals(x.optString("source")));
            items.add(0,entry(post));
            while(items.size()>50) items.remove(items.size()-1);
            write("recent",items);
        } catch (Exception ignored) { }
    }
    void toggleSaved(MediaApi.Post post) {
        try {
            ArrayList<JSONObject> items=list("saved");
            boolean removed=items.removeIf(x->post.source.equals(x.optString("source")));
            if(!removed) items.add(0,entry(post));
            while(items.size()>100) items.remove(items.size()-1);
            write("saved",items);
        } catch(Exception ignored) { }
    }
    void remove(String key,JSONObject entry) {
        ArrayList<JSONObject> items=list(key);
        items.removeIf(x->x.optString("source").equals(entry.optString("source")));
        write(key,items);
    }
    void addDownload(long id,String name,MediaApi.Post post,int index) {
        try {
            ArrayList<JSONObject> items=list("downloads");
            items.add(0,new JSONObject().put("id",id).put("name",name).put("source",post.source)
                .put("index",index).put("video",post.items.get(index).video).put("time",System.currentTimeMillis()));
            // Retain records without deleting files that Android has downloaded.
            while(items.size()>100) items.remove(items.size()-1);
            write("downloads",items);
        } catch(Exception ignored) { }
    }
}
