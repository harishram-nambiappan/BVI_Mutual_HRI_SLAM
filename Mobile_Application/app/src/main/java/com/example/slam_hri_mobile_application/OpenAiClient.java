package com.example.slam_hri_mobile_application;

import androidx.annotation.NonNull;

import org.json.JSONObject;

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
 * Minimal wrapper around the OpenAI audio transcription endpoint.
 * Calls run asynchronously; callbacks fire on a background thread, so the
 * caller is responsible for hopping back to the UI thread.
 */
public class OpenAiClient {

    public interface TranscriptionCallback {
        void onResult(String transcript);
        void onError(String message);
    }

    private static final String ENDPOINT = "https://api.openai.com/v1/audio/transcriptions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini-transcribe";

    private static String model() {
        String m = BuildConfig.TRANSCRIPTION_MODEL;
        return (m == null || m.trim().isEmpty()) ? DEFAULT_MODEL : m.trim();
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public static boolean hasApiKey() {
        String key = BuildConfig.OPENAI_API_KEY;
        return key != null && !key.isEmpty() && !key.equals("sk-replace-me");
    }

    public static void transcribe(final File audioFile, final TranscriptionCallback cb) {
        if (!hasApiKey()) {
            cb.onError("NO_KEY");
            return;
        }
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            cb.onError("NO_FILE");
            return;
        }

        RequestBody fileBody = RequestBody.create(MediaType.parse("audio/mp4"), audioFile);

        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.getName(), fileBody)
                .addFormDataPart("model", model())
                .addFormDataPart("response_format", "json")
                .build();

        Request request = new Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer " + BuildConfig.OPENAI_API_KEY)
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
                    String payload = rb != null ? rb.string() : "";
                    if (!response.isSuccessful()) {
                        cb.onError("HTTP " + response.code() + ": " + payload);
                        return;
                    }
                    String text = new JSONObject(payload).optString("text", "").trim();
                    cb.onResult(text);
                } catch (Exception e) {
                    cb.onError(e.getMessage() != null ? e.getMessage() : "Parse error");
                }
            }
        });
    }
}
