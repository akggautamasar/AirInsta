package com.worksbeyondworks.airinsta;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Ui ui=new Ui(this,false);
        LinearLayout root=ui.column();root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout top=ui.row();top.setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8));
        Button back=ui.button("‹ Back",false);top.addView(back);back.setOnClickListener(v->finish());
        TextView title=ui.text("  Photo viewer",16,ui.text,true);top.addView(title);root.addView(top);
        ZoomImage image=new ZoomImage();root.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=ui.text("Loading image…",12,ui.muted,false);hint.setPadding(ui.dp(18),ui.dp(10),ui.dp(18),ui.dp(12));root.addView(hint);setContentView(root);
        String url=getIntent().getStringExtra("url");
        if(url==null){finish();return;}
        io.execute(()->{
            try{
                Bitmap bitmap=MediaApi.image(this,url);
                runOnUiThread(()->{if(!isFinishing()){image.display(bitmap);hint.setText("Pinch to zoom · Drag to move · Double tap to reset");}});
            }catch(Exception e){runOnUiThread(()->hint.setText("Unable to display this image. Refresh the source link."));}
        });
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
    private final class ZoomImage extends android.widget.ImageView {
        private final Matrix transform=new Matrix();
        private final ScaleGestureDetector scaling;
        private final GestureDetector gestures;
        private Bitmap bitmap;
        private float base=1,zoom=1,lastX,lastY;
        ZoomImage(){
            super(ImageActivity.this);setScaleType(ScaleType.MATRIX);setContentDescription("Zoomable Instagram photo");
            scaling=new ScaleGestureDetector(ImageActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector detector){
                    float next=Math.max(1,Math.min(6,zoom*detector.getScaleFactor()));
                    float factor=next/zoom;zoom=next;transform.postScale(factor,factor,detector.getFocusX(),detector.getFocusY());setImageMatrix(transform);return true;
                }
            });
            gestures=new GestureDetector(ImageActivity.this,new GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDown(MotionEvent e){return true;}
                @Override public boolean onDoubleTap(MotionEvent e){fit();return true;}
            });
        }
        void display(Bitmap value){bitmap=value;setImageBitmap(value);post(this::fit);}
        private void fit(){
            if(bitmap==null||getWidth()==0||getHeight()==0)return;
            base=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());zoom=1;
            transform.reset();transform.postScale(base,base);
            transform.postTranslate((getWidth()-bitmap.getWidth()*base)/2,(getHeight()-bitmap.getHeight()*base)/2);setImageMatrix(transform);
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);fit();}
        @Override public boolean onTouchEvent(MotionEvent event){
            scaling.onTouchEvent(event);gestures.onTouchEvent(event);
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=event.getX();lastY=event.getY();}
            else if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&!scaling.isInProgress()&&event.getPointerCount()==1){
                if(zoom>1){transform.postTranslate(event.getX()-lastX,event.getY()-lastY);setImageMatrix(transform);}
                lastX=event.getX();lastY=event.getY();
            }
            return true;
        }
    }
}
