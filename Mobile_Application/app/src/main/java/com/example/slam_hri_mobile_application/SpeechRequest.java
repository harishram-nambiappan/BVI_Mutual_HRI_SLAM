package com.example.slam_hri_mobile_application;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpeechRequest extends AppCompatActivity {

    ImageView start_record, stop_record;

    TextView back;

    MediaRecorder mr;

    MediaPlayer mp;

    // Recording feedback UI
    View pulseRing1, pulseRing2, recordingDot;
    LinearLayout recordingStatus;
    TextView recordingTimer;

    boolean isRecording = false;
    final List<Animator> animators = new ArrayList<>();
    final Handler timerHandler = new Handler();
    long recordStartMs;

    // Carried through from the image flow (null for speech-only).
    String imagePath;
    String description;

    final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            int totalSec = (int) ((System.currentTimeMillis() - recordStartMs) / 1000);
            recordingTimer.setText(String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60));
            timerHandler.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_request);

        getSupportActionBar().hide();

        start_record = (ImageView) findViewById(R.id.imageView);

        stop_record = (ImageView) findViewById(R.id.imageView2);

        back = (TextView) findViewById(R.id.textView7);

        imagePath = getIntent().getStringExtra("image_path");
        description = getIntent().getStringExtra("description");

        pulseRing1 = findViewById(R.id.pulseRing1);
        pulseRing2 = findViewById(R.id.pulseRing2);
        recordingDot = findViewById(R.id.recordingDot);
        recordingStatus = (LinearLayout) findViewById(R.id.recordingStatus);
        recordingTimer = (TextView) findViewById(R.id.recordingTimer);

        if(ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mr = new MediaRecorder(getApplicationContext());
        }

        mp = new MediaPlayer();

        start_record.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (isRecording) {
                    return;
                }
                try {
                    mr.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    // AAC (not DEFAULT/AMR) so the .mp4 is playable everywhere,
                    // including macOS afplay on the desktop receiver.
                    mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    mr.setAudioSamplingRate(44100);
                    mr.setAudioEncodingBitRate(128000);
                    mr.setOutputFile(getFilesDir()+"/slam_hri_request_sample_1.mp4");
                    mr.prepare();
                    mr.start();
                    startRecordingUi();
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
        });

        stop_record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRecording) {
                    try {
                        mr.stop();
                        mr.release();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    stopRecordingUi();
                }

                Intent confirm_intent = new Intent(getApplicationContext(), SpeechConfirm.class);
                confirm_intent.putExtra("image_path", imagePath);
                confirm_intent.putExtra("description", description);
                startActivity(confirm_intent);
            }
        });

        back.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                Intent menu_intent = new Intent(getApplicationContext(), Menu.class);
                startActivity(menu_intent);
            }
        });

    }

    private void startRecordingUi() {
        isRecording = true;

        recordingStatus.setVisibility(View.VISIBLE);
        recordingTimer.setText(R.string.recording_default_time);
        recordStartMs = System.currentTimeMillis();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);

        // Radar pulse rings emanating from the mic
        startPulse(pulseRing1, 0);
        startPulse(pulseRing2, 800);

        // Gentle breathing pulse on the mic button itself
        addAnimator(buildBreath(start_record, "scaleX"));
        addAnimator(buildBreath(start_record, "scaleY"));

        // Blinking status dot
        ObjectAnimator blink = ObjectAnimator.ofFloat(recordingDot, "alpha", 1f, 0.15f);
        blink.setDuration(600);
        blink.setRepeatCount(ValueAnimator.INFINITE);
        blink.setRepeatMode(ValueAnimator.REVERSE);
        addAnimator(blink);

        recordingStatus.announceForAccessibility(getString(R.string.recording_started));
    }

    private void stopRecordingUi() {
        isRecording = false;

        timerHandler.removeCallbacks(timerRunnable);

        for (Animator a : animators) {
            a.cancel();
        }
        animators.clear();

        start_record.setScaleX(1f);
        start_record.setScaleY(1f);
        recordingDot.setAlpha(1f);
        pulseRing1.setVisibility(View.GONE);
        pulseRing2.setVisibility(View.GONE);
        recordingStatus.setVisibility(View.GONE);

        recordingStatus.announceForAccessibility(getString(R.string.recording_stopped));
    }

    private void startPulse(View v, long delay) {
        v.setVisibility(View.VISIBLE);
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setAlpha(0.6f);

        addAnimator(buildRing(v, "scaleX", 1f, 2.4f, delay));
        addAnimator(buildRing(v, "scaleY", 1f, 2.4f, delay));
        addAnimator(buildRing(v, "alpha", 0.6f, 0f, delay));
    }

    private ObjectAnimator buildRing(View v, String prop, float from, float to, long delay) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, prop, from, to);
        a.setDuration(1600);
        a.setStartDelay(delay);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.RESTART);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.start();
        return a;
    }

    private ObjectAnimator buildBreath(View v, String prop) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, prop, 1f, 1.08f);
        a.setDuration(700);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.start();
        return a;
    }

    private void addAnimator(Animator a) {
        animators.add(a);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        for (Animator a : animators) {
            a.cancel();
        }
        animators.clear();
    }
}
