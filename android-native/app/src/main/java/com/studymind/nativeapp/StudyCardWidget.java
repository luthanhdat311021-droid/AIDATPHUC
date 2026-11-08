package com.studymind.nativeapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.studymind.nativeapp.data.AppState;
import com.studymind.nativeapp.data.Async;

/** Home-screen widget: shows one due flashcard at a time and rates it without opening the app. */
public class StudyCardWidget extends AppWidgetProvider {
    private static final String PREFS = "studymind_widget";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        render(c);
        refresh(c, goAsync());
    }

    /** Not exported, unlike the provider: other apps must not be able to rate cards */
    public static class Actions extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String rating = i.getStringExtra("rating");
            if (rating == null) {
                p.edit().putBoolean("flipped", !p.getBoolean("flipped", false)).apply();
                render(c);
                return;
            }
            JsonArray cards = cards(p);
            if (cards.isEmpty()) return;
            JsonObject card = cards.remove(0).getAsJsonObject();
            p.edit().putString("cards", cards.toString()).putBoolean("flipped", false).apply();
            render(c);
            JsonObject b = new JsonObject();
            b.addProperty("rating", rating);
            PendingResult done = goAsync();
            AppState state = StudyMindApp.state();
            Async.run(() -> state.api().post("/api/v1/documents/" + card.get("documentId").getAsString() + "/flashcards/" + card.get("id").getAsString() + "/review", b),
                    new Async.Callback<JsonObject>() {
                        // On failure the card is still due, so the next refresh brings it back
                        @Override public void onSuccess(JsonObject r) { if (cards.isEmpty()) refresh(c, done); else done.finish(); }
                        @Override public void onError(String m) { done.finish(); }
                    });
        }
    }

    private static void refresh(Context c, BroadcastReceiver.PendingResult done) {
        AppState state = StudyMindApp.state();
        if (!state.auth().isSignedIn()) { clear(c); done.finish(); return; }
        Async.run(() -> state.api().get("/api/v1/flashcards/due"), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                JsonArray fresh = r.getAsJsonArray("cards");
                JsonArray old = cards(p);
                // Don't turn the card over under the user's finger if it is still the first one
                boolean same = !old.isEmpty() && !fresh.isEmpty() && old.get(0).equals(fresh.get(0));
                p.edit().putString("cards", fresh.toString()).putBoolean("flipped", same && p.getBoolean("flipped", false)).apply();
                render(c);
                done.finish();
            }
            @Override public void onError(String m) { done.finish(); }
        });
    }

    /** Signing out: card contents must not stay on the home screen */
    public static void clear(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        render(c);
    }

    private static JsonArray cards(SharedPreferences p) {
        try {
            return JsonParser.parseString(p.getString("cards", "[]")).getAsJsonArray();
        } catch (RuntimeException e) {
            return new JsonArray();
        }
    }

    private static PendingIntent action(Context c, int code, String rating) {
        Intent i = new Intent(c, Actions.class);
        if (rating != null) i.putExtra("rating", rating);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void render(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JsonArray cards = cards(p);
        boolean flipped = p.getBoolean("flipped", false);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_card);
        v.setOnClickPendingIntent(R.id.widgetText, PendingIntent.getActivity(c, 10, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        v.setViewVisibility(R.id.widgetFlip, View.GONE);
        v.setViewVisibility(R.id.widgetHard, View.GONE);
        v.setViewVisibility(R.id.widgetGood, View.GONE);
        if (cards.isEmpty()) {
            v.setTextViewText(R.id.widgetCount, "StudyMind");
            v.setTextViewText(R.id.widgetText, "Chưa có thẻ đến hạn ôn 🎉");
        } else {
            JsonObject card = cards.get(0).getAsJsonObject();
            v.setTextViewText(R.id.widgetCount, cards.size() + " thẻ đến hạn" + (flipped ? " · Đáp án" : ""));
            v.setTextViewText(R.id.widgetText, card.get(flipped ? "back" : "front").getAsString());
            if (flipped) {
                v.setViewVisibility(R.id.widgetHard, View.VISIBLE);
                v.setViewVisibility(R.id.widgetGood, View.VISIBLE);
                v.setOnClickPendingIntent(R.id.widgetHard, action(c, 2, "hard"));
                v.setOnClickPendingIntent(R.id.widgetGood, action(c, 3, "medium"));
            } else {
                v.setViewVisibility(R.id.widgetFlip, View.VISIBLE);
                v.setOnClickPendingIntent(R.id.widgetFlip, action(c, 1, null));
            }
        }
        AppWidgetManager.getInstance(c).updateAppWidget(new ComponentName(c, StudyCardWidget.class), v);
    }
}
