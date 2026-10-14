package com.studymind.nativeapp.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.studymind.nativeapp.BuildConfig;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * StudyMind API: the Supabase Edge Function "studymind-api" (same Express routes as the web backend) plus
 * Supabase Storage for files — nothing goes through Vercel. Every request carries the user's Supabase
 * access token; the server derives role / plan from it, so nothing here can grant access.
 */
public class ApiClient {
    /** Called when the server answers 402 (Pro feature or daily AI quota) so the app can offer the upgrade */
    public interface UpgradeListener { void onUpgradeRequired(String reason); }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final AuthRepository auth;
    private UpgradeListener upgradeListener;

    public ApiClient(OkHttpClient http, AuthRepository auth) {
        this.http = http;
        this.auth = auth;
    }

    public void setUpgradeListener(UpgradeListener l) { upgradeListener = l; }

    public JsonObject get(String path) throws IOException { return send("GET", path, null); }

    public JsonObject post(String path, JsonObject body) throws IOException {
        return send("POST", path, RequestBody.create((body != null ? body : new JsonObject()).toString(), JSON));
    }

    public JsonObject put(String path, JsonObject body) throws IOException {
        return send("PUT", path, RequestBody.create(body.toString(), JSON));
    }

    public JsonObject delete(String path) throws IOException { return send("DELETE", path, null); }

    /**
     * Uploads a file to Supabase Storage as the signed-in user (bucket policies only allow "<uid>/…" paths).
     * Goes straight to Supabase, so the size is bounded by the bucket limit (50MB), not by any API host.
     */
    public void uploadToStorage(String bucket, String objectPath, byte[] bytes, String mime, boolean upsert) throws IOException {
        String url = BuildConfig.SUPABASE_URL + "/storage/v1/object/" + bucket + "/" + objectPath;
        RequestBody body = RequestBody.create(bytes, MediaType.parse(mime != null ? mime : "application/octet-stream"));
        Request.Builder rb = new Request.Builder().url(url).post(body).header("x-upsert", String.valueOf(upsert));
        String token = auth.token(false);
        if (token == null) throw new ApiException(401, "Vui lòng đăng nhập để tiếp tục.", null);
        try (Response r = http.newCall(rb.header("Authorization", "Bearer " + token).header("apikey", BuildConfig.SUPABASE_ANON_KEY).build()).execute()) {
            if (r.isSuccessful()) return;
            String text = r.body() != null ? r.body().string() : "";
            String msg = r.code() == 413 || text.contains("maximum allowed size") ? "Tệp quá lớn. Giới hạn là 50MB." : "Không tải được tệp lên (" + r.code() + ").";
            throw new ApiException(r.code(), msg, null);
        }
    }

    /** Public URL of an object in a public bucket (avatars) */
    public static String publicUrl(String bucket, String objectPath) {
        return BuildConfig.SUPABASE_URL + "/storage/v1/object/public/" + bucket + "/" + objectPath;
    }

    private JsonObject send(String method, String path, RequestBody body) throws IOException {
        String token = auth.token(false);
        if (token == null) throw new ApiException(401, "Vui lòng đăng nhập để tiếp tục.", null);
        try {
            return execute(method, path, body, token);
        } catch (ApiException e) {
            if (e.status != 401) throw e;
            // Token rejected (e.g. revoked elsewhere): refresh once and retry
            String fresh = auth.token(true);
            if (fresh == null) throw e;
            return execute(method, path, body, fresh);
        }
    }

    /** StudyMind Pro / SePay / admin live in the Supabase Edge Function "studymind-billing", not on Vercel */
    public static String billing(String path) {
        return BuildConfig.SUPABASE_URL + "/functions/v1/studymind-billing" + path;
    }

    private JsonObject execute(String method, String path, RequestBody body, String token) throws IOException {
        Request req = new Request.Builder()
                .url(path.startsWith("http") ? path : BuildConfig.API_BASE_URL + path)
                .header("Authorization", "Bearer " + token)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY) // the Supabase gateway wants the project key too
                .method(method, body)
                .build();
        try (Response r = http.newCall(req).execute()) {
            String text = r.body() != null ? r.body().string() : "";
            JsonObject json = null;
            try {
                JsonElement el = JsonParser.parseString(text);
                if (el.isJsonObject()) json = el.getAsJsonObject();
            } catch (RuntimeException ignored) {}

            boolean failed = !r.isSuccessful() || (json != null && json.has("success") && !json.get("success").getAsBoolean());
            if (!failed && json != null) return json;

            String message;
            String code = json != null && json.has("code") && json.get("code").isJsonPrimitive() ? json.get("code").getAsString() : null;
            if (json != null && json.has("error") && json.get("error").isJsonPrimitive()) message = json.get("error").getAsString();
            else if (r.code() == 546 || r.code() == 504) message = "Máy chủ xử lý quá lâu. Hãy thử lại hoặc dùng tài liệu ngắn hơn.";
            else message = "Lỗi máy chủ (Mã lỗi " + r.code() + ")";

            if (r.code() == 402 && upgradeListener != null) {
                String reason = message;
                Async.main(() -> upgradeListener.onUpgradeRequired(reason));
            }
            throw new ApiException(r.code(), message, code);
        }
    }
}
