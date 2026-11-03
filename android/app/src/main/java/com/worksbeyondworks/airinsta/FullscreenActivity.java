package com.worksbeyondworks.airinsta;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

public final class FullscreenActivity extends Activity {
    private VideoView video;
    private int savedPosition;
    private boolean wasPlaying = true;
    private boolean triedProxy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (state != null) {
            savedPosition = state.getInt("position");
            wasPlaying = state.getBoolean("playing", true);
            triedProxy = state.getBoolean("proxy", false);
        }
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        video = new VideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        MediaController controller = new MediaController(this);
        controller.setAnchorView(root); video.setMediaController(controller);
        video.setOnPreparedListener(mp -> {
            if (savedPosition > 0) video.seekTo(savedPosition);
            if (wasPlaying) video.start();
            controller.show(3500);
        });
        video.setOnErrorListener((mp, what, extra) -> {
            if (!triedProxy) {
                triedProxy = true;
                video.setVideoURI(Uri.parse(getIntent().getStringExtra("proxy")));
            } else Toast.makeText(this, "Video unavailable. Please resolve the link again.", Toast.LENGTH_LONG).show();
            return true;
        });
        String address = getIntent().getStringExtra(triedProxy ? "proxy" : "url");
        if (address == null || address.isEmpty()) { finish(); return; }
        video.setVideoURI(Uri.parse(address));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("position", video.getCurrentPosition());
        state.putBoolean("playing", video.isPlaying());
        state.putBoolean("proxy", triedProxy);
        super.onSaveInstanceState(state);
    }

    @Override protected void onPause() {
        super.onPause();
        if (video != null && video.isPlaying()) {
            savedPosition = video.getCurrentPosition();
            wasPlaying = false;
            video.pause();
        }
    }

    @Override protected void onDestroy() {
        if (video != null) video.stopPlayback();
        super.onDestroy();
    }
}
