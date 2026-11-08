package com.studymind.nativeapp.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.studymind.nativeapp.MainActivity;
import com.studymind.nativeapp.R;

/** System notifications and the chime for background analyses. Sound follows the phone's notification volume and Do Not Disturb. */
public final class AnalysisNotifier {
    private AnalysisNotifier() {}

    public static final String EXTRA_JOB = "analysis_job";
    public static final int ONGOING_ID = 2000;
    private static final int FINISHED_ID_BASE = 2001;
    private static final String CHANNEL_PROGRESS = "analysis_progress";
    private static final String CHANNEL_DONE = "analysis_done";

    private static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel progress = new NotificationChannel(CHANNEL_PROGRESS, "Tiến trình phân tích", NotificationManager.IMPORTANCE_LOW);
        progress.setShowBadge(false);
        nm.createNotificationChannel(progress);
        // IMPORTANCE_HIGH: heads-up banner with the default notification sound
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_DONE, "Phân tích hoàn tất", NotificationManager.IMPORTANCE_HIGH));
    }

    private static PendingIntent open(Context c, AnalysisJobs.Kind kind) {
        Intent i = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_JOB, kind.name());
        return PendingIntent.getActivity(c, kind.ordinal(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Shown by the foreground service; job is null only in the instant before the service stops */
    public static Notification ongoing(Context c, AnalysisJobs.Job job) {
        ensureChannels(c);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_sparkles)
                .setColor(ContextCompat.getColor(c, R.color.brand))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentTitle(job != null ? "Đang phân tích: " + job.title : "Đang phân tích tài liệu");
        if (job != null) {
            b.setContentText(job.stage).setProgress(100, job.percent, false).setContentIntent(open(c, job.kind));
        }
        return b.build();
    }

    static void finished(Context c, AnalysisJobs.Job job) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        if (!nm.areNotificationsEnabled()) { // permission denied: the sound is all that can still reach the user
            chime(c);
            return;
        }
        ensureChannels(c);
        boolean ok = job.status == AnalysisJobs.Status.DONE;
        Notification n = new NotificationCompat.Builder(c, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_sparkles)
                .setColor(ContextCompat.getColor(c, R.color.brand))
                .setContentTitle((ok ? "✅ Đã phân tích xong: " : "⚠️ Phân tích thất bại: ") + job.title)
                .setContentText(ok ? "Chạm để mở kết quả." : job.stage)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .setAutoCancel(true)
                .setContentIntent(open(c, job.kind))
                .build();
        try {
            nm.notify(FINISHED_ID_BASE + job.kind.ordinal(), n);
        } catch (SecurityException e) {
            chime(c);
        }
    }

    static void cancelFinished(Context c, AnalysisJobs.Kind kind) {
        NotificationManagerCompat.from(c).cancel(FINISHED_ID_BASE + kind.ordinal());
    }

    static void chime(Context c) {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(c, uri);
            if (r == null) return;
            r.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            r.play();
        } catch (RuntimeException ignored) {
            // A missing or broken ringtone must never fail the analysis
        }
    }
}
