package com.studymind.nativeapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.studymind.nativeapp.BuildConfig;
import com.studymind.nativeapp.data.model.Models.AppUser;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Supabase Auth (GoTrue) over REST — the same project and accounts as the web app.
 * The session lives in private SharedPreferences and is refreshed shortly before it expires.
 */
public class AuthRepository {
    public static final String DEFAULT_AVATAR = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80";
    /** Deep link from AndroidManifest.xml; must be listed in Supabase → Authentication → URL Configuration */
    public static final String NATIVE_AUTH_REDIRECT = "com.studymind.nativeapp://auth/callback";

    public interface UserListener { void onUserChanged(AppUser user); }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final SharedPreferences prefs;
    private final OkHttpClient http;
    private final String base = BuildConfig.SUPABASE_URL + "/auth/v1";
    private final Set<UserListener> listeners = new HashSet<>();

    private String accessToken;
    private String refreshToken;
    private long expiresAt;
    private AppUser user;

    public AuthRepository(Context context, OkHttpClient http) {
        this.http = http;
        this.prefs = context.getSharedPreferences("studymind_session", Context.MODE_PRIVATE);
        accessToken = prefs.getString("access_token", null);
        refreshToken = prefs.getString("refresh_token", null);
        expiresAt = prefs.getLong("expires_at", 0);
        String saved = prefs.getString("user", null);
        if (saved != null) {
            try { user = toAppUser(JsonParser.parseString(saved).getAsJsonObject()); } catch (RuntimeException ignored) {}
        }
    }

    public boolean isSignedIn() { return refreshToken != null; }

    public AppUser getUser() { return user; }

    public void addListener(UserListener l) { listeners.add(l); }

    public void removeListener(UserListener l) { listeners.remove(l); }

    /** Valid access token (refreshing it if needed), or null when signed out. Blocking: call off the main thread. */
    public synchronized String token(boolean forceRefresh) throws IOException {
        if (refreshToken == null) return null;
        long now = System.currentTimeMillis() / 1000;
        if (!forceRefresh && accessToken != null && now < expiresAt - 60) return accessToken;
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refreshToken);
        try {
            storeSession(request("POST", "/token?grant_type=refresh_token", body, null));
        } catch (ApiException e) {
            clear(); // refresh token revoked / expired → back to the sign-in screen
            return null;
        }
        return accessToken;
    }

    public void signIn(String email, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email.trim());
        body.addProperty("password", password);
        storeSession(request("POST", "/token?grant_type=password", body, null));
    }

    /** @return false when Supabase requires e-mail confirmation first (no session yet) */
    public boolean signUp(String fullName, String email, String password) throws IOException {
        String clean = email.trim().toLowerCase();
        JsonObject data = new JsonObject();
        data.addProperty("full_name", fullName.trim().isEmpty() ? clean.split("@")[0] : fullName.trim());
        data.addProperty("avatar_url", DEFAULT_AVATAR);
        JsonObject body = new JsonObject();
        body.addProperty("email", clean);
        body.addProperty("password", password);
        body.add("data", data);
        JsonObject res = request("POST", "/signup", body, null);
        if (!res.has("access_token")) {
            // Supabase answers a sign-up for an existing e-mail with a user that has no identities
            // (so e-mails can't be enumerated); that is "already registered", not "check your inbox"
            JsonObject u = res.has("user") && res.get("user").isJsonObject() ? res.getAsJsonObject("user") : res;
            if (u.has("identities") && u.get("identities").isJsonArray() && u.getAsJsonArray("identities").size() == 0) {
                throw new ApiException(422, "Email này đã được đăng ký. Hãy chuyển sang Đăng nhập.", null);
            }
            return false;
        }
        storeSession(res);
        return true;
    }

    /** Sends Supabase's password-reset e-mail (the link opens the web app to choose a new password) */
    public void recoverPassword(String email) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email.trim().toLowerCase());
        request("POST", "/recover", body, null);
    }

    public void updateProfile(String fullName, String avatarUrl) throws IOException {
        String t = token(false);
        if (t == null) throw new ApiException(401, "Phiên đăng nhập đã hết hạn.", null);
        JsonObject data = new JsonObject();
        data.addProperty("full_name", fullName.trim());
        data.addProperty("avatar_url", avatarUrl.trim().isEmpty() ? DEFAULT_AVATAR : avatarUrl.trim());
        JsonObject body = new JsonObject();
        body.add("data", data);
        saveUser(request("PUT", "/user", body, t));
    }

    public void signOut() {
        String t = accessToken;
        if (t != null) {
            try { request("POST", "/logout", new JsonObject(), t); } catch (IOException ignored) {}
        }
        clear();
    }

    /** OAuth providers switched on in Supabase (only those get a button, like the web sign-in page) */
    public Set<String> enabledProviders() {
        Set<String> out = new HashSet<>();
        Request req = new Request.Builder().url(base + "/settings").header("apikey", BuildConfig.SUPABASE_ANON_KEY).build();
        try (Response r = http.newCall(req).execute()) {
            JsonObject ext = JsonParser.parseString(r.body().string()).getAsJsonObject().getAsJsonObject("external");
            if (ext != null) {
                for (Map.Entry<String, JsonElement> e : ext.entrySet()) {
                    if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isBoolean() && e.getValue().getAsBoolean()) out.add(e.getKey());
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public String oauthUrl(String provider) {
        return base + "/authorize?provider=" + provider + "&redirect_to=" + Uri.encode(NATIVE_AUTH_REDIRECT);
    }

    /** Tokens arrive in the deep-link fragment: com.studymind.nativeapp://auth/callback#access_token=… */
    public void handleOAuthCallback(Uri uri) throws IOException {
        String raw = uri.getEncodedFragment() != null ? uri.getEncodedFragment() : uri.getEncodedQuery();
        Map<String, String> params = new HashMap<>();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int i = pair.indexOf('=');
                if (i > 0) params.put(pair.substring(0, i), Uri.decode(pair.substring(i + 1).replace('+', ' ')));
            }
        }
        if (params.containsKey("error_description")) throw new ApiException(400, params.get("error_description"), null);
        if (!params.containsKey("access_token") || !params.containsKey("refresh_token")) {
            throw new ApiException(400, "Không nhận được phiên đăng nhập.", null);
        }
        synchronized (this) {
            accessToken = params.get("access_token");
            refreshToken = params.get("refresh_token");
            long expiresIn = 3600;
            try { expiresIn = Long.parseLong(params.get("expires_in")); } catch (RuntimeException ignored) {}
            expiresAt = System.currentTimeMillis() / 1000 + expiresIn;
            persistTokens();
        }
        saveUser(request("GET", "/user", null, accessToken));
    }

    // ---------- internals ----------

    private synchronized void storeSession(JsonObject res) throws ApiException {
        if (!res.has("access_token")) throw new ApiException(500, "Phản hồi đăng nhập không hợp lệ.", null);
        accessToken = res.get("access_token").getAsString();
        refreshToken = res.has("refresh_token") ? res.get("refresh_token").getAsString() : refreshToken;
        expiresAt = res.has("expires_at")
                ? res.get("expires_at").getAsLong()
                : System.currentTimeMillis() / 1000 + (res.has("expires_in") ? res.get("expires_in").getAsLong() : 3600);
        persistTokens();
        if (res.has("user") && res.get("user").isJsonObject()) saveUser(res.getAsJsonObject("user"));
    }

    private void persistTokens() {
        prefs.edit().putString("access_token", accessToken).putString("refresh_token", refreshToken).putLong("expires_at", expiresAt).apply();
    }

    private void saveUser(JsonObject u) {
        prefs.edit().putString("user", u.toString()).apply();
        setUser(toAppUser(u));
    }

    private void setUser(AppUser u) {
        user = u;
        Async.main(() -> { for (UserListener l : new HashSet<>(listeners)) l.onUserChanged(u); });
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o != null ? o.get(key) : null;
        return e != null && e.isJsonPrimitive() && !e.getAsString().isEmpty() ? e.getAsString() : null;
    }

    private static AppUser toAppUser(JsonObject u) {
        JsonObject meta = u.has("user_metadata") && u.get("user_metadata").isJsonObject() ? u.getAsJsonObject("user_metadata") : null;
        String email = str(u, "email");
        String name = str(meta, "full_name");
        if (name == null) name = str(meta, "name");
        if (name == null) name = email != null ? email.split("@")[0] : "Học viên";
        String avatar = str(meta, "avatar_url");
        if (avatar == null) avatar = str(meta, "picture");
        return new AppUser(str(u, "id"), email, name, avatar != null ? avatar : DEFAULT_AVATAR);
    }

    private synchronized void clear() {
        accessToken = null;
        refreshToken = null;
        expiresAt = 0;
        prefs.edit().clear().apply();
        setUser(null);
    }

    private JsonObject request(String method, String path, JsonObject body, String bearer) throws IOException {
        Request.Builder b = new Request.Builder().url(base + path).header("apikey", BuildConfig.SUPABASE_ANON_KEY);
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        b.method(method, body != null ? RequestBody.create(body.toString(), JSON) : (method.equals("GET") ? null : RequestBody.create("", JSON)));
        try (Response r = http.newCall(b.build()).execute()) {
            String text = r.body() != null ? r.body().string() : "";
            JsonObject json;
            try { json = JsonParser.parseString(text).getAsJsonObject(); } catch (RuntimeException e) { json = new JsonObject(); }
            if (!r.isSuccessful()) {
                String msg = null;
                for (String k : new String[]{"error_description", "msg", "message", "error"}) {
                    if (msg == null) msg = str(json, k);
                }
                throw new ApiException(r.code(), translate(msg != null ? msg : "Lỗi xác thực (" + r.code() + ")"), null);
            }
            return json;
        }
    }

    // Supabase Auth messages are English; show the common ones in Vietnamese (same table as the web app)
    private static String translate(String msg) {
        switch (msg) {
            case "Invalid login credentials": return "Email hoặc mật khẩu không đúng.";
            case "Email not confirmed": return "Email chưa được xác nhận. Hãy mở email và bấm vào liên kết xác nhận trước khi đăng nhập.";
            case "User already registered": return "Email này đã được đăng ký. Hãy chuyển sang Đăng nhập.";
            case "Password should be at least 6 characters.": return "Mật khẩu phải có ít nhất 6 ký tự.";
            default: return msg;
        }
    }
}
