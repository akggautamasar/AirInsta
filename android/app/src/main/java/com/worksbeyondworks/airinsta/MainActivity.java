package com.worksbeyondworks.airinsta;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity extends Activity {
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Long, DownloadRow> downloadRows = new HashMap<>();
    private Ui ui;
    private LocalStore store;
    private DownloadManager downloads;
    private MediaApi.Post post;
    private int active, tab, generation;
    private String libraryMode = "saved";
    private boolean busy;
    private Future<?> resolveJob;
    private EditText input, search;
    private Button fetchButton, cancelButton, saveButton;
    private Button[] tabs;
    private ScrollView[] pages;
    private LinearLayout studioRoot, libraryRoot, downloadsRoot, result, libraryList, downloadsList;
    private FrameLayout stage;
    private TextView status, creator, caption, position, detail, hint;
    private VideoView video;
    private Button previous, next, downloadAll, fullScreen;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (tab == 2) refreshDownloads();
            handler.postDelayed(this, 1500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new LocalStore(this);
        downloads = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (state != null) {
            tab = state.getInt("tab"); active = state.getInt("active");
            try { post = MediaApi.Post.restore(state.getString("post", "")); } catch (Exception ignored) { }
        }
        buildUi();
        if (state != null) input.setText(state.getString("input", ""));
        else acceptShare(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); acceptShare(intent);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("input", input.getText().toString()); out.putInt("tab", tab); out.putInt("active", active);
        if (post != null) {
            try {
                String value = post.json().toString();
                if (value.length() < 180000) out.putString("post", value);
            } catch (Exception ignored) { }
        }
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume() { super.onResume(); handler.post(poll); }
    @Override protected void onPause() {
        handler.removeCallbacks(poll);
        if (video != null && video.isPlaying()) video.pause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        generation++; handler.removeCallbacksAndMessages(null); stopInline(); io.shutdownNow(); super.onDestroy();
    }

    private ScrollView page(LinearLayout content) {
        ScrollView out = new ScrollView(this); out.setFillViewport(true);
        content.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(24));
        out.addView(content); return out;
    }
    private void buildUi() {
        stopInline();
        ui = new Ui(this, store.flag("light"));
        LinearLayout root = ui.column(); root.setBackgroundColor(ui.bg);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = ui.row(); header.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(10));
        TextView brand = ui.text("airinsta  ✦", 25, ui.accent, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button theme = ui.button(ui.light ? "Dark" : "Light", false);
        theme.setContentDescription("Change appearance");
        header.addView(theme, new LinearLayout.LayoutParams(ui.dp(74), ui.dp(42)));
        theme.setOnClickListener(v -> {
            String draft = input.getText().toString();
            store.flag("light", !ui.light); buildUi(); input.setText(draft);
        });
        root.addView(header);
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        studioRoot = ui.column(); libraryRoot = ui.column(); downloadsRoot = ui.column();
        pages = new ScrollView[]{page(studioRoot), page(libraryRoot), page(downloadsRoot)};
        for (ScrollView page : pages) content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        buildStudio(); buildLibrary(); buildDownloads();
        LinearLayout navigation = ui.row();
        navigation.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(10));
        navigation.setBackgroundColor(ui.panel);
        tabs = new Button[3];
        String[] labels = {"Studio", "Library", "Downloads"};
        for (int i=0;i<3;i++) {
            final int target=i; tabs[i]=ui.button(labels[i], false);
            tabs[i].setContentDescription(labels[i] + " tab");
            ui.weight(navigation,tabs[i],i==0?0:8);
            tabs[i].setOnClickListener(v -> selectTab(target));
        }
        root.addView(navigation); setContentView(root);
        if (post != null && !post.items.isEmpty()) { input.setText(post.source); renderResult(); }
        if (busy) { setBusy(true); status.setText("Finding your media…"); }
        selectTab(tab);
    }
    private void buildStudio() {
        TextView eyebrow = ui.text("YOUR POCKET MEDIA STUDIO", 10, ui.accent, true);
        eyebrow.setLetterSpacing(.12f); ui.add(studioRoot,eyebrow,0);
        ui.add(studioRoot,ui.text("Find it. Watch it.\nKeep it.",30,ui.text,true),9);
        ui.add(studioRoot,ui.text("Share a public Instagram link or paste it below.",14,ui.muted,false),7);
        LinearLayout form=ui.card();ui.add(studioRoot,form,20);
        ui.add(form,ui.text("INSTAGRAM POST URL",10,ui.accent,true),0);
        input=new EditText(this);input.setSingleLine(false);input.setMaxLines(3);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextSize(14);input.setTextColor(ui.text);input.setHintTextColor(ui.muted);
        input.setHint("https://www.instagram.com/reel/...");
        input.setContentDescription("Instagram post URL");
        input.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));input.setBackground(ui.shape(ui.field,12));
        ui.add(form,input,10);
        LinearLayout actions=ui.row();ui.add(form,actions,12);
        Button paste=ui.button("Paste link",false), clear=ui.button("Clear",false);
        ui.weight(actions,paste,0);ui.weight(actions,clear,8);
        paste.setOnClickListener(v -> paste());
        clear.setOnClickListener(v -> {input.setText("");input.requestFocus();});
        fetchButton=ui.button("Get media  →",true);ui.add(form,fetchButton,10);
        fetchButton.setOnClickListener(v -> resolve(input.getText().toString()));
        cancelButton=ui.button("Cancel request",false);ui.add(form,cancelButton,8);cancelButton.setVisibility(View.GONE);
        cancelButton.setOnClickListener(v -> {
            generation++;if(resolveJob!=null)resolveJob.cancel(true);setBusy(false);status.setText("Request canceled.");
        });
        status=ui.text("",13,ui.accent,false);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.add(form,status,8);
        result=ui.card();result.setVisibility(View.GONE);ui.add(studioRoot,result,20);
        creator=ui.text("",18,ui.text,true);ui.add(result,creator,0);
        detail=ui.text("",11,ui.muted,false);ui.add(result,detail,5);
        stage=new FrameLayout(this);stage.setBackground(ui.shape(Color.BLACK,12));stage.setClipToOutline(true);
        LinearLayout.LayoutParams stageParams=new LinearLayout.LayoutParams(-1,ui.dp(300));stageParams.topMargin=ui.dp(14);result.addView(stage,stageParams);
        hint=ui.text("Full frame preview",11,ui.muted,false);hint.setGravity(Gravity.CENTER);ui.add(result,hint,8);
        LinearLayout nav=ui.row();ui.add(result,nav,10);
        position=ui.text("",13,ui.muted,true);nav.addView(position,new LinearLayout.LayoutParams(0,ui.dp(46),1));
        previous=ui.button("‹",false);next=ui.button("›",false);
        previous.setContentDescription("Previous media");next.setContentDescription("Next media");
        nav.addView(previous,new LinearLayout.LayoutParams(ui.dp(50),ui.dp(46)));
        LinearLayout.LayoutParams n=new LinearLayout.LayoutParams(ui.dp(50),ui.dp(46));n.leftMargin=ui.dp(8);nav.addView(next,n);
        previous.setOnClickListener(v -> showMedia(active-1));next.setOnClickListener(v -> showMedia(active+1));
        fullScreen=ui.button("Watch fullscreen",false);ui.add(result,fullScreen,8);
        fullScreen.setOnClickListener(v -> openMedia());
        LinearLayout tools=ui.row();ui.add(result,tools,10);
        saveButton=ui.button("Save link",false);Button share=ui.button("Share link",false);
        ui.weight(tools,saveButton,0);ui.weight(tools,share,8);
        saveButton.setOnClickListener(v -> {
            if(post==null)return;store.toggleSaved(post);updateSave();message(store.saved(post.source)?"Link saved":"Link removed from saved");
        });
        share.setOnClickListener(v -> {if(post!=null)shareText(post.source);});
        caption=ui.text("",13,ui.muted,false);caption.setTextIsSelectable(true);caption.setMaxLines(5);ui.add(result,caption,15);
        LinearLayout captionActions=ui.row();ui.add(result,captionActions,8);
        Button expand=ui.button("Expand caption",false),copy=ui.button("Copy caption",false);
        ui.weight(captionActions,expand,0);ui.weight(captionActions,copy,8);
        expand.setOnClickListener(v -> {
            boolean expanded=caption.getMaxLines()==Integer.MAX_VALUE;
            caption.setMaxLines(expanded?5:Integer.MAX_VALUE);expand.setText(expanded?"Expand caption":"Collapse caption");
        });
        copy.setOnClickListener(v -> {
            if(post==null||post.caption.isEmpty()){message("No caption provided");return;}
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Instagram caption",post.caption));
            message("Caption copied");
        });
        Button download=ui.button("Download this item ↓",true);ui.add(result,download,14);
        download.setOnClickListener(v -> {if(post!=null)enqueue(post,active);});
        downloadAll=ui.button("Download all items",false);ui.add(result,downloadAll,8);
        downloadAll.setOnClickListener(v -> {
            if(post==null)return;int added=0;
            for(int i=0;i<post.items.size();i++)if(enqueueInternal(post,i))added++;
            message(added+" item"+(added==1?"":"s")+" queued");
        });
        Button original=ui.button("Open on Instagram ↗",false);ui.add(result,original,8);
        original.setOnClickListener(v -> {
            if(post==null)return;
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(post.source)));}catch(Exception e){message("No app is available to open the link");}
        });
        ui.add(studioRoot,ui.text("Public links supported by the extractor. Save media you own or have permission to keep.",11,ui.muted,false),20);
        ui.add(studioRoot,ui.text("AirInsta 1.1.0 · WorksBeyondWorks",10,ui.muted,false),12);
    }
    private void buildLibrary() {
        ui.add(libraryRoot,ui.text("Your collection.",28,ui.text,true),0);
        ui.add(libraryRoot,ui.text("Saved and recent links stay on this device.",13,ui.muted,false),7);
        LinearLayout toggle=ui.row();ui.add(libraryRoot,toggle,18);
        Button saved=ui.button("Saved links",false),recent=ui.button("Recent links",false);
        ui.weight(toggle,saved,0);ui.weight(toggle,recent,8);
        saved.setOnClickListener(v -> {libraryMode="saved";renderLibrary();});
        recent.setOnClickListener(v -> {libraryMode="recent";renderLibrary();});
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);
        search.setTextColor(ui.text);search.setHintTextColor(ui.muted);search.setHint("Search creator, caption or link");
        search.setContentDescription("Search library");search.setBackground(ui.shape(ui.field,12));
        search.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));ui.add(libraryRoot,search,12);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int c){renderLibrary();}
            public void afterTextChanged(Editable e){}
        });
        libraryList=ui.column();ui.add(libraryRoot,libraryList,12);
    }
    private void renderLibrary() {
        if(libraryList==null)return;libraryList.removeAllViews();
        String filter=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        ArrayList<JSONObject> items=store.list(libraryMode);int shown=0;
        ui.add(libraryList,ui.text((libraryMode.equals("saved")?"SAVED":"RECENT")+" · "+items.size()+" links",11,ui.accent,true),0);
        for(JSONObject entry:items){
            if(!filter.isEmpty()&&!entry.toString().toLowerCase(Locale.ROOT).contains(filter))continue;
            shown++;
            LinearLayout card=ui.card();ui.add(libraryList,card,12);
            String user=entry.optString("username");
            ui.add(card,ui.text(user.isEmpty()?"Instagram post":"@"+user.replaceFirst("^@",""),16,ui.text,true),0);
            TextView link=ui.text(entry.optString("source"),11,ui.muted,false);link.setMaxLines(2);ui.add(card,link,5);
            TextView snippet=ui.text(entry.optString("caption"),12,ui.muted,false);snippet.setMaxLines(2);ui.add(card,snippet,8);
            LinearLayout actions=ui.row();ui.add(card,actions,12);
            Button open=ui.button("Open",true),remove=ui.button("Remove",false);
            ui.weight(actions,open,0);ui.weight(actions,remove,8);
            open.setOnClickListener(v -> resolve(entry.optString("source")));
            remove.setOnClickListener(v -> {store.remove(libraryMode,entry);renderLibrary();updateSave();});
        }
        if(shown==0)ui.add(libraryList,ui.text(filter.isEmpty()?"Your next discovery starts in Studio.":"No matching links.",15,ui.muted,false),24);
        if(libraryMode.equals("recent")&&!items.isEmpty()){
            Button clear=ui.button("Clear recent links",false);ui.add(libraryList,clear,20);
            clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Clear recent links?")
                .setMessage("Saved links and downloaded files are kept.")
                .setNegativeButton("Keep",null).setPositiveButton("Clear",(d,w)->{store.write("recent",new ArrayList<>());renderLibrary();}).show());
        }
    }
    private void buildDownloads() {
        ui.add(downloadsRoot,ui.text("Ready to keep.",28,ui.text,true),0);
        ui.add(downloadsRoot,ui.text("Track downloads, then open or share your files.",13,ui.muted,false),7);
        LinearLayout settings=ui.card();ui.add(downloadsRoot,settings,18);
        CheckBox wifi=new CheckBox(this);wifi.setText("Wi-Fi only for new downloads");wifi.setTextColor(ui.text);wifi.setChecked(store.flag("wifi"));
        ui.add(settings,wifi,0);wifi.setOnCheckedChangeListener((b,value)->store.flag("wifi",value));
        ui.add(settings,ui.text("Files are saved in Downloads / AirInsta. Android continues queued downloads in the background.",11,ui.muted,false),6);
        downloadsList=ui.column();ui.add(downloadsRoot,downloadsList,12);
    }
    private void selectTab(int selected) {
        tab=Math.max(0,Math.min(2,selected));
        for(int i=0;i<pages.length;i++){pages[i].setVisibility(i==tab?View.VISIBLE:View.GONE);tabs[i].setTextColor(i==tab?ui.accent:ui.muted);}
        if(tab!=0&&video!=null&&video.isPlaying())video.pause();
        if(tab==1)renderLibrary();if(tab==2)renderDownloads();
    }
    private void paste() {
        ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        ClipData clip=cb.getPrimaryClip();
        if(clip!=null&&clip.getItemCount()>0){
            CharSequence text=clip.getItemAt(0).coerceToText(this);input.setText(text==null?"":text);
        }else message("Copy an Instagram link first");
    }
    private void acceptShare(Intent intent) {
        if(intent!=null&&Intent.ACTION_SEND.equals(intent.getAction())){
            String value=intent.getStringExtra(Intent.EXTRA_TEXT);
            if(value!=null&&!value.trim().isEmpty())resolve(value);
        }
    }
    private void setBusy(boolean value) {
        busy=value;fetchButton.setEnabled(!value);fetchButton.setText(value?"Finding your media…":"Get media  →");
        cancelButton.setVisibility(value?View.VISIBLE:View.GONE);
    }
    private void resolve(String raw) {
        final String requested;
        try{requested=MediaApi.normalize(raw);}catch(Exception e){selectTab(0);status.setText(e.getMessage());return;}
        if(resolveJob!=null)resolveJob.cancel(true);
        selectTab(0);input.setText(requested);setBusy(true);status.setText("Finding your media…");result.setVisibility(View.GONE);stopInline();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
        final int request=++generation;
        resolveJob=io.submit(()->{
            try{
                MediaApi.Post resolved=MediaApi.resolve(requested);
                runOnUiThread(()->{
                    if(request!=generation||isFinishing())return;
                    post=resolved;active=0;store.recent(post);setBusy(false);status.setText("Link resolved ✓");renderResult();
                    if(tab==0)result.post(()->pages[0].smoothScrollTo(0,result.getTop()-ui.dp(12)));
                });
            }catch(Exception e){runOnUiThread(()->{
                if(request!=generation||isFinishing())return;setBusy(false);
                status.setText(e.getMessage()==null?"Unable to resolve this link. Try again.":e.getMessage());
            });}
        });
    }
    private void renderResult() {
        if(post==null||post.items.isEmpty())return;
        result.setVisibility(View.VISIBLE);creator.setText(post.username.isEmpty()?"Your media is ready ✦":"@"+post.username.replaceFirst("^@",""));
        detail.setText(post.items.size()+" item"+(post.items.size()==1?"":"s")+" · Public media");
        caption.setText(post.caption.isEmpty()?"No caption provided.":post.caption);caption.setMaxLines(5);
        updateSave();showMedia(Math.min(active,post.items.size()-1));
    }
    private void updateSave(){if(saveButton!=null&&post!=null)saveButton.setText(store.saved(post.source)?"Saved ✓":"Save link");}
    private void stopInline(){if(video!=null){video.stopPlayback();video=null;}}
    private void sizeStage(int width,int height) {
        if(width<=0||height<=0)return;
        int available=stage.getWidth()>0?stage.getWidth():getResources().getDisplayMetrics().widthPixels-ui.dp(68);
        int max=(int)(getResources().getDisplayMetrics().heightPixels*.60);
        LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)stage.getLayoutParams();
        p.height=(int)Math.min(max,Math.max(ui.dp(120),(double)available*height/width));stage.setLayoutParams(p);
    }
    private void showMedia(int index) {
        if(post==null||post.items.isEmpty())return;
        active=(index+post.items.size())%post.items.size();stopInline();stage.removeAllViews();
        MediaApi.Item item=post.items.get(active);
        position.setText((active+1)+" of "+post.items.size());
        previous.setEnabled(post.items.size()>1);next.setEnabled(post.items.size()>1);
        downloadAll.setVisibility(post.items.size()>1?View.VISIBLE:View.GONE);
        fullScreen.setText(item.video?"Watch fullscreen":"Open photo viewer");
        hint.setText(item.video?"Full frame preview · Tap play for video controls":"Tap the image to zoom");
        if(item.video){
            VideoView player=new VideoView(this);video=player;
            stage.addView(player,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
            MediaController controller=new MediaController(this);controller.setAnchorView(stage);player.setMediaController(controller);
            player.setOnPreparedListener(mp->{
                if(video!=player)return;sizeStage(mp.getVideoWidth(),mp.getVideoHeight());mp.seekTo(1);
                detail.setText(post.items.size()+" item"+(post.items.size()==1?"":"s")+" · "+mp.getVideoWidth()+" × "+mp.getVideoHeight()+" · "+Ui.time(mp.getDuration()));
            });
            final boolean[] retried={false};
            player.setOnErrorListener((mp,what,extra)->{
                if(video!=player)return true;
                if(!retried[0]){retried[0]=true;player.setVideoURI(Uri.parse(MediaApi.proxy(item.url,false,"")));}
                else message("Video unavailable. Resolve the link again or download it.");
                return true;
            });
            player.setVideoURI(Uri.parse(item.url));
        }else{
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription("Instagram photo "+(active+1));stage.addView(image,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
            image.setOnClickListener(v->openMedia());
            io.execute(()->{
                try{
                    Bitmap bitmap=MediaApi.image(this,item.url);
                    runOnUiThread(()->{
                        if(stage.indexOfChild(image)<0||isFinishing())return;
                        image.setImageBitmap(bitmap);sizeStage(bitmap.getWidth(),bitmap.getHeight());
                        detail.setText(post.items.size()+" item"+(post.items.size()==1?"":"s")+" · Photo");
                    });
                }catch(Exception e){runOnUiThread(()->{if(stage.indexOfChild(image)>=0)message("Preview unavailable. Try downloading this item.");});}
            });
        }
    }
    private void openMedia(){
        if(post==null||post.items.isEmpty())return;
        MediaApi.Item item=post.items.get(active);
        Intent intent=new Intent(this,item.video?FullscreenActivity.class:ImageActivity.class);
        intent.putExtra("url",item.url);intent.putExtra("title",post.username.isEmpty()?"AirInsta":"@"+post.username);
        if(item.video){intent.putExtra("proxy",MediaApi.proxy(item.url,false,""));intent.putExtra("position",video==null?0:video.getCurrentPosition());if(video!=null)video.pause();}
        startActivity(intent);
    }
    private void shareText(String value){
        Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value);
        startActivity(Intent.createChooser(share,"Share Instagram link"));
    }
    private void enqueue(MediaApi.Post source,int index){if(enqueueInternal(source,index))message("Download queued · See Downloads");else message("This item is already downloading");}
    private boolean enqueueInternal(MediaApi.Post source,int index){
        if(index<0||index>=source.items.size())return false;
        for(JSONObject old:store.list("downloads")){
            if(source.source.equals(old.optString("source"))&&old.optInt("index")==index){
                int state=query(old.optLong("id")).state;
                if(state==DownloadManager.STATUS_PENDING||state==DownloadManager.STATUS_RUNNING||state==DownloadManager.STATUS_PAUSED)return false;
            }
        }
        String name=MediaApi.filename(source,index);
        try{
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(MediaApi.proxy(source.items.get(index).url,true,name)));
            request.setTitle(name);request.setDescription("AirInsta media");
            request.setMimeType(mime(name,source.items.get(index).video));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverRoaming(false);
            if(store.flag("wifi")){request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);request.setAllowedOverMetered(false);}
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"AirInsta/"+name);
            long id=downloads.enqueue(request);store.addDownload(id,name,source,index);return true;
        }catch(Exception e){message("Unable to queue this download");return false;}
    }
    private static String mime(String name,boolean video){
        if(video)return name.endsWith(".mov")?"video/quicktime":"video/mp4";
        if(name.endsWith(".png"))return "image/png";if(name.endsWith(".webp"))return "image/webp";return "image/jpeg";
    }
    private static final class DownloadState{int state;long done,total;String message="Canceled or removed";}
    private DownloadState query(long id){
        DownloadState out=new DownloadState();
        try(Cursor cursor=downloads.query(new DownloadManager.Query().setFilterById(id))){
            if(cursor!=null&&cursor.moveToFirst()){
                out.state=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                out.done=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                out.total=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                if(out.state==DownloadManager.STATUS_SUCCESSFUL)out.message="Complete";
                else if(out.state==DownloadManager.STATUS_RUNNING)out.message="Downloading";
                else if(out.state==DownloadManager.STATUS_PENDING)out.message="Queued";
                else if(out.state==DownloadManager.STATUS_PAUSED)out.message="Waiting for network or retry";
                else out.message="Download failed · Retry to refresh the link";
            }
        }catch(Exception e){out.message="Unable to read download status";}
        return out;
    }
    private static final class DownloadRow{
        TextView status;ProgressBar progress;Button open,share,cancel,retry;
    }
    private void renderDownloads(){
        downloadsList.removeAllViews();downloadRows.clear();
        ArrayList<JSONObject> records=store.list("downloads");
        if(records.isEmpty()){ui.add(downloadsList,ui.text("Your downloads will appear here.",15,ui.muted,false),24);return;}
        for(JSONObject record:records){
            long id=record.optLong("id");LinearLayout card=ui.card();ui.add(downloadsList,card,12);
            TextView title=ui.text(record.optString("name"),13,ui.text,true);title.setMaxLines(2);ui.add(card,title,0);
            DownloadRow row=new DownloadRow();row.status=ui.text("",12,ui.muted,false);ui.add(card,row.status,8);
            row.progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);row.progress.setMax(100);ui.add(card,row.progress,8);
            LinearLayout actions=ui.row();ui.add(card,actions,12);
            row.open=ui.button("Open",true);row.share=ui.button("Share file",false);row.cancel=ui.button("Cancel",false);row.retry=ui.button("Retry",true);
            ui.weight(actions,row.open,0);ui.weight(actions,row.share,8);ui.weight(actions,row.cancel,0);ui.weight(actions,row.retry,0);
            row.open.setOnClickListener(v->openDownload(record,false));row.share.setOnClickListener(v->openDownload(record,true));
            row.cancel.setOnClickListener(v->{downloads.remove(id);refreshDownloads();});
            row.retry.setOnClickListener(v->retry(record,row.retry));
            downloadRows.put(id,row);
        }
        refreshDownloads();
    }
    private void refreshDownloads(){
        for(Map.Entry<Long,DownloadRow> entry:downloadRows.entrySet()){
            DownloadState state=query(entry.getKey());DownloadRow row=entry.getValue();
            boolean complete=state.state==DownloadManager.STATUS_SUCCESSFUL;
            boolean running=state.state==DownloadManager.STATUS_PENDING||state.state==DownloadManager.STATUS_RUNNING||state.state==DownloadManager.STATUS_PAUSED;
            String bytes=state.done>0?" · "+Ui.bytes(state.done)+(state.total>0?" / "+Ui.bytes(state.total):""):"";
            row.status.setText(state.message+bytes);
            row.progress.setVisibility(running?View.VISIBLE:View.GONE);row.progress.setIndeterminate(state.total<=0);
            if(state.total>0)row.progress.setProgress((int)Math.min(100,state.done*100/state.total));
            row.open.setVisibility(complete?View.VISIBLE:View.GONE);row.share.setVisibility(complete?View.VISIBLE:View.GONE);
            row.cancel.setVisibility(running?View.VISIBLE:View.GONE);row.retry.setVisibility(!complete&&!running?View.VISIBLE:View.GONE);
        }
    }
    private void retry(JSONObject record,Button button){
        button.setEnabled(false);button.setText("Refreshing…");
        io.execute(()->{
            try{
                MediaApi.Post fresh=MediaApi.resolve(record.optString("source"));int index=record.optInt("index");
                if(index>=fresh.items.size())throw new Exception("This item is no longer available.");
                runOnUiThread(()->{
                    if(isFinishing())return;
                    if(enqueueInternal(fresh,index)){
                        ArrayList<JSONObject> all=store.list("downloads");
                        all.removeIf(x->x.optLong("id")==record.optLong("id"));store.write("downloads",all);renderDownloads();
                    }else{button.setEnabled(true);button.setText("Retry");}
                });
            }catch(Exception e){runOnUiThread(()->{if(!isFinishing()){button.setEnabled(true);button.setText("Retry");message("Unable to refresh the link. Try opening it in Studio.");}});}
        });
    }
    private void openDownload(JSONObject record,boolean share){
        Uri uri=downloads.getUriForDownloadedFile(record.optLong("id"));
        if(uri==null){message("The file was moved or removed");return;}
        try{
            String type=mime(record.optString("name"),record.optBoolean("video"));
            if(share){
                Intent intent=new Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM,uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent,"Share downloaded media"));
            }else{
                Intent intent=new Intent(this,record.optBoolean("video")?FullscreenActivity.class:ImageActivity.class);
                intent.putExtra("url",uri.toString());intent.putExtra("title",record.optString("name"));startActivity(intent);
            }
        }catch(Exception e){message("Unable to open this file");}
    }
    private void message(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
}
