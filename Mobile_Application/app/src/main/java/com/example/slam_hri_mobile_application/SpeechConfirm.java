package com.example.slam_hri_mobile_application;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class SpeechConfirm extends AppCompatActivity {

    ImageView play_record;

    TextView back, send_speech;

    MediaPlayer mp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_confirm);

        getSupportActionBar().hide();

        play_record = (ImageView) findViewById(R.id.imageView3);

        back = (TextView) findViewById(R.id.textView11);

        send_speech = (TextView) findViewById(R.id.textView10);

        play_record.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                mp = new MediaPlayer();
                try {
                    mp.setDataSource(getFilesDir() + "/slam_hri_request_sample_1.mp4");
                    mp.prepare();
                    mp.start();
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
        });

        send_speech.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent menu_intent = new Intent(getApplicationContext(), Menu.class);
                startActivity(menu_intent);
            }
        });

        back.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Intent record_intent = new Intent(getApplicationContext(), SpeechRequest.class);
                startActivity(record_intent);
            }
        });

    }
}