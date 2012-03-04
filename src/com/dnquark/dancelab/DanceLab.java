package com.dnquark.dancelab;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
//import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;


public class DanceLab extends Activity {

	private static final String TAG = "DanceLab";  

    private boolean appendTimestamps = false;
	private TextView statusText, fnameText;
	private CheckBox checkBoxTS;
	private DataLogger logger;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        statusText = (TextView) findViewById(R.id.recordingStatus1);
        fnameText = (TextView) findViewById(R.id.textViewFname1);
        checkBoxTS = (CheckBox) findViewById(R.id.checkBoxTimestamps1);
        logger = new DataLogger();
        
        Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(
                "/sdcard/dancelab/", null));
      }
    /*
    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);
    }
    */

    /* invoked via android:onClick="myClickHandler" in the button entry of the layout XML */
	public void myClickHandler(View view) {
		switch (view.getId()) {
		case R.id.startButton1:
		    logger.startLogging();
			statusText.setText("recording");	        
		    displayToast("Initiating recording");
			break;
		case R.id.stopButton1:
		    logger.stopLogging();
			statusText.setText("stopped");
			break;
		case R.id.checkBoxTimestamps1:
			if (((CheckBox)view).isChecked()) 
				appendTimestamps = true;
			else
            	appendTimestamps = false;
		}
	}
	
	
	private void displayToast(String msg) {
	         Toast.makeText(getBaseContext(), msg, 
	                 Toast.LENGTH_SHORT).show();        
	    }    

	 @Override
	 protected void onStop() {
	     super.onStop();
	     try {
		 logger.stopLogging();
	     } catch (NullPointerException e) {
		 Log.e(TAG," caught NPE");
	     }
	 }	
	    
public class DataLogger implements SensorEventListener {
    private static final String TAG = "DanceLabLogger";
    public static final String SAVE_FILEDIR = "dancelab";
    public static final String SAVE_FILENAME_BASE = "dancelab";
    private static final String SAVE_FILENAME_BASE_TS = "dl";
    private String metaFilename, saveFilename = SAVE_FILENAME_BASE;
    private static final int NDIMS = 3;
    private float[] accelVals, gyroVals;
    
    private SensorManager mSensorManager;
    
    private int idx = 0;
    private File outfile, outfileMeta;
    private FileWriter outfileWriter, outfileWriterMeta;
    private BufferedWriter outfileBWriter, outfileBWriterMeta;
    private Long tStart, tStop;
    private boolean loggingIsOn = false;
    
    public DataLogger() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
	accelVals = new float[NDIMS];
	gyroVals = new float[NDIMS];
	for (int i = 0; i < NDIMS; i++)
	    accelVals[i] = gyroVals[i] = 0;
    }

    public void startLogging() {
	registerListeners();
	prepFileIO();
	tStart = System.currentTimeMillis();
	writeMetadataStart();
	loggingIsOn = true;
    }
    
    public void stopLogging() {
	loggingIsOn = false;
	unregisterListeners();
	tStop = System.currentTimeMillis();
	writeMetadataEnd();
	finalizeFileIO();
    }

    public void registerListeners() {
    	mSensorManager.registerListener(this,
    			mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
    			SensorManager.SENSOR_DELAY_FASTEST);
    	//		    mSensorManager.registerListener(this,
    	//	                mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
    	//	                SensorManager.SENSOR_DELAY_FASTEST);
    	mSensorManager.registerListener(this,	
    			mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
    			SensorManager.SENSOR_DELAY_FASTEST);
    }
    
    public void unregisterListeners() {
    	mSensorManager.unregisterListener(this);
    }
    
    public void writeMetadataStart() {
	try {
	    outfileBWriterMeta.write("initialized @" + Long.toString(tStart));
	    outfileBWriterMeta.newLine();
	} catch (IOException e) { }
    }

    public void writeMetadataEnd() {
	long diffStartStop = tStart - tStop;
	float msPerSample = diffStartStop/(idx+1);
	try {
	    outfileBWriterMeta.write("stopped @" + Long.toString(tStop));
	    outfileBWriterMeta.newLine();
	    outfileBWriterMeta.write("dt="+Long.toString(diffStartStop)+"; " 
				 + Float.toString(msPerSample) + " ms per sample");
	} catch (IOException e) { }
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
	    File root = Environment.getExternalStorageDirectory();
	    File storeDir = new File(root, SAVE_FILEDIR);
	    if (! storeDir.isDirectory()) 
		storeDir.mkdir();
	    if (storeDir.canWrite()) {
		saveFilename = SAVE_FILENAME_BASE_TS + "-" + getTimestamp();
		metaFilename = saveFilename + ".nfo";
		outfile = new File(storeDir, saveFilename);
		outfileMeta = new File(storeDir, metaFilename);
		fnameText.setText(outfile.getPath());
		outfileWriter = new FileWriter(outfile);
		outfileBWriter = new BufferedWriter(outfileWriter);
		outfileBWriterMeta = new BufferedWriter(new FileWriter(outfileMeta));
		fnameText.setText(" dir: " + storeDir.getPath());
	    } else {
		fnameText.setText("dir not writable: " + storeDir.getPath());
	    }
	} catch (IOException e) {
	    Log.e(TAG, "Could not write file " + e.getMessage());
	}
    }

    public void onSensorChanged(SensorEvent event) {
	Log.d(TAG, "sensor: " + event.sensor + ", x: " + event.values[0] + ", y: " + event.values[1] + ", z: " + event.values[2]);
	synchronized (this) {			  
	    switch (event.sensor.getType()) {
	    case Sensor.TYPE_ACCELEROMETER:
		for (int i = 0; i < NDIMS; i++)
		    accelVals[i] = event.values[i];
		break;
	    case Sensor.TYPE_ROTATION_VECTOR:
	    case Sensor.TYPE_GYROSCOPE:
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

    public void onAccuracyChanged(Sensor sensor, int accuracy) {	 } 
    
    private void writeValues(SensorEvent event) {
	float accelMagnitude;
	int i;
	try {
	    outfileBWriter.write(Integer.toString(idx++) + ","
				 + Integer.toString(event.sensor.getType()) + ","
				 + Long.toString(event.timestamp) + ",");
	    accelMagnitude = 0;
	    for (i = 0; i < NDIMS; i++) { 
		outfileBWriter.write(Float.toString(accelVals[i]) + ",");
		accelMagnitude += accelVals[i];
	    }
	    accelMagnitude = (float) Math.sqrt(accelMagnitude);
	    outfileBWriter.write(Float.toString(accelMagnitude) + ",");
	    for (i = 0; i < NDIMS - 1; i++) 
		outfileBWriter.write(Float.toString(gyroVals[i]) + ",");
	    outfileBWriter.write(Float.toString(gyroVals[i]));
	    outfileBWriter.newLine();
	} catch (IOException e) { }
    }
    
    private String getTimestamp() {
        Date dateNow = new Date ();
        SimpleDateFormat tsFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        return tsFormat.format(dateNow).toString();
    }
	

}	 
	    
}
