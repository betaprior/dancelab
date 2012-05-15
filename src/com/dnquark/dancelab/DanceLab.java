package com.dnquark.dancelab;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
//import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Chronometer;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.graphics.PorterDuff;


public class DanceLab extends Activity implements OnSharedPreferenceChangeListener {

    public static String DEVICE_ID;
    public static boolean DEBUG_FILENAME = false; 
    
    private static final String TAG = "DanceLab";  
    private final int MENU_PREFS=1, MENU_NTP_SYNC=2, MENU_CLEAR=3;
    private final int GROUP_DEFAULT=0;
    private static final int DIALOG_ERASE = 0, DIALOG_ACC_RATE = 1, DIALOG_GYRO_RATE = 2;
    private static final String EOL = String.format("%n");

    private TextView statusText;
    private TextView recordingFileText;
    private TextView ntpStatusText;
    private Button syncButton;
    
    private Chronometer chronometer;
    private DataLogger logger;
    private DLSoundRecorder soundrec;
    SharedPreferences prefs;
    FileManager fileManager;
    SntpClient ntpClient;

    private PowerManager pm;
    private PowerManager.WakeLock wl;
    
    private long ntpOffset = 0;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        getDeviceId();
        initComponents();
        updateFileList();
        
        new GetNtpTime().execute();
        Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(
                        "/sdcard/dancelab/", null));
    }

    private void initComponents() {
        fileManager = new FileManager(this);
        logger = new DataLogger(this);
        soundrec = new DLSoundRecorder(this);
        ntpClient = new SntpClient();

        statusText = (TextView) findViewById(R.id.recordingStatus1);
        recordingFileText = (TextView) findViewById(R.id.textViewStatusRecFile);
        ntpStatusText = (TextView) findViewById(R.id.textViewStatusNtp);
        setRecordingStatusText("Recording to: " + fileManager.getDataDir().getPath());
        syncButton = (Button) findViewById(R.id.syncButton1);
                
        chronometer = (Chronometer) findViewById(R.id.chronometer1);
        initializeChronometer();
        
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        prefs = PreferenceManager.getDefaultSharedPreferences(this); //
        prefs.registerOnSharedPreferenceChangeListener(this);
    }
    
    private void initializeChronometer() {
        chronometer.setOnChronometerTickListener(
                new Chronometer.OnChronometerTickListener(){
                    public void onChronometerTick(Chronometer chr) {
                        if (!logger.isActive()) return;
                        long ts = SystemClock.uptimeMillis();
                        String chrText = chr.getText().toString();
                        logger.metadataWrite(new String("chr: " + chrText + " " + Long.toString(ts) + EOL));
             
                    }}
                );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(GROUP_DEFAULT, MENU_PREFS, 0, "Preferences");
        menu.add(GROUP_DEFAULT, MENU_NTP_SYNC, 0, "NTP Sync");        
        menu.add(GROUP_DEFAULT, MENU_CLEAR, 0, "Clear Data");
        return super.onCreateOptionsMenu(menu);
    }
    
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        logger.setSensorRate(Sensor.TYPE_ACCELEROMETER, 
                samplingRatePrefStringDecode(prefs.getString("prefsAccelSamplingRate", "")));
        logger.setSensorRate(Sensor.TYPE_GYROSCOPE,
                samplingRatePrefStringDecode(prefs.getString("prefsGyroSamplingRate", "")));
        double thrPeakDet = Double.parseDouble(prefs.getString("prefsPeakDetectorThreshold", "0"));
    }

    private int samplingRatePrefStringDecode(String s) {
        if (s.equals("Normal"))
            return SensorManager.SENSOR_DELAY_NORMAL;
        else if (s.equals("Fast"))
            return SensorManager.SENSOR_DELAY_GAME;
        else if (s.equals("Max"))
            return SensorManager.SENSOR_DELAY_FASTEST;
        else
            return -1;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (logger.isActive()) return true;
        switch(item.getItemId()) {
        case MENU_PREFS:
            startActivity(new Intent(this, PrefsActivity.class));
            return true;
        case MENU_NTP_SYNC:
            new GetNtpTime().execute();
            return true;
        case MENU_CLEAR:
            showDialog(DIALOG_ERASE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
 
    private void getDeviceId() {
        String a_id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        // Log.d(TAG, "AID: " + a_id);
        char id[] = {'0', '0'};
        for (int i = 0; i < id.length; i++) {
            char c = a_id.charAt(i);
            boolean is_digit = ((int)(c - 'A') < 0);
            id[i] = is_digit ? (char)((c - '0') + 'g') : c;
        }
        DEVICE_ID = String.valueOf(id);
    }

     
    
       
    private AlertDialog.Builder eraseDataDialogBuilder() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure?")
            .setCancelable(false)
            .setPositiveButton("Erase all data", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        DanceLab.this.clearData();
                    }
                })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        return builder;
    }

    protected Dialog onPrepareDialog(int id) {
        Dialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        switch(id) {
        case DIALOG_ERASE:
            dialog = eraseDataDialogBuilder().create();
            break;
        default:
            dialog = null;
        }
        return dialog;
    }


    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        switch(id) {
        case DIALOG_ERASE:
            dialog = eraseDataDialogBuilder().create();
            break;
        default:
            dialog = null;
        }
        return dialog;
    }
       
    private void clearData() {
        fileManager.deleteFiles();
        updateFileList();
        displayToast("Erased all data");
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
            startRecording();
            break;
        case R.id.stopButton1:
            stopRecordingMaybe();
            break;
        case R.id.syncButton1:
            startRecording();
            startShockSensorSync();
            break;
        }
    }
    
    private void startRecording() {
        if (logger.isActive()) return;
        wl.acquire();
        fileManager.makeTsFilename();
        soundrec.start();
        logger.startLogging();
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
        statusText.setText("recording");         
        displayToast("Initiating recording");
        syncButton.setEnabled(false);
    }
    
    private void startShockSensorSync() {
        if (!logger.isActive()) return;
        logger.setPeakDetectorEnabled(true);
        syncButton.getBackground().setColorFilter(0xFFFF0000, PorterDuff.Mode.MULTIPLY);
    }
   
    public void finalizeShockSensorSync() {
        logger.setPeakDetectorEnabled(false);
        Log.d(TAG, "Setting button to green");
        syncButton.setEnabled(true);
        syncButton.getBackground().setColorFilter(0xFF00FF00, PorterDuff.Mode.MULTIPLY);
        syncButton.setEnabled(false);
    }
    

    int stopPressCounter = 0;
    long firstPressMs = 0;
    boolean stoppingMaybe = false;
    int NUM_STOP_PRESSES = 3;
    int STOP_PRESS_MS = 800;
    public void stopRecordingMaybe() {
        long now = SystemClock.uptimeMillis();
        if (!stoppingMaybe) {
            stopPressCounter = 1;
            firstPressMs = now;
            stoppingMaybe = true;
        } else {
            stopPressCounter++;
            if (stopPressCounter >= NUM_STOP_PRESSES) {
                stoppingMaybe = false;
                if ((int)(now - firstPressMs) < STOP_PRESS_MS) 
                    stopRecording();
            }
        }
    }

    public void stopRecording() {
        if (wl.isHeld())
            wl.release(); 
        logger.stopLogging();
        soundrec.stop();
        chronometer.stop();
        statusText.setText("stopped; " + logger.getRunInfo());
        updateFileList();
        syncButton.setEnabled(true);
        syncButton.getBackground().setColorFilter(null);
    }
   
    class GetNtpTime extends AsyncTask<Void, Void, Boolean> {
        private String NTP_SERVER = "pool.ntp.org";
        private int NTP_TIMEOUT_MS = 2000;
        private int RETRIES = 3;
        private int RETRY_INTERVAL = 500;
        
        @Override
        protected Boolean doInBackground(Void... v) { //
            // long ntptime, ntptimeref;
            for (int i = 0; i < RETRIES; i++) {
                if (ntpClient.requestTime(NTP_SERVER, NTP_TIMEOUT_MS)) {
                    ntpOffset = ntpClient.getClockOffset();
                    // Log.d(TAG, "Nano->ms time: " + Long.toString(System.nanoTime() / 1000000));
                    // Log.d(TAG, "NTPRef: " + Long.toString(ntpClient.getNtpTimeReference()));
                    return true;
                } else {
                    try {
                        Thread.sleep(RETRY_INTERVAL);
                    } catch (InterruptedException e) { }
                }
            }
            return false;
        }
            
        @Override
        protected void onProgressUpdate(Void... v) { 
            super.onProgressUpdate(v);
        }

        @Override
        protected void onPostExecute(Boolean success) { 
            if (success) {
                ntpStatusText.setText(getResources().getString(R.string.statusFieldNtp) 
                        + " " + Long.toString(ntpOffset) + " ms");
            } else {
                ntpStatusText.setText(getResources().getString(R.string.statusFieldNtp) + " unavailable");
                displayToast("Failed to get time from " + NTP_SERVER);
            }
        }

    }


    

    public void updateFileList() {
        ListView listView = (ListView) findViewById(R.id.filelist);
        List<String> filenames = fileManager.getFileListing();
        if (filenames == null) return;
        Collections.sort(filenames, Collections.reverseOrder());
        String tsDateEOL = ".*\\d{8}-\\d{6}$";
        List<String> filenamesFiltered = new ArrayList<String>();
        for (String l : filenames)
            if (l.matches(tsDateEOL))
                filenamesFiltered.add(l);
        ArrayAdapter<String> fileList =
            new ArrayAdapter<String>(this, R.layout.row, filenamesFiltered);

        listView.setAdapter(fileList);

    }
    
    public void setRecordingStatusText(String text) {
        recordingFileText.setText(text);
    }
    
    @Override
    public void onBackPressed() {
        if (logger.isActive())
            displayToast("Stop data logging before exiting");
    }
 
    private void displayToast(String msg) {
        Toast.makeText(getBaseContext(), msg, 
                Toast.LENGTH_SHORT).show();        
    }    

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.stopLogging();
        soundrec.release();
    } 
     
    
    public String getTimestamp() {
        Date dateNow = new Date ();
        SimpleDateFormat tsFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        return tsFormat.format(dateNow).toString();
    }
    
}
