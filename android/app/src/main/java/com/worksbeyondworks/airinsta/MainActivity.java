package com.worksbeyondworks.airinsta;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A native Android UI. The website is not loaded in a WebView. */
public final class MainActivity extends Activity {
    private static final String API = "https://airinsta.vercel.app";
    private static final int BG = Color.rgb(17, 13, 25);
    private static final int PANEL = Color.rgb(36, 27, 44);
    private static final int TEXT = Color.rgb(251, 240, 248);
    private static final int MUTED = Color.rgb(188, 171, 190);
    private static final int PINK = Color.rgb(250, 139, 185);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<Media> media = new ArrayList<>();
    private EditText input;
    private Button fetchButton, previous, next, downloadAll, fullScreen;
    private LinearLayout result;
    private FrameLayout stage;
    private TextView status, creator, caption, position;
    private VideoView video;
    private String link = "", username = "", postCaption = "";
    private int active = 0, generation = 0;

    static final class Media {
        final String url;
        final boolean video;
        Media(String url, boolean video) { this.url = url; this.video = video; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        if (state != null) input.setText(state.getString("input", ""));
        else acceptShare(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptShare(intent);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("input", input.getText().toString());
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        generation++;
        if (video != null) video.stopPlayback();
        io.shutdownNow();
        super.onDestroy();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextColor(color); view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(primary ? BG : TEXT);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(primary ? PINK : Color.rgb(69, 48, 69), 13));
        b.setMinHeight(dp(48)); b.setPadding(dp(12), 0, dp(12), 0);
        return b;
    }
    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top); parent.addView(view, params);
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }
    private void addWeighted(LinearLayout row, View child, int left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
        p.leftMargin = dp(left); row.addView(child, p);
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout root = column();
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(root); setContentView(scroll);

        TextView brand = text("airinsta  ✦", 27, PINK, true);
        add(root, brand, 0);
        TextView badge = text("YOUR NATIVE MEDIA STUDIO", 10, PINK, true);
        badge.setLetterSpacing(.13f); add(root, badge, 25);
        TextView title = text("Save the moments.\nKeep the magic.", 30, TEXT, true);
        title.setLineSpacing(dp(2), 1f); add(root, title, 10);
        TextView intro = text("Paste or share a public Instagram link to preview and save the media.", 14, MUTED, false);
        intro.setLineSpacing(dp(3), 1f); add(root, intro, 10);

        LinearLayout form = column();
        form.setPadding(dp(16), dp(16), dp(16), dp(16)); form.setBackground(rounded(PANEL, 20));
        add(root, form, 22);
        add(form, text("INSTAGRAM POST URL", 11, PINK, true), 0);
        input = new EditText(this);
        input.setSingleLine(false); input.setMaxLines(3);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextSize(14); input.setTextColor(TEXT); input.setHintTextColor(MUTED);
        input.setHint("https://www.instagram.com/reel/...");
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setBackground(rounded(Color.rgb(24, 18, 31), 12)); add(form, input, 10);
        LinearLayout inputActions = row(); add(form, inputActions, 12);
        Button paste = button("Paste link", false);
        Button recent = button("Last link", false);
        addWeighted(inputActions, paste, 0); addWeighted(inputActions, recent, 8);
        paste.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = cb.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence value = clip.getItemAt(0).coerceToText(this);
                input.setText(value == null ? "" : value.toString());
            } else message("Copy an Instagram link first.");
        });
        recent.setOnClickListener(v -> {
            String value = getPreferences(MODE_PRIVATE).getString("last_link", "");
            if (value.isEmpty()) message("No recent link yet."); else input.setText(value);
        });
        fetchButton = button("Get media  →", true); add(form, fetchButton, 10);
        fetchButton.setOnClickListener(v -> resolve());
        status = text("", 13, PINK, false); status.setLineSpacing(dp(3), 1f); add(form, status, 10);

        result = column(); result.setPadding(dp(16), dp(16), dp(16), dp(16));
        result.setBackground(rounded(PANEL, 20)); result.setVisibility(View.GONE); add(root, result, 22);
        creator = text("Your media is ready ✦", 18, TEXT, true); add(result, creator, 0);
        stage = new FrameLayout(this); stage.setBackground(rounded(Color.BLACK, 12));
        stage.setClipToOutline(true);
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(-1, dp(300));
        stageParams.topMargin = dp(14); result.addView(stage, stageParams);
        TextView hint = text("Full frame preview · Tap play for video controls", 11, MUTED, false);
        hint.setGravity(Gravity.CENTER); add(result, hint, 9);
        LinearLayout navigation = row(); add(result, navigation, 10);
        position = text("1 of 1", 13, MUTED, true);
        navigation.addView(position, new LinearLayout.LayoutParams(0, dp(48), 1));
        previous = button("‹", false); next = button("›", false);
        LinearLayout.LayoutParams arrow = new LinearLayout.LayoutParams(dp(52), dp(48));
        navigation.addView(previous, arrow);
        LinearLayout.LayoutParams arrow2 = new LinearLayout.LayoutParams(dp(52), dp(48)); arrow2.leftMargin = dp(8);
        navigation.addView(next, arrow2);
        previous.setOnClickListener(v -> showMedia(active - 1));
        next.setOnClickListener(v -> showMedia(active + 1));
        fullScreen = button("Watch fullscreen", false); add(result, fullScreen, 8);
        fullScreen.setOnClickListener(v -> {
            if (media.isEmpty() || !media.get(active).video) return;
            Intent open = new Intent(this, FullscreenActivity.class);
            open.putExtra("url", media.get(active).url);
            open.putExtra("proxy", proxy(media.get(active).url, false, ""));
            startActivity(open);
        });
        caption = text("", 13, MUTED, false); caption.setLineSpacing(dp(3), 1f); add(result, caption, 15);
        Button download = button("Download this item ↓", true); add(result, download, 16);
        download.setOnClickListener(v -> download(active));
        downloadAll = button("Download all items", false); add(result, downloadAll, 8);
        downloadAll.setOnClickListener(v -> {
            for (int i = 0; i < media.size(); i++) download(i);
        });
        Button original = button("Open original on Instagram ↗", false); add(result, original, 8);
        original.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))); }
            catch (Exception e) { message("No app is available to open this link."); }
        });
        TextView note = text("Download only content you own or have permission to save. Public links supported by the extractor work here.", 11, MUTED, false);
        note.setLineSpacing(dp(3), 1f); add(root, note, 20);
    }

    private void acceptShare(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && !shared.trim().isEmpty()) { input.setText(shared); resolve(); }
        }
    }

    private String validLink(String raw) throws Exception {
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

    private void resolve() {
        final String requested;
        try { requested = validLink(input.getText().toString()); }
        catch (Exception e) { status.setText(e.getMessage()); return; }
        link = requested; input.setText(requested); fetchButton.setEnabled(false);
        status.setText("Finding your media…"); result.setVisibility(View.GONE);
        if (video != null) { video.stopPlayback(); video = null; }
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
        final int request = ++generation;
        io.execute(() -> {
            try {
                byte[] data = readUrl(API + "/api/resolve?url=" + Uri.encode(requested), 4 * 1024 * 1024);
                Object json = new JSONTokener(new String(data, StandardCharsets.UTF_8)).nextValue();
                ArrayList<Media> found = new ArrayList<>();
                String[] metadata = new String[] {"", ""};
                collect(json, found, new HashSet<>(), metadata, 0);
                if (found.isEmpty()) throw new Exception("No media was returned. The post may be private, expired or unsupported.");
                runOnUiThread(() -> {
                    if (request != generation || isFinishing()) return;
                    media.clear(); media.addAll(found); username = metadata[0]; postCaption = metadata[1];
                    getPreferences(MODE_PRIVATE).edit().putString("last_link", requested).apply();
                    creator.setText(username.isEmpty() ? "Your media is ready ✦" : "@" + username.replaceFirst("^@", ""));
                    caption.setText(postCaption.isEmpty() ? "No caption provided." : postCaption);
                    result.setVisibility(View.VISIBLE); status.setText("Link resolved ✓");
                    fetchButton.setEnabled(true); showMedia(0);
                    result.post(() -> result.requestFocus());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (request != generation || isFinishing()) return;
                    fetchButton.setEnabled(true);
                    status.setText(e.getMessage() == null ? "Unable to resolve this link. Please retry." : e.getMessage());
                });
            }
        });
    }

    private static byte[] readUrl(String address, int limit) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "AirInsta-Android/1.0");
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
    private static void collect(Object obj, ArrayList<Media> out, Set<String> seen, String[] meta, int depth) {
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
        if (approved(chosen) && seen.add(chosen)) out.add(new Media(chosen, isVideo));
        for (String key : new String[]{"result","data","items","media","medias","carousel_media","resources",
                "slides","children","nodes","edges","node","edge_sidecar_to_children","edge_sidecar","carousel","attachments"}) {
            if (node.has(key)) collect(node.opt(key), out, seen, meta, depth + 1);
        }
    }

    private static String proxy(String url, boolean download, String name) {
        return API + "/api/media?url=" + Uri.encode(url) +
            (download ? "&download=1&filename=" + Uri.encode(name) : "");
    }
    private void resizeStage(int width, int height) {
        if (width <= 0 || height <= 0) return;
        int availableWidth = getResources().getDisplayMetrics().widthPixels - dp(72);
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * .62);
        int newHeight = (int) Math.min(maxHeight, Math.max(dp(120), (double) availableWidth * height / width));
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) stage.getLayoutParams();
        p.height = newHeight; stage.setLayoutParams(p);
    }
    private void showMedia(int index) {
        if (media.isEmpty()) return;
        active = (index + media.size()) % media.size();
        if (video != null) { video.stopPlayback(); video = null; }
        stage.removeAllViews();
        Media item = media.get(active);
        position.setText((active + 1) + " of " + media.size());
        previous.setEnabled(media.size() > 1); next.setEnabled(media.size() > 1);
        downloadAll.setVisibility(media.size() > 1 ? View.VISIBLE : View.GONE);
        fullScreen.setVisibility(item.video ? View.VISIBLE : View.GONE);
        if (item.video) {
            VideoView player = new VideoView(this);
            video = player;
            stage.addView(player, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            MediaController controller = new MediaController(this);
            controller.setAnchorView(stage); player.setMediaController(controller);
            player.setOnPreparedListener(mp -> {
                if (video != player) return;
                resizeStage(mp.getVideoWidth(), mp.getVideoHeight()); mp.seekTo(1);
                controller.show(3500);
            });
            final boolean[] retried = {false};
            player.setOnErrorListener((mp, what, extra) -> {
                if (video != player) return true;
                if (!retried[0]) { retried[0] = true; player.setVideoURI(Uri.parse(proxy(item.url, false, ""))); }
                else message("Video unavailable. Resolve the link again or download the item.");
                return true;
            });
            player.setVideoURI(Uri.parse(item.url));
        } else {
            ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            stage.addView(image, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            int shown = active;
            io.execute(() -> {
                try {
                    byte[] bytes;
                    try { bytes = readUrl(item.url, 24 * 1024 * 1024); }
                    catch (Exception first) { bytes = readUrl(proxy(item.url, false, ""), 24 * 1024 * 1024); }
                    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
                    while (bounds.outWidth / options.inSampleSize > 2048 || bounds.outHeight / options.inSampleSize > 2048) options.inSampleSize *= 2;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    if (bitmap == null) throw new Exception();
                    runOnUiThread(() -> {
                        if (active != shown || stage.indexOfChild(image) < 0 || isFinishing()) return;
                        image.setImageBitmap(bitmap); resizeStage(bitmap.getWidth(), bitmap.getHeight());
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> { if (stage.indexOfChild(image) >= 0) message("Preview unavailable. Try downloading the item."); });
                }
            });
        }
    }
    private void download(int index) {
        if (index < 0 || index >= media.size()) return;
        Media item = media.get(index);
        String handle = username.replaceAll("[^A-Za-z0-9_-]", "");
        if (handle.isEmpty()) handle = "instagram";
        if (handle.length() > 24) handle = handle.substring(0, 24);
        String extension = item.video ? ".mp4" : ".jpg";
        try {
            String path = new URI(item.url).getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".png")) extension = ".png";
            else if (path.endsWith(".webp")) extension = ".webp";
            else if (path.endsWith(".jpeg")) extension = ".jpeg";
        } catch (Exception ignored) { }
        String name = "AirInsta_" + handle + "_" + System.currentTimeMillis() + "_" + (index + 1) + extension;
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(proxy(item.url, true, name)));
            request.setTitle(name); request.setDescription("AirInsta media");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AirInsta/" + name);
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
            message("Download started. Check Downloads / AirInsta.");
        } catch (Exception e) { message("Couldn't start the download. Please try again."); }
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}
