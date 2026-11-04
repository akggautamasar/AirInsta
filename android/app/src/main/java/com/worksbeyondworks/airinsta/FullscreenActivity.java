package com.worksbeyondworks.airinsta;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.VideoView;

public final class FullscreenActivity extends Activity {
    private VideoView video;
    private MediaPlayer player;
    private MediaController controller;
    private LinearLayout header, footer;
    private TextView status;
    private Button play, speedButton, loopButton;
    private int savedPosition, width=9, height=16;
    private boolean wantPlay=true, triedProxy, loop;
    private float speed=1f;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){
        public void run(){
            if(video!=null&&player!=null){
                boolean playing=video.isPlaying();
                status.setText((playing?"Playing":"Paused")+" · "+Ui.time(video.getCurrentPosition())+" / "+Ui.time(video.getDuration())+" · "+width+" × "+height);
                play.setText(playing?"Pause":"Play");
            }
            handler.postDelayed(this,500);
        }
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        savedPosition=getIntent().getIntExtra("position",0);
        if(state!=null){
            savedPosition=state.getInt("position");wantPlay=state.getBoolean("playing",true);
            triedProxy=state.getBoolean("proxy");loop=state.getBoolean("loop");speed=state.getFloat("speed",1f);
        }
        Ui ui=new Ui(this,false);
        LinearLayout root=ui.column();root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;
        });
        header=ui.row();header.setPadding(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(6));
        Button back=ui.button("‹",false);back.setContentDescription("Back to AirInsta");
        header.addView(back,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(42)));back.setOnClickListener(v->finish());
        TextView title=ui.text(getIntent().getStringExtra("title")==null?"AirInsta":getIntent().getStringExtra("title"),14,ui.text,true);
        title.setMaxLines(1);title.setPadding(ui.dp(12),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        FrameLayout stage=new FrameLayout(this);
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        video=new VideoView(this);stage.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        controller=new MediaController(this);controller.setAnchorView(stage);video.setMediaController(controller);
        footer=ui.column();footer.setPadding(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(10));
        status=ui.text("Loading video…",11,ui.muted,false);status.setContentDescription("Playback status");ui.add(footer,status,0);
        LinearLayout actions=ui.row();ui.add(footer,actions,8);
        play=ui.button("Play",true);speedButton=ui.button(speedLabel(),false);loopButton=ui.button(loop?"Loop on":"Loop off",false);Button pip=ui.button("Mini player",false);
        ui.weight(actions,play,0);ui.weight(actions,speedButton,6);ui.weight(actions,loopButton,6);ui.weight(actions,pip,6);
        play.setOnClickListener(v->{if(player==null)return;if(video.isPlaying())video.pause();else video.start();});
        speedButton.setOnClickListener(v->{
            PopupMenu menu=new PopupMenu(this,speedButton);
            float[] rates={.5f,.75f,1f,1.25f,1.5f,2f};
            for(int i=0;i<rates.length;i++)menu.getMenu().add(0,i,i,rates[i]+"×");
            menu.setOnMenuItemClickListener(item->{speed=rates[item.getItemId()];applySpeed();speedButton.setText(speedLabel());return true;});
            menu.show();
        });
        loopButton.setOnClickListener(v->{loop=!loop;if(player!=null)player.setLooping(loop);loopButton.setText(loop?"Loop on":"Loop off");});
        pip.setEnabled(getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE));
        pip.setOnClickListener(v->miniPlayer());root.addView(footer);setContentView(root);
        video.setOnPreparedListener(mp->{
            player=mp;width=mp.getVideoWidth();height=mp.getVideoHeight();mp.setLooping(loop);
            applySpeed();if(savedPosition>0)video.seekTo(savedPosition);if(wantPlay)video.start();
            controller.show(3000);handler.removeCallbacks(tick);handler.post(tick);
        });
        video.setOnCompletionListener(mp->{wantPlay=false;play.setText("Play");});
        video.setOnErrorListener((mp,what,extra)->{
            player=null;
            String proxy=getIntent().getStringExtra("proxy");
            if(!triedProxy&&proxy!=null&&!proxy.isEmpty()){
                triedProxy=true;video.setVideoURI(Uri.parse(proxy));
            }else status.setText("Video unavailable. Return to Studio and refresh the link.");
            return true;
        });
        String address=getIntent().getStringExtra(triedProxy?"proxy":"url");
        if(address==null||address.isEmpty()){finish();return;}
        video.setVideoURI(Uri.parse(address));
    }
    private String speedLabel(){return "Speed "+speed+"×";}
    private void applySpeed(){
        if(player==null)return;
        try{
            boolean playing=video.isPlaying();
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));
            if(!playing)video.pause();
        }catch(Exception ignored){}
    }
    private void miniPlayer(){
        if(player==null||!getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))return;
        try{
            double ratio=width/(double)Math.max(1,height);
            Rational aspect=ratio<.42?new Rational(9,21):ratio>2.38?new Rational(21,9):new Rational(Math.max(1,width),Math.max(1,height));
            enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(aspect).build());
        }catch(Exception ignored){}
    }
    @Override protected void onUserLeaveHint(){super.onUserLeaveHint();if(video!=null&&video.isPlaying())miniPlayer();}
    @Override public void onPictureInPictureModeChanged(boolean pip,Configuration configuration){
        super.onPictureInPictureModeChanged(pip,configuration);
        header.setVisibility(pip?View.GONE:View.VISIBLE);footer.setVisibility(pip?View.GONE:View.VISIBLE);if(pip)controller.hide();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("position",video.getCurrentPosition());state.putBoolean("playing",video.isPlaying());
        state.putBoolean("proxy",triedProxy);state.putBoolean("loop",loop);state.putFloat("speed",speed);super.onSaveInstanceState(state);
    }
    @Override protected void onPause(){
        if(video!=null&&!isInPictureInPictureMode()&&video.isPlaying()){savedPosition=video.getCurrentPosition();video.pause();}
        super.onPause();
    }
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(video!=null)video.stopPlayback();super.onDestroy();}
}
