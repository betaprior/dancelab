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
    private int n = 0;
    private double mean = 0, var = 0, sd = 0, x2sum = 0;
    
    static class Datapoint {
        long t;
        float a;
        public Datapoint(long time, float acc) {
            t = time; 
            a = acc;
        }
    }
    
    public void init() {
        n = 0; mean = 0; var = 0; sd = 0; x2sum = 0;
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
        n++;
        //Log.d(TAG, "Receiving message " + Integer.toString(n));
        int BURN_IN_PTS = 150;
        double thr_sigma = 1.9;
        double a = ((Datapoint)m.obj).a;
        long t = ((Datapoint)m.obj).t;
        double xbar = mean;
        x2sum += a * a;
        mean = ((n - 1) * xbar + a) / n;
        var = ((n * (x2sum - (n - 1) * xbar * xbar)) + 
                (n - 1) * Math.pow(xbar - a, 2)) / (n * n);
        
        sd = Math.sqrt(var);
        //Log.d(TAG + "dumbledore", Integer.toString(n) + "," + Long.toString(t) + "," + Double.toString(a) +
          //      "," + Double.toString(mean) + "," + Double.toString(sd));
        // Log.d(TAG, "a = " + Double.toString(a) + "; n = " + Double.toString(n) + "; Var = " + Double.toString(var) + "; x2s = " + Double.toString(x2sum) + "; mean = " + Double.toString(mean) + "; sd = " + Double.toString(sd) + "; thr = " + Double.toString(mean + sd * thr_sigma));
        if (n > BURN_IN_PTS && a > mean + sd * thr_sigma) {
            danceLab.runOnUiThread(new Runnable() {
                    public void run() {
                        PeakDetector.this.danceLab.finalizeShockSensorSync();
                    }
                });
        }
        return true;
    }
}
