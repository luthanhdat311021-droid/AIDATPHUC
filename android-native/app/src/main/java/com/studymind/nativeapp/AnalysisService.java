package com.studymind.nativeapp;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import com.studymind.nativeapp.data.AnalysisJobs;
import com.studymind.nativeapp.data.AnalysisNotifier;

/**
 * Keeps the process and network alive while an analysis runs with the app in the background or the screen off.
 * The work itself lives in AppState / AnalysisJobs; this only holds the foreground state and shows progress.
 */
public class AnalysisService extends Service implements AnalysisJobs.Listener {
    private static final long WAKE_LOCK_MS = 60 * 60 * 1000L;

    private AnalysisJobs jobs;
    private PowerManager.WakeLock wakeLock;
    private String lastShown;

    @Override
    public void onCreate() {
        super.onCreate();
        jobs = StudyMindApp.state().jobs();
        jobs.addListener(this);
        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "studymind:analysis");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(WAKE_LOCK_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Required after every startForegroundService, even when the job has already finished
        AnalysisJobs.Job job = jobs.firstRunning();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(AnalysisNotifier.ONGOING_ID, AnalysisNotifier.ongoing(this, job), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(AnalysisNotifier.ONGOING_ID, AnalysisNotifier.ongoing(this, job));
        }
        if (job == null) shutDown();
        return START_NOT_STICKY; // after a process kill, AppState resumes the lesson the next time the app opens
    }

    @Override
    public void onJobsChanged() {
        AnalysisJobs.Job job = jobs.firstRunning();
        if (job == null) {
            shutDown();
            return;
        }
        String shown = job.kind.name() + job.percent + job.stage + job.title;
        if (shown.equals(lastShown)) return;
        lastShown = shown;
        getSystemService(NotificationManager.class).notify(AnalysisNotifier.ONGOING_ID, AnalysisNotifier.ongoing(this, job));
    }

    /** Android 15+ time limit for this service type; the job continues on a best-effort basis */
    @Override
    public void onTimeout(int startId, int fgsType) {
        shutDown();
    }

    private void shutDown() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        jobs.removeListener(this);
        if (wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
