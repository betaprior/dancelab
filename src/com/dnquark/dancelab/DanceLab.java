package com.dnquark.dancelab;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
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
    private DLSoundRecorder soundrec;

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
		soundrec = new DLSoundRecorder();
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
	    fileManager.makeTsFilename();
	    soundrec.start();
	    logger.startLogging();
	    statusText.setText("recording");	        
	    displayToast("Initiating recording");
	    break;
	case R.id.stopButton1:
	    logger.stopLogging();
	    soundrec.stop();
	    statusText.setText("stopped; " + logger.getRunInfo());
	    updateFileList();
	    break;
	case R.id.beepButton1:
	    beep();
	    break;
	case R.id.checkBoxTimestamps1:
	    if (((CheckBox)view).isChecked()) 
		appendTimestamps = true;
	    else
            	appendTimestamps = false;
	}
    }

    public void beep() {
	DLBeeper b = new DLBeeper();
	b.beep();
    }
    
    // public void beep() {
    // 	Thread bgdBeepThread = new Thread(new Runnable() {
    // 		public void run() {
    // 		    play_note();
    // 		}
    // 	    });
    // 	bgdBeepThread.start();
    // }
   
    // MediaPlayer m_mediaPlayer; 
    // private void play_note() {
    // 	int BEEP_DURATION = 2200;
    // 	m_mediaPlayer = MediaPlayer.create(this, R.raw.a4);
    // 	m_mediaPlayer.start();
    // 	try {
    // 	    Thread.sleep(BEEP_DURATION);
    // 	} catch (InterruptedException e) { }

    // } 

    protected class DLBeeper {
	private final int duration = 3; // seconds
	private final int sampleRate = 8000;
	private final int numSamples = duration * sampleRate;
	private final double sample[] = new double[numSamples];
	private final double freqOfTone = 440; // hz
	
	private final byte generatedSnd[] = new byte[2 * numSamples];
	
	Handler handler = new Handler();

	public void beep() {
	    // Use a new tread as this can take a while
	    final Thread thread = new Thread(new Runnable() {
		    public void run() {
			genTone();
			handler.post(new Runnable() {
				public void run() {
				    playSound();
				}
			    });
		    }
		});
	    thread.start();
	}

	private void genTone() {
	    // fill out the array
	    for (int i = 0; i < numSamples; ++i)
		sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
	    
	    // convert to 16 bit pcm sound array
	    // assumes the sample buffer is normalised.
	    int idx = 0;
	    for (final double dVal : sample) {
		// scale to maximum amplitude
		final short val = (short) ((dVal * 32767));
		// in 16 bit wav PCM, first byte is the low order byte
		generatedSnd[idx++] = (byte) (val & 0x00ff);
		generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
	    }
	}

	void playSound() {
	    final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
							 sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
							 AudioFormat.ENCODING_PCM_16BIT, 2*numSamples,
							 AudioTrack.MODE_STATIC);
	    audioTrack.write(generatedSnd, 0, generatedSnd.length);
	    audioTrack.play();
	}
    }

	public void updateFileList() {
		ListView listView = (ListView) findViewById(R.id.filelist);
		List<String> filenames = fileManager.getFileListing();
		if (filenames == null) return;
		Collections.sort(filenames, Collections.reverseOrder());
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
	     soundrec.release();
	 }	
	    
    public String getTimestamp() {
        Date dateNow = new Date ();
        SimpleDateFormat tsFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        return tsFormat.format(dateNow).toString();
    }
 
	protected class FileManager {
	    public static final String SAVE_FILEDIR = "dancelab";
	    public static final String SAVE_FILENAME_BASE = "dancelab";
	    private static final String SAVE_FILENAME_BASE_TS = "dl";
	    private File root, storeDir;
	    private File[] files;
	    private List<String> filenames;
	    private String currentFilename;

	    public FileManager() {
	    root = Environment.getExternalStorageDirectory();
	    storeDir = new File(root, FileManager.SAVE_FILEDIR);
	    if (! storeDir.isDirectory()) 
		storeDir.mkdir();
	    	filenames = new ArrayList<String>();
		currentFilename = "";
	    }
	    public File getDataDir() { return storeDir; } 
	    public String makeTsFilename() {
		currentFilename = SAVE_FILENAME_BASE_TS + "-" + getTimestamp();
		if (DanceLab.DEBUG_FILENAME) currentFilename = "test";
		return currentFilename;
	    }
	    public String getTsFilename() {
		if (currentFilename.equals(""))
		    return makeTsFilename();
		else
		    return currentFilename;
	    }

	    public List<String> getFileListing() {
	    	filenames.clear();
	    	files = storeDir.listFiles();
	    	for (int i = files.length - 1; i >= 0; i--) 
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

	private int idx, num_a, num_g;
    private File outfile, outfileMeta;
    private BufferedWriter outfileBWriter, outfileBWriterMeta;
    private Long tStart, tStop;
    
	public DataLogger() { 
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
	accelVals = new float[NDIMS];
	gyroVals = new float[NDIMS];
	for (int i = 0; i < NDIMS; i++)
	    accelVals[i] = gyroVals[i] = 0;
	tStart = tStop = 0L;
	idx = num_a = num_g = 0;

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
    	mSensorManager.registerListener(this,	
    			mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
					SensorManager.SENSOR_DELAY_NORMAL, handler);
    }
    
    public void unregisterListeners() {
    	mSensorManager.unregisterListener(this);
    }
    
    public void writeMetadataStart() {
	try {
	    outfileBWriterMeta.write("t_i" + Long.toString(tStart));
	    outfileBWriterMeta.newLine();
	} catch (IOException e) { }
    }

    public void writeMetadataEnd() {
	long diffStartStop = tStop - tStart;
	float msPerSample = diffStartStop/(idx+1);
	try {
	    outfileBWriterMeta.write("t_f " + Long.toString(tStop));
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
	    return "len=" + dt_s + " s; " + samp_t_a + " ms/acc smp; " + samp_t_g + " ms/gyr smp"; 
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
		saveFilename = fileManager.getTsFilename();
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
	// Log.d(TAG, "idx = " + idx + "sensor: " + event.sensor + ", x: " + event.values[0] + ", y: " + event.values[1] + ", z: " + event.values[2]);
	synchronized (this) {			  
	    switch (event.sensor.getType()) {
	    case Sensor.TYPE_ACCELEROMETER:
		num_a++;
		for (int i = 0; i < NDIMS; i++)
		    accelVals[i] = event.values[i];
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
    
	

    }	 
	    
    protected class DLSoundRecorder {
	
    private MediaRecorder recorder;
		
	public DLSoundRecorder() {
	    recorder = new MediaRecorder();
	    recorderSetSettings();
	}

	private void recorderSetSettings() {
	    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//	    recorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
//	    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
	    recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
	    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
	 //   recorder.setAudioEncodingBitRate(16);
	    recorder.setAudioSamplingRate(22050);
	}

	public void start() {
	    recorder.setOutputFile(fileManager.getDataDir() + "/" + fileManager.getTsFilename() + ".3gp");
	    try {
			recorder.prepare();
		} catch (IllegalStateException e) {
		} catch (IOException e) {
		}
	    recorder.start(); 
	}
	public void stop() {
	    recorder.stop();
	    recorder.reset();
	    recorderSetSettings();
	}
	public void release() {
	    recorder.reset(); 
	    recorder.release();
	}

    }
    
}
