package com.dnquark.movementlab;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
//import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;


public class MovementLab extends Activity
{
	private static final String SAVE_FILEDIR = "movementlab";
	private static final String SAVE_FILENAME_BASE = "movementlab";
	private static final String SAVE_FILENAME_BASE_TS = "ml";
	private static final String TAG = "MovementLab";  
	private String saveFilename = SAVE_FILENAME_BASE;
	private TextView statusText, fnameText;
	private CheckBox checkBoxTS;
	private boolean appendTimestamps = true;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        statusText = (TextView) findViewById(R.id.recordingStatus1);
        fnameText = (TextView) findViewById(R.id.textViewFname1);
        checkBoxTS = (CheckBox) findViewById(R.id.checkBoxTimestamps1);

  /*      TextView tv = new TextView(this);
        tv.setText("Hello, Android");
        setContentView(tv);*/
    }
    
    
    /* invoked via android:onClick="myClickHandler" in the button entry of the layout XML */
	public void myClickHandler(View view) {
		switch (view.getId()) {
		case R.id.startButton1:
			writeStuffToFile();
			statusText.setText("recording");
			Toast.makeText(getBaseContext(), 
                    "Initiating recording", 
                    Toast.LENGTH_SHORT).show();
			break;
		case R.id.stopButton1:
			statusText.setText("stopped");
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
		    		File gpxfile = new File(storeDir, saveFilename);
		    		fnameText.setText(gpxfile.getPath());
		    		FileWriter gpxwriter = new FileWriter(gpxfile);
		    		BufferedWriter out = new BufferedWriter(gpxwriter);
		    		out.write("Hello world");
		    		out.close();
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

	
}
