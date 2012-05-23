package com.dnquark.dancelab;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class GraphView extends View implements Callback {
    private static final String TAG = "GraphView";
    private static final int MAX_TRACES = 6;

    static class Datapoint {
        long t;
        float a;
        public Datapoint(long time, float acc) {
            t = time; 
            a = acc;
        }
    }
    private static final int COL_GRAY = 0xFFAAAAAA;
    private static final int COL_ORANGE = 0xFFFF7100;
    
    private HandlerThread handlerThread;
    private Looper looper;
    private Handler handler;

    private Handler redrawHandler = new Handler();

    private float a;
    
    private int     colors[] = new int[MAX_TRACES];
    private Bitmap  bitmap;
    private Canvas  canvas = new Canvas();
    private Paint   paint = new Paint();
    
    private float   lastX;
    private float   maxX;
    private float   offsetY;
    private float   lastValues[] = new float[MAX_TRACES];
    private float   accelScale;
    private float   accelOffsetRaw = - SensorManager.STANDARD_GRAVITY;
    private float   traceSpeed = 1.0f;

    private SharedPreferences prefs;
    private boolean drawSyncThr = false;

    public GraphView(Context c) {
        super(c);
        prefs = PreferenceManager.getDefaultSharedPreferences(c);
        init();
    }
    
    public GraphView(Context c, AttributeSet a) {
        super(c, a);
        prefs = PreferenceManager.getDefaultSharedPreferences(c);
        init();
    }
   
    public void prepareDataHandlers() {
        handlerThread = new HandlerThread("GraphViewThread");
        handlerThread.start();
        looper = handlerThread.getLooper();
        handler = new Handler(looper, this);
    }
    public void stopDataHandlers() {
        handlerThread.quit();
    }

    public Handler getHandler() {
        return handler;
    }

    @Override
    public boolean handleMessage(Message m) {
        synchronized (this) {
            if (m.obj == null) return true;
            a = ((Datapoint)m.obj).a;
            if (bitmap != null) {
                final Canvas canvas = this.canvas;
                final Paint paint = this.paint;
                float deltaX = traceSpeed;
                float newX = lastX + deltaX;
                paint.setColor(colors[0]);
                final float v = offsetY + (a + accelOffsetRaw) * accelScale;
                canvas.drawLine(lastX, lastValues[0], newX, v, paint);
                lastValues[0] = v;
                lastX += traceSpeed;
            }
            redrawHandler.post(new Runnable() {
                    @Override
                    public void run() { invalidate(); }
                });
            return true;
        }
    }

    protected void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        colors[0] = Color.argb(192, 255, 64, 64);
        colors[1] = Color.argb(192, 64, 128, 64);
        colors[2] = Color.argb(192, 64, 64, 255);
        colors[3] = Color.argb(192, 64, 255, 255);
        colors[4] = Color.argb(192, 128, 64, 128);
        colors[5] = Color.argb(192, 255, 255, 64);
        
        paint.setFlags(Paint.ANTI_ALIAS_FLAG);
    } 
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        canvas.setBitmap(bitmap);
        canvas.drawColor(Color.WHITE);
        offsetY = h * 0.5f;
        accelScale = - (h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
        maxX = w;
        lastX = maxX;
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    public void onDraw(Canvas canvas) {
        synchronized (this) {
            if (bitmap != null) {
           
            
            final Paint paint = this.paint;
            
            if (lastX >= maxX) {
                lastX = 0;
                final Canvas canv = this.canvas;
                final float yoffset = offsetY;
                final float maxx = maxX;
                final float oneG = SensorManager.STANDARD_GRAVITY * accelScale;
                canv.drawColor(Color.WHITE);
                if (drawSyncThr) drawSyncThreshold();
                paint.setColor(COL_GRAY);
                canv.drawLine(0, yoffset, maxx, yoffset, paint);
                canv.drawLine(0, yoffset+oneG, maxx, yoffset+oneG, paint);
                canv.drawLine(0, yoffset-oneG, maxx, yoffset-oneG, paint);
            }
            canvas.drawBitmap(bitmap, 0, 0, null);
            
            }
        }
    }
    
    private float getSyncThreshold() {
        return Float.parseFloat(prefs.getString("prefsPeakDetectorGAbsThreshold", "0"));
    }

    private void drawSyncThreshold() {
        paint.setColor(COL_ORANGE);
        paint.setPathEffect(new DashPathEffect(new float[] {5,5}, 1));
        final float yoffset = offsetY;
        final float thr = (getSyncThreshold() + accelOffsetRaw) * accelScale;
        canvas.drawLine(0, yoffset+thr, maxX, yoffset+thr, paint);
        paint.setPathEffect(null);
    }
    
    public void setDrawSyncThreshold(boolean v) { 
        drawSyncThr = v; 
        if (drawSyncThr) {
            drawSyncThreshold();
            invalidate();
        }
    }

}


