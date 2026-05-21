package com.example.slam_hri_mobile_application;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class Menu extends AppCompatActivity {

    TextView send_speech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        getSupportActionBar().hide();

        send_speech = (TextView) findViewById(R.id.textView3);

        send_speech.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent send_speech_intent = new Intent(getApplicationContext(), SpeechRequest.class);
                startActivity(send_speech_intent);
            }
        });
    }
}