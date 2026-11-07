package com.studymind.nativeapp.data;

import com.google.gson.JsonObject;
import com.studymind.nativeapp.StudyMindApp;

/**
 * Links the Pomodoro timer to a StudyCoin focus session timed by the server: the device only sends
 * heartbeats, the server decides how many seconds count. When rewards are off or the user is signed out,
 * every call fails quietly and the Pomodoro keeps working as a plain timer.
 */
public final class FocusRewards {
    public interface Notice { void show(String message); }

    private static final long BEAT_MS = 60_000;
    private static final int FINISH_RETRIES = 3;
    private static String sessionId;
    private static int retriesLeft;
    private static Notice pending;
    private static final Runnable beat = FocusRewards::heartbeat;
    private static final Runnable retryFinish = () -> sendHeartbeat(FocusRewards::finish);

    private FocusRewards() {}

    private static RewardsRepository repo() { return StudyMindApp.state().rewards(); }

    /** A focus (work) phase started or resumed: open/resume the server session and beat every minute. */
    public static void workStarted() {
        Async.cancel(beat);
        Async.cancel(retryFinish);
        JsonObject body = new JsonObject();
        body.addProperty("kind", "focus");
        repo().request("POST", "/sessions/start", body, new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject s) {
                sessionId = s != null && s.has("id") ? s.get("id").getAsString() : null;
                if (sessionId != null) Async.mainDelayed(beat, BEAT_MS);
            }
            @Override public void onError(String message) { sessionId = null; }
        });
    }

    /** Paused, reset or switched to a break: stop crediting time (the server session stays open). */
    public static void workPaused() {
        Async.cancel(beat);
        Async.cancel(retryFinish);
    }

    /** The 25-minute phase finished on the device; the server confirms it with its own clock. */
    public static void workCompleted(Notice notice) {
        workPaused();
        if (sessionId == null) return;
        pending = notice;
        retriesLeft = FINISH_RETRIES;
        sendHeartbeat(FocusRewards::finish);
    }

    private static void heartbeat() {
        sendHeartbeat(() -> { if (sessionId != null) Async.mainDelayed(beat, BEAT_MS); });
    }

    private static void sendHeartbeat(Runnable then) {
        if (sessionId == null) return;
        repo().request("POST", "/sessions/" + sessionId + "/heartbeat", new JsonObject(), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) { then.run(); }
            @Override public void onError(String message) { then.run(); }
        });
    }

    private static void finish() {
        if (sessionId == null) return;
        String id = sessionId;
        repo().request("POST", "/sessions/" + id + "/finish", new JsonObject(), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                if (!id.equals(sessionId)) return;
                sessionId = null;
                show("⭐ Phiên tập trung đã được ghi nhận cho nhiệm vụ StudyCoin.");
            }
            @Override public void onError(String message) {
                if (!id.equals(sessionId)) return;
                // Usually a few seconds short on the server clock, or fewer than 2 study activities yet:
                // keep the session alive a little longer and try again.
                if (retriesLeft-- > 0) Async.mainDelayed(retryFinish, BEAT_MS);
                else show(message);
            }
        });
    }

    private static void show(String message) {
        Notice n = pending;
        pending = null;
        if (n != null && message != null) n.show(message);
    }
}
