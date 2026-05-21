package com.example.slam_hri_mobile_application;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class SpeechRequest extends AppCompatActivity {

    ImageView start_record, stop_record;

    TextView back;

    MediaRecorder mr;

    MediaPlayer mp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_request);

        getSupportActionBar().hide();

        start_record = (ImageView) findViewById(R.id.imageView);

        stop_record = (ImageView) findViewById(R.id.imageView2);

        back = (TextView) findViewById(R.id.textView7);

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
                try {
                    mr.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    mr.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
                    mr.setOutputFile(getFilesDir()+"/slam_hri_request_sample_1.mp4");
                    mr.prepare();
                    mr.start();
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
        });

        stop_record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mr.stop();
                mr.release();

                Intent confirm_intent = new Intent(getApplicationContext(), SpeechConfirm.class);
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
}