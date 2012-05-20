package com.dnquark.dancelab;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;

public class PrefsActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);
        boolean haveGyro = this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE);
        PreferenceCategory sensorPrefsCat = (PreferenceCategory) findPreference("prefsCategorySensors");
        Preference gyroPrefs = getPreferenceScreen().findPreference("prefsGyroSamplingRate");
        if (!haveGyro) 
            sensorPrefsCat.removePreference(gyroPrefs);
    }
}
