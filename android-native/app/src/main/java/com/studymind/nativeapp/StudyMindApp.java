package com.studymind.nativeapp;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.studymind.nativeapp.data.ApiClient;
import com.studymind.nativeapp.data.AppState;
import com.studymind.nativeapp.data.AuthRepository;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class StudyMindApp extends Application {
    private static StudyMindApp instance;

    private AuthRepository auth;
    private AppState state;

    public static StudyMindApp get() { return instance; }

    public static AppState state() { return instance.state; }

    public static AuthRepository auth() { return instance.auth; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        com.studymind.nativeapp.data.Gamification.init(this);

        // AI generation can take minutes (Vercel function limit is 300s)
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
        auth = new AuthRepository(this, http);
        ApiClient api = new ApiClient(http, auth);
        state = new AppState(this, auth, api);

        AppCompatDelegate.setDefaultNightMode(state.isDarkMode()
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
