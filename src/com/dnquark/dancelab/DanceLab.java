package com.dnquark.dancelab;


import android.app.Activity;
import android.os.Bundle;
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
        logger = new DataLogger(getApplicationContext(), fnameText);
        
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
		    logger.start();
			statusText.setText("recording");	        
		    displayToast("Initiating recording");
			break;
		case R.id.stopButton1:
		    logger.stop();
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
		 logger.stop();
	     } catch (NullPointerException e) {
		 Log.e(TAG," caught NPE");
	     }
	 }	
	    
	 
	    
}
