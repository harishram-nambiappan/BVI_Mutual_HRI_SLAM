package com.example.slam_hri_mobile_application;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

public class CamImg extends AppCompatActivity {

    TextView take_image, add_speech, back;
    PreviewView pv;
    FloatingActionButton flip_camera;
    ListenableFuture<ProcessCameraProvider> cpf;

    ProcessCameraProvider pcp;

    Preview preview;

    CameraSelector cs;

    Camera camera;

    ImageCapture ic;

    ImageCapture.OutputFileOptions ofo;

    ContentValues cv;

    int lensFacing = CameraSelector.LENS_FACING_FRONT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cam_img);

        getSupportActionBar().hide();

        pv = (PreviewView) findViewById(R.id.previewView);

        take_image = (TextView) findViewById(R.id.textView14);

        back = (TextView) findViewById(R.id.textView16);

        flip_camera = (FloatingActionButton) findViewById(R.id.flipCamera);

        if(ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        }

        cpf = ProcessCameraProvider.getInstance(this);

        try{
            pcp = cpf.get();
            bindCamera();
        }
        catch(Exception e){
            e.printStackTrace();
        }

        flip_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                lensFacing = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                        ? CameraSelector.LENS_FACING_BACK
                        : CameraSelector.LENS_FACING_FRONT;
                bindCamera();
            }
        });

        cv = new ContentValues();

        take_image.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "extra_img_1.jpg");
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image");
                }

                ofo = new ImageCapture.OutputFileOptions.Builder(getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cv).build();
                ic.takePicture(ofo, ContextCompat.getMainExecutor(getApplicationContext()), new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Toast saved_toast = Toast.makeText(getApplicationContext(), "Image Captured", Toast.LENGTH_SHORT);
                        saved_toast.show();
                        Intent review_intent = new Intent(getApplicationContext(), ImageReview.class);
                        review_intent.putExtra("image_path", "/storage/emulated/0/Pictures/CameraX-Image/extra_img_1.jpg");
                        startActivity(review_intent);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {

                    }
                });

            }
        });

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent back_intent = new Intent(getApplicationContext(), Menu.class);
                startActivity(back_intent);
            }
        });

    }

    private void bindCamera() {
        if (pcp == null) {
            return;
        }

        CameraSelector requested = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        try {
            if (!pcp.hasCamera(requested)) {
                Toast.makeText(getApplicationContext(), R.string.cam_unavailable, Toast.LENGTH_SHORT).show();
                lensFacing = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                        ? CameraSelector.LENS_FACING_BACK
                        : CameraSelector.LENS_FACING_FRONT;
                return;
            }
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
        }

        try {
            pcp.unbindAll();

            preview = new Preview.Builder().build();
            preview.setSurfaceProvider(pv.getSurfaceProvider());

            cs = requested;
            ic = new ImageCapture.Builder().build();

            camera = pcp.bindToLifecycle((LifecycleOwner) this, cs, preview, ic);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}