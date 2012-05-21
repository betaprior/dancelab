package com.dnquark.dancelab;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.RectShape;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

public class GraphView extends View implements OnTouchListener, Callback {
    private static final String TAG = "GraphView";

    List<Point> points = new ArrayList<Point>();
    Paint paint = new Paint();
    

    static class Datapoint {
        long t;
        float a;
        public Datapoint(long time, float acc) {
            t = time; 
            a = acc;
        }
    }
    private HandlerThread handlerThread;
    private Looper looper;
    private Handler handler;

    private Handler redrawHandler = new Handler();

    private float a;
    private long t;


//copypaste from sensors.java    
    private Bitmap  mBitmap;
    private Paint   mPaint = new Paint();
    private Canvas  mCanvas = new Canvas();
    private Path    mPath = new Path();
    private RectF   mRect = new RectF();
    private float   mLastValues[] = new float[3*2];
    private float   mOrientationValues[] = new float[3];
    private int     mColors[] = new int[3*2];
    private float   mLastX;
    private float   mScale[] = new float[2];
    private float   mYOffset;
    private float   mMaxX;
    private float   mSpeed = 1.0f;
    private float   mWidth;
    private float   mHeight;

    public GraphView(Context context) {
        super(context);
        init();
    }
    
    public GraphView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }
   
    protected ShapeDrawable square;
    protected ShapeDrawable circle;
    
    public void prepareDataHandlers() {
        handlerThread = new HandlerThread("PeakDetectorThread");
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
        a = ((Datapoint)m.obj).a;
        t = ((Datapoint)m.obj).t;
        if (mBitmap != null) {
            final Canvas canvas = mCanvas;
            final Paint paint = mPaint;
            float deltaX = mSpeed;
            float newX = mLastX + deltaX;
            paint.setColor(mColors[0]);
            final float v = mYOffset + a * mScale[0];
            canvas.drawLine(mLastX, mLastValues[0], newX, v, paint);
            mLastValues[0] = v;
            mLastX += mSpeed;
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
        mColors[0] = Color.argb(192, 255, 64, 64);
        mColors[1] = Color.argb(192, 64, 128, 64);
        mColors[2] = Color.argb(192, 64, 64, 255);
        mColors[3] = Color.argb(192, 64, 255, 255);
        mColors[4] = Color.argb(192, 128, 64, 128);
        mColors[5] = Color.argb(192, 255, 255, 64);
        
        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mRect.set(-0.5f, -0.5f, 0.5f, 0.5f);
        mPath.arcTo(mRect, 0, 180);

        this.setOnTouchListener(this);

        paint.setColor(Color.RED);
        paint.setAntiAlias(true);
         
        // // blue 60x60 square at 80, 120
        // square = new ShapeDrawable(new RectShape());
        // // set the color
        // square.getPaint().setColor(Color.BLUE);
        // // position it
        // square.setBounds(80, 120, 80+60, 120+60);
   
        // // greenish circle at 230, 220
        // circle = new ShapeDrawable(new OvalShape());
        // // set the color using opacity + RGB
        // circle.getPaint().setColor(0xff74AC23);
        // // give it a white shadow
        // // arguments are blur radius, x-offset, y-offset
        // circle.getPaint().setShadowLayer(10, 15, 15, Color.WHITE);
        // // position it
        // circle.setBounds(230, 220, 230+80, 220+80);
   
    } // end of init method
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        mCanvas.setBitmap(mBitmap);
        mCanvas.drawColor(0xFFFFFFFF);
        mYOffset = h * 0.5f;
        mScale[0] = - (h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
        mScale[1] = - (h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));
        mWidth = w;
        mHeight = h;
        if (mWidth < mHeight) {
            mMaxX = w;
        } else {
            mMaxX = w-50;
        }
        mLastX = mMaxX;
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    public void onDraw(Canvas canvas) {
        synchronized (this) {
            if (mBitmap != null) {
           
            
            final Paint paint = mPaint;
            final Path path = mPath;
            final int outer = 0xFFC0C0C0;
            final int inner = 0xFFff7010;
            
         

            if (mLastX >= mMaxX) {
                mLastX = 0;
                final Canvas cavas = mCanvas;
                final float yoffset = mYOffset;
                final float maxx = mMaxX;
                final float oneG = SensorManager.STANDARD_GRAVITY * mScale[0];
                paint.setColor(0xFFAAAAAA);
                cavas.drawColor(0xFFFFFFFF);
                cavas.drawLine(0, yoffset,      maxx, yoffset,      paint);
                cavas.drawLine(0, yoffset+oneG, maxx, yoffset+oneG, paint);
                cavas.drawLine(0, yoffset-oneG, maxx, yoffset-oneG, paint);
            }
            paint.setColor(mColors[0]);
            for (Point point : points) {
                mCanvas.drawCircle(point.x, point.y, 5, paint);
            }
            canvas.drawBitmap(mBitmap, 0, 0, null);
            
            }
        }
    }

    public boolean onTouch(View view, MotionEvent event) {
        // if(event.getAction() != MotionEvent.ACTION_DOWN)
        // return super.onTouchEvent(event);
        Point point = new Point();
        point.x = event.getX();
        point.y = event.getY();
        points.add(point);
        invalidate();
        Log.d(TAG, "point: " + point);
        return true;
    }
}

class Point {
    float x, y;

    @Override
    public String toString() {
        return x + ", " + y;
    }
}
