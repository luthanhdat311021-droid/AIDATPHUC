package com.studymind.nativeapp.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.studymind.nativeapp.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Human check: follow two random actions in front of the camera. Frames stay in memory on this device.
 * It stops photos and simple bots, not a replayed video or a modified APK, so it is not a security boundary.
 */
public class LivenessActivity extends AppCompatActivity {
    private static final long TIME_LIMIT_MS = 20_000;
    private static final long VERIFIED_FOR_MS = 10 * 60_000;
    private static final float TURN_DEGREES = 22f;
    private static final float CENTER_DEGREES = 10f;
    private static final float EYE_OPEN = 0.7f;
    private static final float EYE_CLOSED = 0.3f;

    private enum Action { BLINK, TURN_LEFT, TURN_RIGHT }

    private static String verifiedUser;
    private static long verifiedUntil;

    public static boolean isVerified(String userId) {
        return userId != null && userId.equals(verifiedUser) && SystemClock.elapsedRealtime() < verifiedUntil;
    }

    public static Intent intent(Context context, String userId) {
        return new Intent(context, LivenessActivity.class).putExtra("user", userId);
    }

    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else showPrompt("Cần quyền camera để xác minh. Hãy bật trong Cài đặt của máy.");
            });

    private TextView prompt, progress;
    private PreviewView preview;
    private FaceDetector detector;
    private ExecutorService analysis;
    private List<Action> actions;
    private int step;
    private boolean centered, eyesWereOpen, eyesClosed, finished;
    private Integer trackedFace;
    private long deadline;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFF000000);
        preview = new PreviewView(this);
        frame.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(0xCC000000);
        int pad = Ui.dp(this, 20);
        panel.setPadding(pad, pad, pad, Ui.dp(this, 36));
        progress = new TextView(this);
        progress.setTextColor(0xFFCCFBF1);
        progress.setTextSize(14);
        panel.addView(progress);
        prompt = new TextView(this);
        prompt.setTextColor(0xFFFFFFFF);
        prompt.setTextSize(22);
        prompt.setGravity(Gravity.CENTER);
        panel.addView(prompt);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        frame.addView(panel, lp);
        setContentView(frame);
        setTitle(R.string.app_name);

        detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build());
        analysis = Executors.newSingleThreadExecutor();
        restart();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else cameraPermission.launch(Manifest.permission.CAMERA);
    }

    private void showPrompt(String text) { prompt.setText(text); }

    private void restart() {
        List<Action> pool = new ArrayList<>(List.of(Action.BLINK, Action.TURN_LEFT, Action.TURN_RIGHT));
        Collections.shuffle(pool);
        actions = pool.subList(0, 2);
        step = 0;
        trackedFace = null;
        resetStep();
        deadline = SystemClock.elapsedRealtime() + TIME_LIMIT_MS;
        showStep();
    }

    private void resetStep() { centered = false; eyesWereOpen = false; eyesClosed = false; }

    private void showStep() {
        progress.setText("Bước " + (step + 1) + "/" + actions.size());
        switch (actions.get(step)) {
            case BLINK: prompt.setText("Nhìn thẳng rồi chớp mắt"); break;
            case TURN_LEFT: prompt.setText("Nhìn thẳng rồi quay đầu sang trái"); break;
            default: prompt.setText("Nhìn thẳng rồi quay đầu sang phải");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview view = new Preview.Builder().build();
                view.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis images = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                images.setAnalyzer(analysis, this::analyze);
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, view, images);
            } catch (Exception e) {
                prompt.setText("Không mở được camera trước trên máy này.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(@NonNull ImageProxy frame) {
        if (frame.getImage() == null) { frame.close(); return; }
        InputImage image = InputImage.fromMediaImage(frame.getImage(), frame.getImageInfo().getRotationDegrees());
        detector.process(image)
                .addOnSuccessListener(this, this::onFaces)
                .addOnCompleteListener(task -> frame.close());
    }

    private void onFaces(List<Face> faces) {
        if (finished) return;
        if (SystemClock.elapsedRealtime() > deadline) {
            restart();
            prompt.setText("Hết giờ, thử lại: " + prompt.getText());
            return;
        }
        if (faces.size() != 1) {
            progress.setText(faces.isEmpty() ? "Chưa thấy khuôn mặt" : "Chỉ được có một khuôn mặt trong khung");
            return;
        }
        Face face = faces.get(0);
        // A different face appearing midway starts the check again
        if (trackedFace != null && !trackedFace.equals(face.getTrackingId())) { restart(); return; }
        trackedFace = face.getTrackingId();
        progress.setText("Bước " + (step + 1) + "/" + actions.size());

        float yaw = face.getHeadEulerAngleY();
        if (Math.abs(yaw) < CENTER_DEGREES) centered = true;
        boolean done = false;
        switch (actions.get(step)) {
            case BLINK:
                Float left = face.getLeftEyeOpenProbability(), right = face.getRightEyeOpenProbability();
                if (left == null || right == null) break;
                if (left > EYE_OPEN && right > EYE_OPEN) {
                    if (eyesClosed) done = true;
                    eyesWereOpen = true;
                } else if (eyesWereOpen && left < EYE_CLOSED && right < EYE_CLOSED) {
                    eyesClosed = true;
                }
                break;
            case TURN_LEFT: done = centered && yaw > TURN_DEGREES; break;
            default: done = centered && yaw < -TURN_DEGREES;
        }
        if (!done) return;
        step++;
        resetStep();
        if (step < actions.size()) { showStep(); return; }
        finished = true;
        verifiedUser = getIntent().getStringExtra("user");
        verifiedUntil = SystemClock.elapsedRealtime() + VERIFIED_FOR_MS;
        setResult(RESULT_OK);
        finish();
    }

    @Override protected void onDestroy() {
        detector.close();
        analysis.shutdown();
        super.onDestroy();
    }
}
