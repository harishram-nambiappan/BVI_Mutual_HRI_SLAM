package com.example.slam_hri_mobile_application;

import androidx.appcompat.app.AppCompatActivity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class ImageAnalysis extends AppCompatActivity {

    ImageView analysisImage;
    View scanLine;
    MaterialCardView analysisCard;
    android.widget.TextView analysisText;
    CircularProgressIndicator analysisProgress;
    MaterialButton addSpeechBtn, retakeBtn;

    String imagePath;
    File imageFile;
    String description = "";
    ObjectAnimator scanAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_analysis);

        getSupportActionBar().hide();

        analysisImage = (ImageView) findViewById(R.id.analysisImage);
        scanLine = findViewById(R.id.scanLine);
        analysisCard = (MaterialCardView) findViewById(R.id.analysisCard);
        analysisText = (android.widget.TextView) findViewById(R.id.analysisText);
        analysisProgress = (CircularProgressIndicator) findViewById(R.id.analysisProgress);
        addSpeechBtn = (MaterialButton) findViewById(R.id.addSpeechBtn);
        retakeBtn = (MaterialButton) findViewById(R.id.retakeBtn);

        imagePath = getIntent().getStringExtra("image_path");
        if (!TextUtils.isEmpty(imagePath)) {
            Bitmap bm = loadOrientedBitmap(imagePath);
            if (bm != null) {
                analysisImage.setImageBitmap(bm);
            }
            imageFile = new File(imagePath);
        }

        // Retry analysis by tapping the result card.
        analysisCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (analysisProgress.getVisibility() != View.VISIBLE) {
                    startAnalysis();
                }
            }
        });

        addSpeechBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent speech_intent = new Intent(getApplicationContext(), SpeechRequest.class);
                speech_intent.putExtra("image_path", imagePath);
                speech_intent.putExtra("description", description);
                startActivity(speech_intent);
            }
        });

        retakeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent cam_intent = new Intent(getApplicationContext(), CamImg.class);
                startActivity(cam_intent);
            }
        });

        startAnalysis();
    }

    private void startAnalysis() {
        addSpeechBtn.setEnabled(false);
        analysisProgress.setVisibility(View.VISIBLE);
        analysisText.setText(R.string.analyzing);
        startScanAnimation();

        if (imageFile == null || !imageFile.exists()) {
            onAnalysisFinished("(no image to analyze)", true);
            return;
        }

        OpenAiVision.describe(imageFile, new OpenAiVision.DescriptionCallback() {
            @Override
            public void onResult(String result) {
                onAnalysisFinished(prettify(result), false);
            }

            @Override
            public void onError(String message) {
                onAnalysisFinished("[image description unavailable: " + message + "]", true);
            }
        });
    }

    private void onAnalysisFinished(final String result, final boolean isError) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopScanAnimation();
                analysisProgress.setVisibility(View.GONE);
                description = result;
                if (isError) {
                    analysisText.setText(getString(R.string.analysis_failed) + "\n\n" + result);
                } else {
                    analysisText.setText(result);
                }
                // Allow the user to continue regardless (they can still add speech).
                addSpeechBtn.setEnabled(true);
            }
        });
    }

    // Pretty-print the JSON object for readability; fall back to raw text.
    private String prettify(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            return new JSONObject(raw).toString(2);
        } catch (Exception e) {
            return raw;
        }
    }

    private void startScanAnimation() {
        scanLine.setVisibility(View.VISIBLE);
        scanLine.post(new Runnable() {
            @Override
            public void run() {
                View parent = (View) scanLine.getParent();
                float max = parent.getHeight() - scanLine.getHeight();
                if (max <= 0) {
                    return;
                }
                stopScanAnimation();
                scanAnimator = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, max);
                scanAnimator.setDuration(1300);
                scanAnimator.setRepeatCount(ValueAnimator.INFINITE);
                scanAnimator.setRepeatMode(ValueAnimator.REVERSE);
                scanAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                scanAnimator.start();
            }
        });
    }

    private void stopScanAnimation() {
        if (scanAnimator != null) {
            scanAnimator.cancel();
            scanAnimator = null;
        }
        scanLine.setTranslationY(0f);
        scanLine.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScanAnimation();
    }

    // Decodes the captured file, downsampled and EXIF-rotated for display.
    private Bitmap loadOrientedBitmap(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);

        bounds.inSampleSize = calculateInSampleSize(bounds, 1200, 1200);
        bounds.inJustDecodeBounds = false;

        Bitmap bitmap = BitmapFactory.decodeFile(path, bounds);
        if (bitmap == null) {
            return null;
        }

        int rotation = 0;
        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotation = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotation = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotation = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        }
        return bitmap;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
