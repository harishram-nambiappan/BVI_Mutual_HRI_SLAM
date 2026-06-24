package com.example.slam_hri_mobile_application;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Uploads the recorded audio clip and its transcript to the desktop Python
 * receiver over the local network. The target URL comes from BuildConfig
 * (set via SERVER_URL in the .env file).
 *
 * Callbacks fire on a background thread; callers must hop to the UI thread.
 */
public class ServerUploader {

    public interface UploadCallback {
        void onSuccess();
        void onError(String message);
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public static void upload(final File audioFile, final String transcript, final UploadCallback cb) {
        String base = BuildConfig.SERVER_URL;
        if (base == null || base.trim().isEmpty()) {
            cb.onError("NO_URL");
            return;
        }
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            cb.onError("NO_FILE");
            return;
        }

        base = base.trim();
        String url = base.endsWith("/") ? base + "upload" : base + "/upload";

        RequestBody fileBody = RequestBody.create(MediaType.parse("audio/mp4"), audioFile);

        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", audioFile.getName(), fileBody)
                .addFormDataPart("transcript", transcript != null ? transcript : "")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                cb.onError(e.getMessage() != null ? e.getMessage() : "Network error");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody rb = response.body()) {
                    if (response.isSuccessful()) {
                        cb.onSuccess();
                    } else {
                        String payload = rb != null ? rb.string() : "";
                        cb.onError("HTTP " + response.code() + ": " + payload);
                    }
                } catch (Exception e) {
                    cb.onError(e.getMessage() != null ? e.getMessage() : "Upload error");
                }
            }
        });
    }
}
