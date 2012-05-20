package com.dnquark.dancelab;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Handler.Callback;
import android.preference.PreferenceManager;
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
    //private static enum TriggerType { TRIGGER_ON_MAX, TRIGGER_ON_LEVEL};
    //private TriggerType triggerType = TriggerType.TRIGGER_ON_MAX;
    private HandlerThread handlerThread;
    private Looper looper;
    private Handler handler;
    private int n = 0;
    private double mean = 0,var = 0, sd = 0, x2sum = 0;
    private double thrSigma = 2.1, thrGAbsolute = 11.4;
    private double maxAccel = Double.MIN_VALUE;    
    private boolean peakTriggerSet = false;
    private long tprev;
    
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(danceLab);
        thrSigma = Double.parseDouble(prefs.getString("prefsPeakDetectorThreshold", Double.toString(thrSigma)));
        thrGAbsolute = Double.parseDouble(prefs.getString("prefsPeakDetectorGAbsThreshold", Double.toString(thrGAbsolute)));
        // Log.d(TAG, "Setting sigma threshold to " + Double.toString(thrSigma));
        // Log.d(TAG, "Setting abs threshold to " + Double.toString(thrGAbsolute));
        maxAccel = Double.MIN_VALUE;    
    }
    
    public void setPeakThresholdSigma(double sigma) { thrSigma = sigma; }
    
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
        double a = ((Datapoint)m.obj).a;
        long t = ((Datapoint)m.obj).t;
        double xbar = mean; // prev. mean
        mean = ((n - 1) * xbar + a) / n; // updated mean
        // Note: in this calculation, var is the "updated" var value with the nth datapoint included
        // xbar and x2sum are values computed over previous n - 1 datapoints.
        var = ((n * (x2sum - (n - 1) * xbar * xbar)) + 
                (n - 1) * Math.pow(xbar - a, 2)) / (n * n);

        x2sum += a * a; // update x2sum for next iteration
        
        sd = Math.sqrt(var);
        if (n < BURN_IN_PTS) return true;
        // Log.d(TAG, "a = " + Double.toString(a) + "; n = " + Double.toString(n) + "; Var = " + Double.toString(var) + "; x2s = " + Double.toString(x2sum) + "; mean = " + Double.toString(mean) + "; sd = " + Double.toString(sd) + "; thr = " + Double.toString(mean + sd * thr_sigma));
        
        if (a > maxAccel) {
            maxAccel = a;          
            // Log.d(TAG, "thresh:"+Double.toString(a)+","+Double.toString(sd));
            	
            peakTriggerSet = (a > mean + sd * thrSigma && a > thrGAbsolute);
            // if (peakTriggerSet) Log.d(TAG, "Trigger set on accel " + Double.toString(a) + " exceeding thresh " + Double.toString(mean + sd * thrSigma) + " and " + Double.toString(thrGAbsolute) + "; mean is " + Double.toString(mean) + " and sd is " + Double.toString(sd));
        } else
            if (peakTriggerSet) {
                peakTriggerSet = false;
                reportPeak(tprev);
                // Log.d(TAG, "thresh: reporting peak accel " + Double.toString(maxAccel));
                maxAccel = Double.MIN_VALUE;
            }     
            
        tprev = t;
        return true;
    }

    private void reportPeak(final long t) {
        danceLab.runOnUiThread(new Runnable() {
            public void run() {
                PeakDetector.this.danceLab.finalizeShockSensorSync(t);
            }
        });
    }
}
