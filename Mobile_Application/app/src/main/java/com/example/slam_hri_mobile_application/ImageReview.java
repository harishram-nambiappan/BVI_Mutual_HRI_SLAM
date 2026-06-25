package com.example.slam_hri_mobile_application;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;

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

        final String imagePath = getIntent().getStringExtra("image_path");
        if (!TextUtils.isEmpty(imagePath)) {
            bm = loadOrientedBitmap(imagePath);
            iv.setImageBitmap(bm);
        }

        add_speech.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Intent analysis_intent = new Intent(getApplicationContext(), ImageAnalysis.class);
                analysis_intent.putExtra("image_path", imagePath);
                startActivity(analysis_intent);
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

    // Decodes the captured file, downsampled to avoid out-of-memory on large
    // photos, and rotated to match the camera's EXIF orientation.
    private Bitmap loadOrientedBitmap(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);

        bounds.inSampleSize = calculateInSampleSize(bounds, 1600, 1600);
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
