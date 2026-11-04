package com.worksbeyondworks.airinsta;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MediaApi {
    static final String API = "https://airinsta.vercel.app";
    static final class Item {
        final String url;
        final boolean video;
        Item(String url, boolean video) { this.url = url; this.video = video; }
    }
    static final class Post {
        String source = "", username = "", caption = "";
        final ArrayList<Item> items = new ArrayList<>();
        JSONObject json() throws Exception {
            JSONObject out = new JSONObject().put("source", source).put("username", username).put("caption", caption);
            JSONArray array = new JSONArray();
            for (Item item : items) array.put(new JSONObject().put("url", item.url).put("video", item.video));
            return out.put("items", array);
        }
        static Post restore(String value) throws Exception {
            JSONObject json = new JSONObject(value);
            Post p = new Post(); p.source = normalize(json.optString("source"));
            p.username = json.optString("username", ""); p.caption = json.optString("caption", "");
            JSONArray items = json.optJSONArray("items");
            if (items != null) for (int i=0; i<items.length() && i<100; i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && approved(item.optString("url"))) p.items.add(new Item(item.optString("url"), item.optBoolean("video")));
            }
            return p;
        }
    }
    static Post resolve(String raw) throws Exception {
        String source = normalize(raw);
        Object json = new JSONTokener(new String(readUrl(API + "/api/resolve?url=" + Uri.encode(source), 4*1024*1024), StandardCharsets.UTF_8)).nextValue();
        Post post = new Post(); post.source = source;
        String[] metadata = {"", ""};
        collect(json, post.items, new HashSet<>(), metadata, 0);
        post.username = metadata[0]; post.caption = metadata[1];
        if (post.items.isEmpty()) throw new Exception("No media was returned. The post may be private, expired or unsupported.");
        return post;
    }
    static Bitmap image(Context context, String url) throws Exception {
        byte[] bytes;
        if (url.startsWith("content://")) bytes = readStream(context.getContentResolver().openInputStream(Uri.parse(url)), 32*1024*1024);
        else {
            if (!approved(url)) throw new Exception("Invalid image URL.");
            try { bytes = readUrl(url, 32*1024*1024); }
            catch (Exception first) { bytes = readUrl(proxy(url, false, ""), 32*1024*1024); }
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inSampleSize = 1;
        while (bounds.outWidth/opts.inSampleSize > 2048 || bounds.outHeight/opts.inSampleSize > 2048) opts.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        if (bitmap == null) throw new Exception("Unable to decode this image.");
        return bitmap;
    }
    static String filename(Post post, int index) {
        Item item = post.items.get(index);
        String handle = post.username.replaceAll("[^A-Za-z0-9_-]", "");
        if (handle.isEmpty()) handle = "instagram";
        if (handle.length()>24) handle=handle.substring(0,24);
        String ext = item.video ? ".mp4" : ".jpg";
        try {
            String path = new URI(item.url).getPath().toLowerCase(Locale.ROOT);
            for (String candidate : new String[]{".png", ".webp", ".jpeg", ".mov"}) if (path.endsWith(candidate)) ext=candidate;
        } catch (Exception ignored) { }
        return "AirInsta_"+handle+"_"+System.currentTimeMillis()+"_"+(index+1)+ext;
    }
    static String normalize(String raw) throws Exception {
        String candidate = raw == null ? "" : raw.trim();
        Matcher match = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE).matcher(candidate);
        if (match.find()) candidate = match.group();
        else if (candidate.matches("(?i)^(www\\.)?(instagram\\.com|instagr\\.am)/.*")) candidate = "https://" + candidate;
        candidate = candidate.replaceAll("[).,]+$", "");
        if (candidate.length() > 2500) throw new Exception("This link is too long.");
        URI uri;
        try { uri = new URI(candidate); } catch (Exception e) { throw new Exception("Paste a valid Instagram reel or post link."); }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getPort() != -1 || uri.getUserInfo() != null ||
            !(host.equals("instagram.com") || host.equals("www.instagram.com") || host.equals("m.instagram.com") ||
              host.equals("instagr.am") || host.equals("www.instagr.am")))
            throw new Exception("Use a public Instagram link.");
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (!path.matches("(?i)^/(reel|reels|p|tv|stories|share)/[^/]+.*"))
            throw new Exception("Use a link to a reel, post, carousel or public story.");
        return "https://www.instagram.com" + path;
    }

    private static byte[] readUrl(String address, int limit) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "AirInsta-Android/1.1");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                String error = "The media service could not complete this request. Try again.";
                InputStream stream = connection.getErrorStream();
                if (stream != null) {
                    try { error = new JSONObject(new String(readStream(stream, 16384), StandardCharsets.UTF_8)).optString("message", error); }
                    catch (Exception ignored) { }
                }
                throw new Exception(error);
            }
            return readStream(connection.getInputStream(), limit);
        } finally { connection.disconnect(); }
    }
    private static byte[] readStream(InputStream stream, int limit) throws Exception {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n, total = 0;
            while ((n = in.read(buf)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Request canceled");
                total += n; if (total > limit) throw new Exception("The response is too large.");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
    private static boolean approved(String value) {
        try {
            URI uri = new URI(value);
            String h = uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == -1 && uri.getUserInfo() == null &&
                (h.equals("fbcdn.net") || h.endsWith(".fbcdn.net") ||
                 h.equals("cdninstagram.com") || h.endsWith(".cdninstagram.com") ||
                 h.equals("instagram.com") || h.endsWith(".instagram.com"));
        } catch (Exception e) { return false; }
    }
    private static String first(JSONObject obj, String... keys) {
        for (String key : keys) {
            Object value = obj.opt(key);
            if (value instanceof String && ((String) value).startsWith("https://")) return (String) value;
            if (value instanceof JSONObject) {
                String nested = ((JSONObject) value).optString("url", "");
                if (nested.startsWith("https://")) return nested;
            }
        }
        return "";
    }
    private static void collect(Object obj, ArrayList<Item> out, Set<String> seen, String[] meta, int depth) {
        if (obj == null || depth > 9 || out.size() >= 100) return;
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;
            for (int i = 0; i < array.length() && out.size() < 100; i++) collect(array.opt(i), out, seen, meta, depth + 1);
            return;
        }
        if (!(obj instanceof JSONObject)) return;
        JSONObject node = (JSONObject) obj;
        if (meta[0].isEmpty()) {
            meta[0] = node.optString("username", "");
            JSONObject owner = node.optJSONObject("owner");
            if (meta[0].isEmpty() && owner != null) meta[0] = owner.optString("username", "");
        }
        if (meta[1].isEmpty()) {
            Object cap = node.opt("caption");
            if (cap instanceof String) meta[1] = (String) cap;
            else if (cap instanceof JSONObject) meta[1] = ((JSONObject) cap).optString("text", "");
        }
        String vid = first(node, "video_url", "play_url", "videoUrl", "video");
        JSONArray versions = node.optJSONArray("video_versions");
        if (vid.isEmpty() && versions != null && versions.optJSONObject(0) != null) vid = first(versions.optJSONObject(0), "url");
        String image = first(node, "image_url", "display_url", "imageUrl", "display_src", "image");
        JSONObject imageVersions = node.optJSONObject("image_versions2");
        if (image.isEmpty() && imageVersions != null) {
            JSONArray candidates = imageVersions.optJSONArray("candidates");
            if (candidates != null && candidates.optJSONObject(0) != null) image = first(candidates.optJSONObject(0), "url");
        }
        String chosen = !vid.isEmpty() ? vid : image;
        boolean isVideo = !vid.isEmpty();
        if (chosen.isEmpty() && !node.has("username") && !node.has("caption")) {
            chosen = first(node, "download_url", "media_url", "url");
            isVideo = node.optBoolean("is_video") || chosen.matches("(?i).*\\.mp4(?:\\?.*)?$");
        }
        if (approved(chosen) && seen.add(chosen)) out.add(new Item(chosen, isVideo));
        for (String key : new String[]{"result","data","items","media","medias","carousel_media","resources",
                "slides","children","nodes","edges","node","edge_sidecar_to_children","edge_sidecar","carousel","attachments"}) {
            if (node.has(key)) collect(node.opt(key), out, seen, meta, depth + 1);
        }
    }

    static String proxy(String url, boolean download, String name) {
        return API + "/api/media?url=" + Uri.encode(url) +
            (download ? "&download=1&filename=" + Uri.encode(name) : "");
    }
}
