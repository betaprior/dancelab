package com.dnquark.dancelab;


import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
//import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;


public class DanceLab extends Activity implements OnSharedPreferenceChangeListener {

    public static String DEVICE_ID;
    public static boolean DEBUG_FILENAME = false;
    public static final int MSG_CONNECTION=1, MSG_COMMAND=2, MSG_LOG=3;

    private static final String TAG = "DanceLab";
    private final int MENU_PREFS=1, MENU_NTP_SYNC=2, MENU_CLEAR=3, MENU_FILELIST=4;
    private static final int ON=1, OFF=0;
    private static final int V_BGD=0, V_GRAPH=1, V_FILELIST=2;
    private final int GROUP_DEFAULT=0;
    private static final int DIALOG_ERASE = 0;
    static final String EOL = String.format("%n");

    private TextView statusText;
    private TextView connectionStatusText;
    private TextView recordingFileText;
    private TextView ntpStatusText;
    private TextView appLogText;
    private GraphView graphView;
    private ListView fileListView;
    private View bgdImage;

    private Button syncButton, stopButton;

    private Chronometer chronometer;
    private DataLogger logger;
    private DLSoundRecorder soundrec;
    // TODO: figure out how to make this private
    DataStreamer dataStreamer;
    SharedPreferences prefs;
    SharedPreferences appDataPrefs;
    static final String DATA_PREFS_FILENAME = "datastorePrefs";
    FileManager fileManager;
    SntpClient ntpClient;

    private PowerManager pm;
    private PowerManager.WakeLock wl;

    private long ntpOffset = 0;
    private boolean haveGyro;

    private boolean buttonTimerDone = false;
    private static final int BUTTON_HOLD_INTERVAL = 2000;
    private Handler btnHoldHandler = new Handler();

    // this somewhat convoluted handler is here to appease the linter:
    // http://www.androiddesignpatterns.com/2013/01/inner-class-handler-memory-leak.html
    private static class StatusHandler extends Handler {
        private final WeakReference<DanceLab> activity;

        public StatusHandler(DanceLab activity) {
            this.activity = new WeakReference<DanceLab>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            DanceLab dl = activity.get();
            if (dl != null) {
                switch(msg.what) {
                case MSG_CONNECTION:
                    dl.setConnectionStatusText((String)msg.obj);
                    break;
                case MSG_COMMAND:
                    dl.processExternalCommand((String)msg.obj);
                    break;
                case MSG_LOG:
                    dl.processLogMessage((String)msg.obj);
                    break;
                default:
                    super.handleMessage(msg);
                }
            }
        }
    }
    private StatusHandler statusUpdateHandler;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        getDeviceId();
        initComponents();
        updateFileList();
        showInFrameView(V_BGD);

        new GetNtpTime().execute();
        Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(
                        "/sdcard/dancelab/", null));
    }

    private void initComponents() {
        // init views
        connectionStatusText = (TextView) findViewById(R.id.connectionStatus1);
        statusText = (TextView) findViewById(R.id.recordingStatus1);
        recordingFileText = (TextView) findViewById(R.id.textViewStatusRecFile);
        ntpStatusText = (TextView) findViewById(R.id.textViewStatusNtp);
        appLogText = (TextView) findViewById(R.id.appLog);
        syncButton = (Button) findViewById(R.id.syncButton1);
        stopButton = (Button) findViewById(R.id.stopButton1);
        chronometer = (Chronometer) findViewById(R.id.chronometer1);
        graphView = (GraphView) findViewById(R.id.graphDisplay1);
        fileListView = (ListView) findViewById(R.id.filelist);
        // bgdImage = findViewById(R.id.bgdImage1);

        // init prefs
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        // ok, why do I have both ways of getting prefs here? FML
        // TODO: read http://stackoverflow.com/questions/2614719/how-do-i-get-the-sharedpreferences-from-a-preferenceactivity-in-android
        appDataPrefs = getSharedPreferences(DATA_PREFS_FILENAME, MODE_PRIVATE);

        // init components
        statusUpdateHandler = new StatusHandler(this);
        initializeDataStreamerFromPrefs();

        fileManager = new FileManager(this);
        logger = new DataLogger(this);
        soundrec = new DLSoundRecorder(this);
        ntpClient = new SntpClient();


        setupStopButtonLongPress();
        setRecordingStatusText("Recording to: " + fileManager.getDataDir().getPath());

        initializeChronometer();


        haveGyro = this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE);

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
    }

    private void initializeDataStreamerFromPrefs() {
        int serverPort = Integer.parseInt(prefs.getString("prefsServerPort", "5678"));
        String serverIP = prefs.getString("prefsServerIP", "192.168.1.57");
        if (dataStreamer == null) {
            Log.d(TAG, "Creating new DataStreamer object");
            dataStreamer = new DataStreamer(serverIP, serverPort, statusUpdateHandler);
        } else {
            Log.d(TAG, "Reinitializing connection info on DataStreamer object");
            dataStreamer.setServerPort(serverPort);
            dataStreamer.setServerIP(serverIP);
            dataStreamer.reconnect();
        }
    }

    private View.OnTouchListener myButtonLongPressListener(final Runnable delayedAction) {
        return new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()){
                case MotionEvent.ACTION_DOWN:
                      buttonTimerDone = true;
                      displayToast("Keep holding...");
                      // Handler handler = new Handler();
                      // handler.postDelayed(delayedAction, BUTTON_HOLD_INTERVAL);
                      btnHoldHandler.postDelayed(delayedAction, BUTTON_HOLD_INTERVAL);
                    break;
                case MotionEvent.ACTION_MOVE:
                    break;
                case MotionEvent.ACTION_UP:
                    buttonTimerDone = false;
                    btnHoldHandler.removeCallbacks(delayedAction);
                    break;
                }
                return true;
            }
        };
    }

    private void setupSyncButtonLongPress() {
        syncButton.setOnTouchListener(myButtonLongPressListener(new Runnable() {
                public void run() {
                    if (buttonTimerDone) {
                        if (logger.peakDetectorActive()) {
                            displayToast("Stopping peak detector");
                            logger.setPeakDetectorEnabled(false);
                            graphView.setDrawSyncThreshold(false);
                            syncButton.setEnabled(false);
                            syncButton.setEnabled(true);
                            syncButton.getBackground().setColorFilter(null);
                        } else {
                            displayToast("Restarting peak detector");
                            startShockSensorSync();
                        }
                    }
                }
            }));
    }

    private void setupStopButtonLongPress() {
        stopButton.setOnTouchListener(myButtonLongPressListener(new Runnable() {
                    public void run() {
                        if (buttonTimerDone) { stopRecording(); }
                    }
            }));
    }

    private void initializeChronometer() {
        chronometer.setOnChronometerTickListener(
                new Chronometer.OnChronometerTickListener(){
                    public void onChronometerTick(Chronometer chr) {
                        if (!logger.isActive()) return;
                        long ts = SystemClock.uptimeMillis();
                        String chrText = chr.getText().toString();
                        logger.metadataWrite(new String("CHR: " + chrText + " " + Long.toString(ts) + EOL));

                    }}
                );
    }

    private void showInFrameView(int v) {
        switch (v) {
        case V_BGD:
            toggleGraphDisplay(OFF);
            toggleFileListDisplay(OFF);
            toggleBgdImage(ON);
            return;
        case V_GRAPH:
            toggleGraphDisplay(ON);
            toggleFileListDisplay(OFF);
            toggleBgdImage(OFF);
            return;
        case V_FILELIST:
            toggleGraphDisplay(OFF);
            toggleFileListDisplay(ON);
            toggleBgdImage(OFF);
            return;
        }
    }

    private void toggleFileListDisplay(int state) {
        fileListView.setVisibility(state == ON ? View.VISIBLE : View.GONE);
    }
    private void toggleFileListDisplay() {
        fileListView.setVisibility(fileListView.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
    }
    private void toggleBgdImage(int state) {
        // findViewById(R.id.bgdImage1).setVisibility(state == ON ? View.VISIBLE : View.GONE);
        findViewById(R.id.appLog).setVisibility(state == ON ? View.VISIBLE : View.GONE);
    }
    private void toggleBgdImage() {
        bgdImage.setVisibility(bgdImage.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
    }
    private void toggleGraphDisplay(int state) {
        graphView.setVisibility(state == ON ? View.VISIBLE : View.GONE);
    }
    private void toggleGraphDisplay() {
        graphView.setVisibility(graphView.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
    }
    public View getGraphView() { return graphView; }

    public StatusHandler getStatusUpdateHandler() {
        return statusUpdateHandler;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(GROUP_DEFAULT, MENU_PREFS, 0, "Preferences");
        menu.add(GROUP_DEFAULT, MENU_NTP_SYNC, 0, "NTP Sync");
        menu.add(GROUP_DEFAULT, MENU_FILELIST, 0, "File list");
        menu.add(GROUP_DEFAULT, MENU_CLEAR, 0, "Clear Data");
        return super.onCreateOptionsMenu(menu);
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        logger.setSensorRate(Sensor.TYPE_ACCELEROMETER,
                samplingRatePrefStringDecode(prefs.getString("prefsAccelSamplingRate", "")));
        logger.setSensorRate(Sensor.TYPE_GYROSCOPE,
                samplingRatePrefStringDecode(prefs.getString("prefsGyroSamplingRate", "")));
        if (key.equals("prefsServerIP") || key.equals("prefsServerPort")) {
            Log.d(TAG, "Connection info has changed, reinitializing DataStreamer");
            initializeDataStreamerFromPrefs();
        }
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
        switch (item.getItemId()) {
        case MENU_PREFS:
            startActivity(new Intent(this, PrefsActivity.class));
            return true;
        case MENU_FILELIST:
            if (fileListView.getVisibility() != View.VISIBLE)
                showInFrameView(V_FILELIST);
            else
                showInFrameView(V_GRAPH);
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


    /* invoked via android:onClick="myClickHandler" in the button entry of the layout XML */
    public void myClickHandler(View view) {
        switch (view.getId()) {
        case R.id.startButton1:
            startRecording();
            break;
        case R.id.syncButton1:
            startRecording();
            startShockSensorSync();
            // only want long-press behavior after the logger is running
            setupSyncButtonLongPress();
            break;
        }
    }

    public void processLogMessage(String msg) {
        appLogText.append(msg + "\n");
        // find the amount we need to scroll.  This works by
        // asking the TextView's internal layout for the position
        // of the final line and then subtracting the TextView's height
        // final int scrollAmount = appLogText.getLayout().getLineTop(appLogText.getLineCount()) - appLogText.getHeight();
        // // if there is no need to scroll, scrollAmount will be <=0
        // if (scrollAmount > 0)
        //     appLogText.scrollTo(0, scrollAmount);
        // else
        //     appLogText.scrollTo(0, 0);
    }

    public void processExternalCommand(String cmd) {
        Log.d(TAG, "EXTCMD: " + cmd);
        if (cmd.equals("start")) {
            startRecording();
        } else if (cmd.equals("stop")) {
            stopRecording();
            // wl.acquire(); // reacquire wakelock
            // Log.d(TAG, "Reacquiring wakelock");
        } else {
            Log.d(TAG, "Received unknown command: " + cmd);
        }
    }

    private void startRecording() {
        if (logger.isActive()) return;
        wl.acquire();
        Log.d(TAG, "Acquiring wakelock");
        fileManager.makeTsFilename();
        graphView.prepareDataHandlers();
        soundrec.start();
        logger.startLogging();
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.setTextColor(Color.WHITE);
        chronometer.start();
        statusText.setText("recording");
        showInFrameView(V_GRAPH);
    }

    private void startShockSensorSync() {
        if (!logger.isActive()) return;
        logger.setPeakDetectorEnabled(true);
        graphView.setDrawSyncThreshold(true);
        syncButton.setEnabled(false);
        syncButton.setEnabled(true);
        syncButton.getBackground().setColorFilter(0xFFFF0000, PorterDuff.Mode.MULTIPLY);
    }


    public void finalizeShockSensorSync(long timestamp, double accel) {
        logger.setPeakDetectorEnabled(false);
        logger.setOffsetReference(timestamp);
        graphView.setDrawSyncThreshold(false);
        logger.metadataWrite("SYNC: " + Long.toString(timestamp) +
                " " + Double.toString(accel) + EOL);
        syncButton.setEnabled(false);
        syncButton.setEnabled(true);
        syncButton.getBackground().setColorFilter(0xFF00FF00, PorterDuff.Mode.MULTIPLY);
    }


    public void stopRecording() {
        if (wl.isHeld()) {
            wl.release();
            Log.d(TAG, "Releasing wakelock");
        }
        if (!logger.isActive()) return;
        logger.stopLogging();
        soundrec.stop();
        chronometer.stop();
        chronometer.setTextColor(Color.GREEN);
        graphView.stopDataHandlers();
        statusText.setText(logger.getRunInfo());
        updateFileList();
        logger.setPeakDetectorEnabled(false);
        syncButton.setEnabled(false);
        syncButton.setEnabled(true);
        syncButton.getBackground().setColorFilter(null);
        syncButton.setOnTouchListener(null);
    }



    public void updateFileList() {
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

        fileListView.setAdapter(fileList);

    }

    public void setRecordingStatusText(String text) {
        recordingFileText.setText(text);
    }

    public void setConnectionStatusText(String text) {
        connectionStatusText.setText(text);
    }

    @Override
    public void onBackPressed() {
        if (logger.isActive())
            displayToast("Stop data logging before exiting");
        else
            super.onBackPressed();
    }

    public boolean haveGyro() { return haveGyro; }

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

    @Override
    protected void onResume() {
        super.onResume();
        if (!logger.isActive())
            showInFrameView(V_BGD);
    }

    public String getTimestamp() {
        Date dateNow = new Date ();
        SimpleDateFormat tsFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        return tsFormat.format(dateNow).toString();
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
                SharedPreferences.Editor appDataPrefsEditor = appDataPrefs.edit();
                appDataPrefsEditor.putLong("ntpSyncTimeEpoch", System.currentTimeMillis());
                appDataPrefsEditor.putLong("ntpSyncClockOffset", ntpClient.getClockOffset());
                appDataPrefsEditor.putLong("ntpTime", ntpClient.getNtpTime());
                appDataPrefsEditor.putLong("ntpSyncTimeRef", ntpClient.getNtpTimeReference());
                appDataPrefsEditor.commit();
            } else {
                ntpStatusText.setText(getResources().getString(R.string.statusFieldNtp) + " unavailable");
                displayToast("Failed to get time from " + NTP_SERVER);
            }
        }

    }

}
