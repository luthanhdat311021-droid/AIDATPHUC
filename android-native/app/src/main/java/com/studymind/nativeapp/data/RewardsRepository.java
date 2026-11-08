package com.studymind.nativeapp.data;

import com.google.gson.JsonObject;

/** Network-only reward state. A response from an old login can never populate another account. */
public final class RewardsRepository {
    private final ApiClient api;
    private final AuthRepository auth;
    public RewardsRepository(ApiClient api, AuthRepository auth) { this.api = api; this.auth = auth; }

    public void request(String method, String path, JsonObject body, Async.Callback<JsonObject> callback) {
        String owner = auth.getUser() != null ? auth.getUser().id : null;
        Async.run(() -> {
            if (owner == null) throw new ApiException(401, "Vui lòng đăng nhập.", null);
            // Wallet/missions calls are quick; never hold the screen for the 5-minute AI timeout
            JsonObject response = api.sendAsUser(method, "/api/v1/rewards" + path, body, owner, 20);
            return response.getAsJsonObject("data");
        }, new Async.Callback<JsonObject>() {
            private boolean current() { return auth.getUser() != null && owner != null && owner.equals(auth.getUser().id); }
            @Override public void onSuccess(JsonObject result) { if (current()) callback.onSuccess(result); }
            @Override public void onError(String message) { if (current()) callback.onError(message); }
        });
    }
}
