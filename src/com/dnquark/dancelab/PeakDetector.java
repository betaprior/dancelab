package com.dnquark.dancelab;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Handler.Callback;
import android.util.Log;

class PeakDetector implements Callback {
    /**
     * 
     */
    private final DanceLab danceLab;


    /**
     * @param dataLogger
     */
    PeakDetector(DanceLab danceLab) {
        this.danceLab = danceLab;
    }

    private static final String TAG = "DanceLab PeakDetector";
    private HandlerThread handlerThread;
    private Looper looper;
    private Handler handler;
    private int counter = 0;
    
    static class Datapoint {
        long t;
        float a;
        public Datapoint(long time, float acc) {
            t = time; 
            a = acc;
        }
    }
    
    public void init() {
        counter = 0;
        handlerThread = new HandlerThread("PeakDetectorThread");
        handlerThread.start();
        looper = handlerThread.getLooper();
        handler = new Handler(looper, this);
    }
    
    public void stop() {
        handlerThread.quit();
    }

    public Handler getHandler() {
        return handler;
    }

    @Override
    public boolean handleMessage(Message m) {
        counter++;
        //Log.d(TAG, "Receiving message " + Integer.toString(counter));
        if (counter > 120)
            danceLab.runOnUiThread(new Runnable() {
                    public void run() {
                        PeakDetector.this.danceLab.finalizeShockSensorSync();
                    }
                });
        return true;
    }
}
