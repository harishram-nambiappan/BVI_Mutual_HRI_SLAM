package com.example.slam_hri_mobile_application;

import androidx.appcompat.app.AppCompatActivity;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SpeechConfirm extends AppCompatActivity {

    ImageView play_record;

    TextView back, send_speech;

    MediaPlayer mp;

    View playRing1, playRing2;
    final List<Animator> animators = new ArrayList<>();

    // Transcription
    MaterialCardView transcriptCard;
    TextView transcriptText;
    CircularProgressIndicator transcriptProgress;
    String lastTranscript = "";

    File audioFile;

    // Image flow (null/absent for speech-only). Description is generated in the
    // background and sent to the desktop app; it is not shown on the phone.
    String imagePath;
    File imageFile;
    volatile String lastDescription = "";
    volatile boolean descriptionDone = true;   // true when there's nothing to wait for
    boolean pendingSend = false;                // user tapped Send while describing

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_confirm);

        getSupportActionBar().hide();

        play_record = (ImageView) findViewById(R.id.imageView3);

        back = (TextView) findViewById(R.id.textView11);

        send_speech = (TextView) findViewById(R.id.textView10);

        playRing1 = findViewById(R.id.playRing1);
        playRing2 = findViewById(R.id.playRing2);

        transcriptCard = (MaterialCardView) findViewById(R.id.transcriptCard);
        transcriptText = (TextView) findViewById(R.id.transcriptText);
        transcriptProgress = (CircularProgressIndicator) findViewById(R.id.transcriptProgress);

        audioFile = new File(getFilesDir(), "slam_hri_request_sample_1.mp4");

        // Retry transcription by tapping the card.
        transcriptCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startTranscription();
            }
        });

        startTranscription();

        imagePath = getIntent().getStringExtra("image_path");
        startDescription();

        play_record.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                mp = new MediaPlayer();
                try {
                    mp.setDataSource(audioFile.getAbsolutePath());
                    mp.prepare();
                    mp.start();
                    startPlaybackUi();
                    mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer player) {
                            stopPlaybackUi();
                        }
                    });
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
        });

        send_speech.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendToServer();
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

    private void sendToServer() {
        // In image mode, wait for the GPT-5 description before uploading so it
        // is actually included (the vision call is slower than transcription).
        if (imageFile != null && !descriptionDone) {
            pendingSend = true;
            send_speech.setEnabled(false);
            Toast.makeText(getApplicationContext(), R.string.waiting_description, Toast.LENGTH_SHORT).show();
            return;
        }
        doUpload();
    }

    private void doUpload() {
        Toast.makeText(getApplicationContext(), R.string.sending, Toast.LENGTH_SHORT).show();
        send_speech.setEnabled(false);

        String mode = (imageFile != null) ? "speech+image" : "speech";

        ServerUploader.upload(audioFile, lastTranscript, mode, imageFile, lastDescription,
                new ServerUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), R.string.sent_ok, Toast.LENGTH_SHORT).show();
                        Intent menu_intent = new Intent(getApplicationContext(), Menu.class);
                        startActivity(menu_intent);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        send_speech.setEnabled(true);
                        int msg = "NO_URL".equals(message) ? R.string.send_no_url : R.string.send_failed;
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void startDescription() {
        lastDescription = "";
        if (imagePath == null || imagePath.isEmpty()) {
            imageFile = null;
            descriptionDone = true;
            return;
        }
        File img = new File(imagePath);
        if (!img.exists()) {
            imageFile = null;
            descriptionDone = true;
            return;
        }
        imageFile = img;
        descriptionDone = false;

        // Runs in the background; result is kept for upload (not shown on phone).
        OpenAiVision.describe(img, new OpenAiVision.DescriptionCallback() {
            @Override
            public void onResult(final String description) {
                onDescriptionFinished(description != null ? description : "");
            }

            @Override
            public void onError(final String message) {
                // Surface the failure (so it's visible on the desktop) instead
                // of silently sending an empty description.
                onDescriptionFinished("[image description unavailable: " + message + "]");
            }
        });
    }

    private void onDescriptionFinished(final String result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lastDescription = result;
                descriptionDone = true;
                if (pendingSend) {
                    pendingSend = false;
                    doUpload();
                }
            }
        });
    }

    private void startTranscription() {
        transcriptProgress.setVisibility(View.VISIBLE);
        transcriptText.setText(R.string.transcribing);

        OpenAiClient.transcribe(audioFile, new OpenAiClient.TranscriptionCallback() {
            @Override
            public void onResult(final String transcript) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        transcriptProgress.setVisibility(View.GONE);
                        if (transcript == null || transcript.isEmpty()) {
                            lastTranscript = "";
                            transcriptText.setText(R.string.transcript_empty);
                        } else {
                            lastTranscript = transcript;
                            transcriptText.setText(transcript);
                        }
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        transcriptProgress.setVisibility(View.GONE);
                        lastTranscript = "";
                        if ("NO_KEY".equals(message)) {
                            transcriptText.setText(R.string.transcript_no_key);
                        } else if ("NO_FILE".equals(message)) {
                            transcriptText.setText(R.string.transcript_no_recording);
                        } else {
                            transcriptText.setText(R.string.transcript_error);
                        }
                    }
                });
            }
        });
    }

    private void startPlaybackUi() {
        startPulse(playRing1, 0);
        startPulse(playRing2, 800);
        addAnimator(buildBreath(play_record, "scaleX"));
        addAnimator(buildBreath(play_record, "scaleY"));
    }

    private void stopPlaybackUi() {
        for (Animator a : animators) {
            a.cancel();
        }
        animators.clear();
        play_record.setScaleX(1f);
        play_record.setScaleY(1f);
        playRing1.setVisibility(View.GONE);
        playRing2.setVisibility(View.GONE);
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
        for (Animator a : animators) {
            a.cancel();
        }
        animators.clear();
        if (mp != null) {
            mp.release();
            mp = null;
        }
    }
}
