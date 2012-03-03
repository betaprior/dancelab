package com.dnquark.dancelab;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
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


public class DanceLab extends Activity implements SensorEventListener
{
	private static final String SAVE_FILEDIR = "dancelab";
	private static final String SAVE_FILENAME_BASE = "dancelab";
	private static final String SAVE_FILENAME_BASE_TS = "dl";
	private static final String TAG = "MovementLab";  
	private static final int BUF_SIZE = 1024 * 1024;
	private String saveFilename = SAVE_FILENAME_BASE;
	private TextView statusText, fnameText;
	private CheckBox checkBoxTS;
	private boolean appendTimestamps = false;
	private float   mAccelValues[] = new float[3];
//	private float accelBufX[] = new float[BUF_SIZE];
//    private float accelBufY[] = new float[BUF_SIZE];
//    private float accelBufZ[] = new float[BUF_SIZE];
//    private long timestamps[] = new long[BUF_SIZE];
    private int idx = 0;
    private File outfile;
    private FileWriter outfileWriter;
    private BufferedWriter outfileBWriter;
    private Long tStart, tStop;
	private SensorManager mSensorManager;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        statusText = (TextView) findViewById(R.id.recordingStatus1);
        fnameText = (TextView) findViewById(R.id.textViewFname1);
        checkBoxTS = (CheckBox) findViewById(R.id.checkBoxTimestamps1);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        
        Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(
                "/sdcard/movementlab/", null));
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
			writeStuffToFile();
			statusText.setText("recording");
		    mSensorManager.registerListener(this,
	                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
	                SensorManager.SENSOR_DELAY_FASTEST);
//		    mSensorManager.registerListener(this,
//	                mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
//	                SensorManager.SENSOR_DELAY_FASTEST);
		    mSensorManager.registerListener(this,
	                mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
	                SensorManager.SENSOR_DELAY_FASTEST);
	        
		    Toast.makeText(getBaseContext(), 
                    "Initiating recording", 
                    Toast.LENGTH_SHORT).show();
			break;
		case R.id.stopButton1:
			statusText.setText("stopped");
			mSensorManager.unregisterListener(this);
			tStop = System.currentTimeMillis();
			long diffStartStop = tStart - tStop;
			float msPerSample = diffStartStop/(idx+1);
			try {
	    		outfileBWriter.write("stopped @" + Long.toString(tStop));
	    		outfileBWriter.newLine();
	    		outfileBWriter.write("dt="+Long.toString(diffStartStop)+"; " 
	    				+ Float.toString(msPerSample) + " ms per sample");
	    		outfileBWriter.flush();
	    		outfileBWriter.close();
			} catch (IOException e) {
			}
			break;
		case R.id.checkBoxTimestamps1:
			if (((CheckBox)view).isChecked()) 
				appendTimestamps = true;
			else
            	appendTimestamps = false;
		}
	}
	
	public void writeStuffToFile() {
		try {
		    File root = Environment.getExternalStorageDirectory();
		    File storeDir = new File(root, SAVE_FILEDIR);
		    boolean makedir = false;
		    if (! storeDir.isDirectory()) {
		    	makedir = true;
		    }
		    if (makedir && ! storeDir.mkdir()) {
		    	fnameText.setText("cannot create dir: " + storeDir.getPath());
		    } else 
		    	if (storeDir.canWrite()){
		    		if (appendTimestamps)
		    			saveFilename = SAVE_FILENAME_BASE_TS + "-" + getTimestamp();
		    		else
		    			saveFilename = SAVE_FILENAME_BASE;
		    		outfile = new File(storeDir, saveFilename);
		    		fnameText.setText(outfile.getPath());
		    		outfileWriter = new FileWriter(outfile);
		    		outfileBWriter = new BufferedWriter(outfileWriter);
		    		tStart = System.currentTimeMillis();
		    		outfileBWriter.write("initialized @" + Long.toString(tStart));
		    		outfileBWriter.newLine();
		    	} else {
		    		fnameText.setText("dir not writable: " + storeDir.getPath());
		    	}
			} catch (IOException e) {
				Log.e(TAG, "Could not write file " + e.getMessage());
			}
	}
	
	private String getTimestamp() {
        Date dateNow = new Date ();
        SimpleDateFormat tsFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        return tsFormat.format(dateNow).toString();
	}
	
	 private void DisplayToast(String msg) {
	         Toast.makeText(getBaseContext(), msg, 
	                 Toast.LENGTH_SHORT).show();        
	    }    

	 @Override
	 protected void onStop() {
		 super.onStop();
		 try {
		 mSensorManager.unregisterListener(this);
		 
		 try {
			 outfileBWriter.flush();
			 outfileBWriter.close();
		} catch (IOException e) {
		}
		 } catch (NullPointerException e) {
			 Log.e(TAG," caught NPE");
		 }
	 }	
	    
	 public void onSensorChanged(SensorEvent event) {
		 Log.d(TAG, "sensor: " + event.sensor + ", x: " + event.values[0] + ", y: " + event.values[1] + ", z: " + event.values[2]);
		 synchronized (this) {			  
			 int sensorEventType = event.sensor.getType(); 
			 if (sensorEventType == Sensor.TYPE_ACCELEROMETER
					 || sensorEventType == Sensor.TYPE_ROTATION_VECTOR
					 || sensorEventType == Sensor.TYPE_GYROSCOPE) {
//				 timestamps[idx] = event.timestamp;
//				 accelBufX[idx] = event.values[0];
//				 accelBufY[idx] = event.values[1];
//				 accelBufZ[idx] = event.values[2];
				 try {
					outfileBWriter.write(
							  Integer.toString(idx++) + ","
							+ Integer.toString(sensorEventType) + ","
							+ Long.toString(event.timestamp) + ","
							+ Float.toString(event.values[0]) + ","
							+ Float.toString(event.values[1]) + ","
							+ Float.toString(event.values[2])
							);
					outfileBWriter.newLine();
				} catch (IOException e) {
				}
//				 for (int i=0 ; i<3 ; i++) {
//					 mAccelValues[i] = event.values[i];
//				 }	
			 }
		 }
	 }
	    
	 public void onAccuracyChanged(Sensor sensor, int accuracy) {	 } 
}
