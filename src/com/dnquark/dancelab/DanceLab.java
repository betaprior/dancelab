package com.dnquark.dancelab;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
//import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;


public class DanceLab extends Activity {

	private static final String TAG = "DanceLab";  
	private static boolean DEBUG_FILENAME = true;

    private boolean appendTimestamps = false;
	private TextView statusText, fnameText;
	private CheckBox checkBoxTS;
	private FileManager fileManager;
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
        fileManager = new FileManager();
	logger = new DataLogger();
	updateFileList();
        
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
			updateFileList();
			break;
		case R.id.checkBoxTimestamps1:
			if (((CheckBox)view).isChecked()) 
				appendTimestamps = true;
			else
            	appendTimestamps = false;
		}
	}
	
	public void updateFileList() {
		ListView listView = (ListView) findViewById(R.id.filelist);
		List<String> filenames = fileManager.getFileListing();
		if (filenames == null) return;
    ArrayAdapter<String> fileList =
  	      new ArrayAdapter<String>(this, R.layout.row, filenames);

  	     listView.setAdapter(fileList);

  	    }

	
	private void displayToast(String msg) {
	         Toast.makeText(getBaseContext(), msg, 
	                 Toast.LENGTH_SHORT).show();        
	    }    

	 @Override
	 protected void onStop() {
	     super.onStop();
	     logger.stopLogging();
	 }	
	    
	 
	protected class FileManager {
	    public static final String SAVE_FILEDIR = "dancelab";
	    public static final String SAVE_FILENAME_BASE = "dancelab";
	    private static final String SAVE_FILENAME_BASE_TS = "dl";
	    private File root, storeDir;
	    private File[] files;
	    private List<String> filenames;
	    
	    public FileManager() {
	    root = Environment.getExternalStorageDirectory();
	    storeDir = new File(root, FileManager.SAVE_FILEDIR);
	    if (! storeDir.isDirectory()) 
		storeDir.mkdir();
	    	filenames = new ArrayList<String>();
	    }
	    public File getDataDir() { return storeDir; } 
	    public List<String> getFileListing() {
	    	files = storeDir.listFiles();
	    	for (int i=0; i < files.length; i++) 
	    		filenames.add(files[i].getName());
	    	return filenames;
	    }
	    	
	   
	}

	 
    protected class DataLogger implements SensorEventListener {
    private static final String TAG = "DanceLabLogger";
    private String metaFilename, saveFilename = FileManager.SAVE_FILENAME_BASE;
    private static final int NDIMS = 3;
    private float[] accelVals, gyroVals;
    private boolean loggingIsOn = false;
    
    private SensorManager mSensorManager;
	private HandlerThread mHandlerThread;
	private Handler handler;

    private int idx = 0;
    private File outfile, outfileMeta;
    private BufferedWriter outfileBWriter, outfileBWriterMeta;
    private Long tStart, tStop;
    
	public DataLogger() { 
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
	accelVals = new float[NDIMS];
	gyroVals = new float[NDIMS];
	for (int i = 0; i < NDIMS; i++)
	    accelVals[i] = gyroVals[i] = 0;


    }

	protected void startLogging() {
		mHandlerThread = new HandlerThread("sensorThread");
	    mHandlerThread.start();
	    handler = new Handler(mHandlerThread.getLooper());
	registerListeners();
	prepFileIO();
	tStart = System.currentTimeMillis();
	writeMetadataStart();
	loggingIsOn = true;
	}
    
    protected void stopLogging() {
    	if (!loggingIsOn) return;
	loggingIsOn = false;
	mHandlerThread.quit();
	unregisterListeners();
	tStop = System.currentTimeMillis();
	writeMetadataEnd();
	finalizeFileIO();
    }

    public void registerListeners() {
    	mSensorManager.registerListener(this,
    			mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
    			SensorManager.SENSOR_DELAY_FASTEST, handler);
    	//		    mSensorManager.registerListener(this,
    	//	                mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
    	//	                SensorManager.SENSOR_DELAY_FASTEST);
//    	mSensorManager.registerListener(this,	
//    			mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
//					SensorManager.SENSOR_DELAY_FASTEST, handler);
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
	long diffStartStop = tStop - tStart;
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
		File storeDir = fileManager.getDataDir();
	    if (storeDir.canWrite()) {
		saveFilename = FileManager.SAVE_FILENAME_BASE_TS + "-" + getTimestamp();
		if (DanceLab.DEBUG_FILENAME) saveFilename = "test";
		metaFilename = saveFilename + ".nfo";
		outfile = new File(storeDir, saveFilename);
		outfileMeta = new File(storeDir, metaFilename);
		fnameText.setText(outfile.getPath());
		outfileBWriter = new BufferedWriter(new FileWriter(outfile));
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
	Log.d(TAG, "idx = " + idx + "sensor: " + event.sensor + ", x: " + event.values[0] + ", y: " + event.values[1] + ", z: " + event.values[2]);
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
		accelMagnitude += accelVals[i] * accelVals[i];
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
