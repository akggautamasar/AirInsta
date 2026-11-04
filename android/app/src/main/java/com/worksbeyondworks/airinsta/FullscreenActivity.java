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
import android.widget.SeekBar;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.VideoView;

public final class FullscreenActivity extends Activity {
    private VideoView video;
    private MediaPlayer player;
    private SeekBar timeline;
    private boolean seeking;
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
                String label=(playing?"Playing":"Paused")+" · "+Ui.time(video.getCurrentPosition())+" / "+Ui.time(video.getDuration())+" · "+width+" × "+height;
                if(!label.contentEquals(status.getText()))status.setText(label);
                if(!seeking&&video.getDuration()>0)timeline.setProgress((int)(video.getCurrentPosition()*1000L/video.getDuration()));
                play.setText(playing?"Pause":"Play");
            }
            handler.postDelayed(this,1500);
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

        footer=ui.column();footer.setPadding(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(10));
        status=ui.text("Loading video…",11,ui.muted,false);status.setContentDescription("Playback status");ui.add(footer,status,0);
        timeline=new SeekBar(this);timeline.setMax(1000);timeline.setContentDescription("Video seek bar");ui.add(footer,timeline,5);
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){}
            public void onStartTrackingTouch(SeekBar bar){seeking=true;}
            public void onStopTrackingTouch(SeekBar bar){if(player!=null&&video.getDuration()>0)video.seekTo((int)(video.getDuration()*bar.getProgress()/1000L));seeking=false;}
        });
        LinearLayout transport=ui.row();ui.add(footer,transport,5);
        Button rewind=ui.button("−10 seconds",false),forward=ui.button("+10 seconds",false);
        play=ui.button("Play",true);ui.weight(transport,rewind,0);ui.weight(transport,play,6);ui.weight(transport,forward,6);
        rewind.setOnClickListener(v->{if(player!=null)video.seekTo(Math.max(0,video.getCurrentPosition()-10000));});
        forward.setOnClickListener(v->{if(player!=null)video.seekTo(Math.min(video.getDuration(),video.getCurrentPosition()+10000));});
        LinearLayout actions=ui.row();ui.add(footer,actions,8);
        speedButton=ui.button(speedLabel(),false);loopButton=ui.button(loop?"Loop on":"Loop off",false);Button pip=ui.button("Mini player",false);
        ui.weight(actions,speedButton,0);ui.weight(actions,loopButton,6);ui.weight(actions,pip,6);
        play.setOnClickListener(v->{if(player==null)return;if(video.isPlaying()){video.pause();play.setText("Play");}else{video.start();play.setText("Pause");}});
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
            handler.removeCallbacks(tick);handler.post(tick);
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
        header.setVisibility(pip?View.GONE:View.VISIBLE);footer.setVisibility(pip?View.GONE:View.VISIBLE);
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("position",video.getCurrentPosition());state.putBoolean("playing",video.isPlaying());
        state.putBoolean("proxy",triedProxy);state.putBoolean("loop",loop);state.putFloat("speed",speed);super.onSaveInstanceState(state);
    }
    @Override protected void onPause(){
        if(video!=null&&!isInPictureInPictureMode()&&video.isPlaying()){savedPosition=video.getCurrentPosition();video.pause();}
        super.onPause();
    }
    @Override protected void onStop(){if(video!=null)video.pause();super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(video!=null)video.stopPlayback();super.onDestroy();}
}
