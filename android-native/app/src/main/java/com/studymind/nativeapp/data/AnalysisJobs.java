package com.studymind.nativeapp.data;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.studymind.nativeapp.AnalysisService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Long AI analyses, held at app level so they outlive any screen. One job per kind; a finished job stays
 * here until the user opens or dismisses it. All methods run on the main thread, except that a worker
 * thread may read {@link Job#cancelled}.
 */
public final class AnalysisJobs {
    public enum Kind { IMPORT, PREREQUISITE, FUSION }

    public enum Status { RUNNING, DONE, ERROR }

    public interface Listener { void onJobsChanged(); }

    public static final class Job {
        public final Kind kind;
        public String title;
        public Status status = Status.RUNNING;
        public String stage;
        public int percent;
        public String documentId;
        /** The server kept the progress, so "Thử lại" continues instead of starting over */
        public boolean resumable;
        public volatile boolean cancelled;
        int creepCap = 20;

        Job(Kind kind, String title, String stage) {
            this.kind = kind;
            this.title = title;
            this.stage = stage;
        }

        public boolean running() { return status == Status.RUNNING; }
    }

    private static final long TICK_MS = 1000;

    private final Context app;
    private final Map<Kind, Job> jobs = new EnumMap<>(Kind.class);
    private final List<Listener> listeners = new ArrayList<>();
    private final Runnable tick = this::tick;

    public AnalysisJobs(Context context) {
        app = context.getApplicationContext();
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    public Job get(Kind kind) { return jobs.get(kind); }

    public boolean isRunning(Kind kind) {
        Job j = jobs.get(kind);
        return j != null && j.running();
    }

    public Job firstRunning() {
        for (Job j : jobs.values()) if (j.running()) return j;
        return null;
    }

    /** What the app-wide banner shows: a running job first, otherwise a result waiting to be opened */
    public Job current() {
        Job running = firstRunning();
        if (running != null) return running;
        return jobs.isEmpty() ? null : jobs.values().iterator().next();
    }

    Job start(Kind kind, String title, String stage) {
        dismiss(jobs.get(kind));
        Job job = new Job(kind, title, stage);
        job.percent = 5;
        jobs.put(kind, job);
        try {
            ContextCompat.startForegroundService(app, new Intent(app, AnalysisService.class));
        } catch (RuntimeException e) {
            // The system refused a foreground start (app not visible): the work still runs while the process lives
        }
        Async.cancel(tick);
        Async.mainDelayed(tick, TICK_MS);
        changed();
        return job;
    }

    /** A step the server confirmed; the bar then creeps toward the next step's cap */
    void progress(Job job, int percent, String stage) {
        if (job.cancelled || !job.running()) return;
        job.creepCap = percent >= 80 ? 97 : Math.min(97, percent + 12);
        job.percent = Math.max(job.percent, percent);
        job.stage = stage;
        changed();
    }

    void setCreepCap(Job job, int cap) { job.creepCap = cap; }

    /** @param foreground the app is on screen: chime only, the banner shows the result */
    void finish(Job job, boolean ok, String message, String documentId, boolean foreground) {
        job.status = ok ? Status.DONE : Status.ERROR;
        job.stage = message;
        job.documentId = documentId != null ? documentId : job.documentId;
        if (ok) job.percent = 100;
        if (foreground) AnalysisNotifier.chime(app);
        else AnalysisNotifier.finished(app, job);
        changed();
    }

    public void dismiss(Job job) {
        if (job == null || jobs.get(job.kind) != job) return;
        job.cancelled = true;
        jobs.remove(job.kind);
        AnalysisNotifier.cancelFinished(app, job.kind);
        changed();
    }

    /** Signing out: nothing from this account may linger */
    void clear() {
        for (Job j : new ArrayList<>(jobs.values())) dismiss(j);
    }

    private void tick() {
        boolean any = false;
        for (Job j : jobs.values()) {
            if (!j.running()) continue;
            any = true;
            if (j.percent < j.creepCap) j.percent += Math.max(1, Math.round((j.creepCap - j.percent) / 10f));
        }
        if (!any) return;
        changed();
        Async.mainDelayed(tick, TICK_MS);
    }

    private void changed() {
        for (Listener l : new ArrayList<>(listeners)) l.onJobsChanged();
    }
}
