package com.studymind.nativeapp.data;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs blocking network work off the main thread and delivers the result back on it. */
public final class Async {
    private Async() {}

    public interface Task<T> { T run() throws Exception; }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static <T> void run(Task<T> task, Callback<T> callback) {
        POOL.execute(() -> {
            try {
                T result = task.run();
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Đã xảy ra lỗi, vui lòng thử lại.";
                if (e instanceof java.io.IOException && !(e instanceof ApiException)) {
                    msg = "Không kết nối được máy chủ. Kiểm tra Internet rồi thử lại.";
                }
                String finalMsg = msg;
                MAIN.post(() -> callback.onError(finalMsg));
            }
        });
    }

    public static void main(Runnable r) { MAIN.post(r); }

    public static void mainDelayed(Runnable r, long ms) { MAIN.postDelayed(r, ms); }

    public static void cancel(Runnable r) { MAIN.removeCallbacks(r); }
}
