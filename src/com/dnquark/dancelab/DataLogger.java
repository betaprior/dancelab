package com.dnquark.dancelab;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;


class DataLogger implements SensorEventListener {
    /**
     * 
     */
    final DanceLab danceLab;
    private static final String TAG = "DanceLab DataLogger";
    private static final int MSG_PEAKDET_DATAPOINT = 1, MSG_GRAPH_DATAPOINT = 2;
    private String metaFilename, saveFilename = FileManager.SAVE_FILENAME_BASE;
    private static final int NDIMS = 3;
    private static final int NANO_IN_MILLI = 1000000;
    private float[] accelVals, gyroVals;
    private float accelMagnitude;
    private boolean loggingIsOn = false, peakDetectionIsOn = false;
    private int accelSensorRate = SensorManager.SENSOR_DELAY_FASTEST, gyroSensorRate = SensorManager.SENSOR_DELAY_NORMAL;

    private SensorManager mSensorManager;
    private PeakDetector peakDet;

    private int idx, num_a, num_g;
    private File outfile, outfileMeta;
    private BufferedWriter outfileBWriter, outfileBWriterMeta;
    private Long tStart, tStop, tStart_epoch, tStop_epoch, tStart_ns, tStop_ns;
    private Long eventTimestampOffset, eventTime, eventTimeWrtRef, syncReference = 0L;

    public DataLogger(DanceLab danceLab) { 
        this.danceLab = danceLab;
        mSensorManager = (SensorManager) this.danceLab.getSystemService(Context.SENSOR_SERVICE);
        peakDet = new PeakDetector(danceLab);
        accelVals = new float[NDIMS];
        gyroVals = new float[NDIMS];
        for (int i = 0; i < NDIMS; i++)
            accelVals[i] = gyroVals[i] = 0;
        tStart = tStop = tStart_ns = tStop_ns = 0L;
        idx = num_a = num_g = 0;

    }

    public boolean isActive() { return loggingIsOn; }
    public boolean peakDetectorActive() { return peakDetectionIsOn; }

    protected void startLogging() {
        registerListeners();
        prepFileIO();
        tStart = SystemClock.uptimeMillis();
        tStart_ns = System.nanoTime();
        tStart_epoch = System.currentTimeMillis();
        // syncReference = tStart_epoch;
        setEventTimestampOffset();
        writeMetadataStart();
        loggingIsOn = true;
    }

    protected void stopLogging() {
        if (!loggingIsOn) return;
        loggingIsOn = false;
        unregisterListeners();
        tStop = SystemClock.uptimeMillis();
        tStop_ns = System.nanoTime();
        tStop_epoch = System.currentTimeMillis();
        writeMetadataEnd();
        finalizeFileIO();
    }

    public void setSensorRate(int sensorType, int r) {
        switch(sensorType) {
        case Sensor.TYPE_ACCELEROMETER:
            accelSensorRate = r;
            break;
        case Sensor.TYPE_ROTATION_VECTOR:
        case Sensor.TYPE_GYROSCOPE:
            gyroSensorRate = r;
            break;
        }
    }
    public int getSensorRate(int sensorType) {
        switch(sensorType) {
        case Sensor.TYPE_ACCELEROMETER:
            return accelSensorRate;
        case Sensor.TYPE_ROTATION_VECTOR:
        case Sensor.TYPE_GYROSCOPE:
            return gyroSensorRate;
        default:
            return 0;
        }
    }

    public void registerListeners() {
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                accelSensorRate);
        //      mSensorManager.registerListener(this,
        //                 mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
        //                 SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, 
                mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                gyroSensorRate);
    }

    public void unregisterListeners() {
        mSensorManager.unregisterListener(this);
    }

    public void writeMetadataStart() {
        try {
            outfileBWriterMeta.write("t_i " + Long.toString(tStart) 
                    + " " + Long.toString(tStart_ns));
            outfileBWriterMeta.newLine();
        } catch (IOException e) { }
    }

    public void metadataWrite(String s) {
        try {
            outfileBWriterMeta.write(s);
        } catch (IOException e) { }
    }

    private void setEventTimestampOffset() {
        eventTimestampOffset = danceLab.ntpClient.isValid() ? 
            (danceLab.ntpClient.getNtpTime() - danceLab.ntpClient.getNtpTimeReference())
            : (tStart_epoch - tStart_ns / NANO_IN_MILLI);
        syncReference = tStart_ns / NANO_IN_MILLI + eventTimestampOffset;
    }

    public void writeMetadataEnd() {
        long diffStartStop = tStop - tStart;
        float msPerSample = diffStartStop/(idx+1);
        try {
            outfileBWriterMeta.write("t_f " + Long.toString(tStop)  
                    + " " + Long.toString(tStop_ns));
            outfileBWriterMeta.newLine();
            outfileBWriterMeta.write("dt="+Long.toString(diffStartStop)+"; " 
                    + Float.toString(msPerSample) + " ms per sample");
        } catch (IOException e) { }
    }
    public String getRunInfo() {
        String fmt = "%.2f";
        long dt = tStop - tStart;
        String dt_s = String.format(fmt, (float)dt/1000);
        String samp_t_a = String.format(fmt, (float)dt / (num_a+1));
        String samp_t_g = String.format(fmt, (float)dt / (num_g+1));
        String res = "len=" + dt_s + " s; " + samp_t_a + " ms/acc smp";
        if (danceLab.haveGyro()) res += "; " + samp_t_g + " ms/gyr smp";
        return res;
    }

    private void finalizeFileIO() {
        try {
            outfileBWriterMeta.flush();
            outfileBWriterMeta.close();
            outfileBWriter.flush();
            outfileBWriter.close();
        } catch (IOException e) { }
    }

    private void prepFileIO() {
        try {
            File storeDir = danceLab.fileManager.getDataDir();
            if (storeDir.canWrite()) {
                saveFilename = danceLab.fileManager.getTsFilename();
                metaFilename = saveFilename + ".nfo";
                outfile = new File(storeDir, saveFilename);
                outfileMeta = new File(storeDir, metaFilename);
                outfileBWriter = new BufferedWriter(new FileWriter(outfile));
                outfileBWriterMeta = new BufferedWriter(new FileWriter(outfileMeta));
                danceLab.setRecordingStatusText("Recording to: " + storeDir.getPath());
            } else {
                danceLab.setRecordingStatusText("Dir not writable: " + storeDir.getPath());
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not write file " + e.getMessage());
        }
    }

    public void setSyncReference(long ref) { syncReference = ref; }

    public void onSensorChanged(SensorEvent event) {
        // Log.d(TAG, "idx = " + idx + "sensor: " + event.sensor + ", x: " + event.values[0] + ", y: " + event.values[1] + ", z: " + event.values[2]);
        synchronized (this) {     
            eventTime = event.timestamp / NANO_IN_MILLI + eventTimestampOffset;
            eventTimeWrtRef = eventTime - syncReference;
            switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                num_a++;
                accelMagnitude = 0;
                for (int i = 0; i < NDIMS; i++) {
                    accelVals[i] = event.values[i];
                    accelMagnitude += accelVals[i] * accelVals[i];
                }
                accelMagnitude = (float) Math.sqrt(accelMagnitude);
                if (peakDetectionIsOn)
                    passToPeakDetector();
                passToGraph();
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
            case Sensor.TYPE_GYROSCOPE:
                num_g++;
                for (int i = 0; i < NDIMS; i++)
                    gyroVals[i] = event.values[i];
                break;
            default:
                return;
            }
            if (loggingIsOn)
                writeValues(event);
        }
    }
    
    public void setPeakDetectorEnabled(final boolean pdSwitch) {
        if (peakDetectionIsOn == pdSwitch)
            return;
        else 
            peakDetectionIsOn = pdSwitch;
        if (pdSwitch)
            peakDet.init();
        else
            peakDet.stop();
    }
   
    public void passToGraph() {
        GraphView.Datapoint data = new GraphView.Datapoint(eventTime, accelMagnitude);
        danceLab.getGraphView().getHandler()
            .obtainMessage(MSG_GRAPH_DATAPOINT, data)
            .sendToTarget();
    } 
    
    public void passToPeakDetector() {
        // Log.d(TAG, "Sending message");
        PeakDetector.Datapoint data = new PeakDetector.Datapoint(eventTime, accelMagnitude);
        peakDet.getHandler()
            .obtainMessage(MSG_PEAKDET_DATAPOINT, data)
            .sendToTarget();
    }
    
    public void onAccuracyChanged(Sensor sensor, int accuracy) {  } 

    private void writeValues(SensorEvent event) {
        int i;
        try {
            // outfileBWriter.write("TIMESTAMP TESTS:" + Long.toString(event.timestamp) + " " + Long.toString(System.currentTimeMillis()) + " " + System.nanoTime() + " " + SystemClock.elapsedRealtime() + 
                    // " " + Long.toString(ntpClient.getNtpTimeReference()) + " " +  Long.toString(time));
            // outfileBWriter.newLine();
            outfileBWriter.write(Long.toString(eventTimeWrtRef) + ","
                    + Integer.toString(event.sensor.getType()) + ","
                    + Long.toString(eventTime) + ",");
            for (i = 0; i < NDIMS; i++)
                outfileBWriter.write(Float.toString(accelVals[i]) + ",");
            outfileBWriter.write(Float.toString(accelMagnitude) + ",");
            for (i = 0; i < NDIMS - 1; i++) 
                outfileBWriter.write(Float.toString(gyroVals[i]) + ",");
            outfileBWriter.write(Float.toString(gyroVals[i]));
            outfileBWriter.newLine();
        } catch (IOException e) { }
    }



}
