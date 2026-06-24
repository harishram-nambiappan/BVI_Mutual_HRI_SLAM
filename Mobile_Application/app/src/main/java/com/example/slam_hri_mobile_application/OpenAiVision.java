package com.example.slam_hri_mobile_application;

import androidx.annotation.NonNull;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sends a captured photo to OpenAI and asks for a detailed scene description,
 * tailored to the assistive human-robot mapping use case.
 *
 * Calls run asynchronously; callbacks fire on a background thread, so the
 * caller is responsible for hopping back to the UI thread.
 */
public class OpenAiVision {

    public interface DescriptionCallback {
        void onResult(String description);
        void onError(String message);
    }

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-5";

    // Context for the system: a blind/visually-impaired person walks an indoor
    // public space holding the phone, reporting obstacles and points of interest
    // so robots can understand and navigate the environment.
    private static final String SYSTEM_PROMPT =
            "You are a visual scene-description assistant for an assistive human-robot "
            + "mapping system. A blind or visually impaired person is walking through an "
            + "indoor public space (such as an airport terminal) while holding a phone. "
            + "Their job is to report obstacles, hazards, and points of interest so that "
            + "robots can understand and navigate the environment. They have just captured "
            + "a photo of what is in front of them. Describe the photo in high detail and in "
            + "a way that is useful for robot navigation and environment mapping. Include: "
            + "notable objects and obstacles, their approximate position (left, center, right) "
            + "and distance (near, mid, far), people, doors, signage and any readable text, "
            + "floor and path conditions, and anything that could block or guide movement. "
            + "Be precise, concrete, and spatially organized. Do not guess beyond what is "
            + "visible.";

    private static final String USER_PROMPT =
            "Describe this image in high detail for robot navigation and mapping.";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private static String model() {
        String m = BuildConfig.VISION_MODEL;
        return (m == null || m.trim().isEmpty()) ? DEFAULT_MODEL : m.trim();
    }

    public static void describe(final File imageFile, final DescriptionCallback cb) {
        if (!OpenAiClient.hasApiKey()) {
            cb.onError("NO_KEY");
            return;
        }
        if (imageFile == null || !imageFile.exists() || imageFile.length() == 0) {
            cb.onError("NO_FILE");
            return;
        }

        final String requestJson;
        try {
            String dataUrl = "data:image/jpeg;base64,"
                    + Base64.encodeToString(readAllBytes(imageFile), Base64.NO_WRAP);

            JSONObject systemMsg = new JSONObject()
                    .put("role", "system")
                    .put("content", SYSTEM_PROMPT);

            JSONArray userContent = new JSONArray()
                    .put(new JSONObject().put("type", "text").put("text", USER_PROMPT))
                    .put(new JSONObject()
                            .put("type", "image_url")
                            .put("image_url", new JSONObject().put("url", dataUrl)));

            JSONObject userMsg = new JSONObject()
                    .put("role", "user")
                    .put("content", userContent);

            JSONObject payload = new JSONObject()
                    .put("model", model())
                    .put("messages", new JSONArray().put(systemMsg).put(userMsg));

            requestJson = payload.toString();
        } catch (Exception e) {
            cb.onError(e.getMessage() != null ? e.getMessage() : "Failed to build request");
            return;
        }

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), requestJson);

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
                    String text = new JSONObject(payload)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .optString("content", "")
                            .trim();
                    cb.onResult(text);
                } catch (Exception e) {
                    cb.onError(e.getMessage() != null ? e.getMessage() : "Parse error");
                }
            }
        });
    }

    private static byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
