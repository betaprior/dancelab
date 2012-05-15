package com.dnquark.dancelab;

import java.io.IOException;

import android.media.MediaRecorder;

class DLSoundRecorder {

    /**
     * 
     */
    private final DanceLab danceLab;
    private MediaRecorder recorder;
    private boolean recordingIsOn = false;

    public DLSoundRecorder(DanceLab danceLab) {
        this.danceLab = danceLab;
        recorder = new MediaRecorder();
        recorderSetSettings();
    }

    private void recorderSetSettings() {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        //     recorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
        //     recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        //   recorder.setAudioEncodingBitRate(16);
        recorder.setAudioSamplingRate(22050);
    }

    public void start() {
        if (recordingIsOn) return;
        recorder.setOutputFile(this.danceLab.fileManager.getDataDir() + "/" + this.danceLab.fileManager.getTsFilename() + ".3gp");
        try {
            recorder.prepare();
        } catch (IllegalStateException e) {
        } catch (IOException e) {
        }
        recorder.start(); 
        recordingIsOn = true;
    }
    public void stop() {
        if (recordingIsOn)
            recorder.stop();
        recordingIsOn = false;
        recorder.reset();
        recorderSetSettings();
    }
    public void release() {
        recorder.reset(); 
        recorder.release();
    }

}