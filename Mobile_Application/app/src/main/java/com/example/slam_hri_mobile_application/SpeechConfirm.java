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
    volatile boolean transcriptionDone = false;

    File audioFile;

    // Image flow (null/absent for speech-only). The description was already
    // generated on the analysis screen and passed in via the Intent.
    String imagePath;
    File imageFile;
    String lastDescription = "";

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
        lastDescription = getIntent().getStringExtra("description");
        if (lastDescription == null) {
            lastDescription = "";
        }
        if (imagePath != null && !imagePath.isEmpty()) {
            File img = new File(imagePath);
            imageFile = img.exists() ? img : null;
        }

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
        // Wait for the transcription (and, in image mode, the description that was
        // computed on the analysis screen) before allowing a send.
        if (!transcriptionDone) {
            Toast.makeText(getApplicationContext(), R.string.please_wait_processing, Toast.LENGTH_SHORT).show();
            return;
        }

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

    private void startTranscription() {
        transcriptProgress.setVisibility(View.VISIBLE);
        transcriptText.setText(R.string.transcribing);
        transcriptionDone = false;

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
                        transcriptionDone = true;
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
                        // Allow sending even if transcription failed (so the
                        // audio/image still reach the desktop); retry via the card.
                        transcriptionDone = true;
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
