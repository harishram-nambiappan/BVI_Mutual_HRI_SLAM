package com.example.slam_hri_mobile_application;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class ImageReview extends AppCompatActivity {

    TextView add_speech, back;

    ImageView iv;

    Bitmap bm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_review);

        getSupportActionBar().hide();

        iv = (ImageView) findViewById(R.id.imageView4);
        add_speech = (TextView) findViewById(R.id.textView18);
        back = (TextView) findViewById(R.id.textView19);

        bm = BitmapFactory.decodeFile(getIntent().getStringExtra("image_path"));
        iv.setImageBitmap(bm);

        add_speech.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Intent speech_intent = new Intent(getApplicationContext(), SpeechRequest.class);
                startActivity(speech_intent);
            }
        });

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent back_intent = new Intent(getApplicationContext(), CamImg.class);
                startActivity(back_intent);
            }
        });
    }
}