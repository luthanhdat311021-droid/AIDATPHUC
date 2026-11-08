package com.studymind.nativeapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.studymind.nativeapp.data.model.Models;
import com.studymind.nativeapp.data.model.Models.ActiveDocument;
import com.studymind.nativeapp.data.model.Models.AdminOverview;
import com.studymind.nativeapp.data.model.Models.BillingInfo;
import com.studymind.nativeapp.data.model.Models.ChatMessage;
import com.studymind.nativeapp.data.model.Models.DocumentItem;
import com.studymind.nativeapp.data.model.Models.FusionResult;
import com.studymind.nativeapp.data.model.Models.PaymentOrder;
import com.studymind.nativeapp.data.model.Models.QuizHistoryRecord;
import com.studymind.nativeapp.data.model.Models.StudyPack;
import com.studymind.nativeapp.data.model.Models.UserStats;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App-wide state and actions — the Android counterpart of the web app's StudyContext.
 * Screens observe it through {@link Listener} and call its actions; the UI host (MainActivity)
 * implements {@link Host} for navigation, toasts and the upgrade sheet.
 */
public class AppState {

    public enum Screen { DASHBOARD, IMPORT, HISTORY, WORKSPACE, PREREQUISITE, GAPS, MINDMAP, FLASHCARD, QUIZ, FUSION, PRO, REWARDS, ADMIN }

    public interface Listener { void onStateChanged(); }

    public interface Host {
        void navigate(Screen screen);
        void toast(String message);
        void openUpgrade(String reason);
        void announceBadges(List<Gamification.Badge> badges);
        /** The screen the user is looking at; null while the app is in the background */
        Screen visibleScreen();
        void askNotificationPermission();
    }

    /** Callback that only needs to know when the call finished */
    public interface Done { void done(boolean ok); }

    private final AuthRepository auth;
    private final ApiClient api;
    private final RewardsRepository rewards;
    private final SharedPreferences prefs;
    private final Set<Listener> listeners = new HashSet<>();
    private Host host;

    private ActiveDocument activeDoc;
    private UserStats stats;
    private List<DocumentItem> history = new ArrayList<>();
    private BillingInfo billing;
    private FusionResult fusionResult;
    private final AnalysisJobs jobs;

    public AppState(Context context, AuthRepository auth, ApiClient api) {
        this.auth = auth;
        this.api = api;
        this.rewards = new RewardsRepository(api, auth);
        this.prefs = context.getSharedPreferences("studymind_prefs", Context.MODE_PRIVATE);
        this.jobs = new AnalysisJobs(context);
        api.setUpgradeListener(reason -> { if (host != null) host.openUpgrade(reason); });
    }

    // ---------- observation ----------

    public void setHost(Host h) { host = h; }

    /** An activity that is going away must not receive navigation from a job that finishes later */
    public void clearHost(Host h) { if (host == h) host = null; }

    public AnalysisJobs jobs() { return jobs; }

    public void addListener(Listener l) { listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() { for (Listener l : new ArrayList<>(listeners)) l.onStateChanged(); }

    private void toast(String m) { if (host != null && m != null) host.toast(m); }

    public void navigate(Screen s) { if (host != null) host.navigate(s); }

    public void openUpgrade(String reason) { if (host != null) host.openUpgrade(reason); }

    // ---------- getters ----------

    public AuthRepository auth() { return auth; }

    public RewardsRepository rewards() { return rewards; }

    public ActiveDocument activeDoc() { return activeDoc; }

    public UserStats stats() { return stats; }

    public List<DocumentItem> history() { return history; }

    public BillingInfo billing() { return billing; }

    public boolean isPro() { return billing != null && billing.isPro; }

    public boolean isAdmin() { return billing != null && billing.isAdmin; }

    public FusionResult fusionResult() { return fusionResult; }

    public String tierLabel() { return isAdmin() ? "Quản trị viên" : isPro() ? "StudyMind Pro" : "Gói Miễn phí"; }

    public boolean isDarkMode() { return prefs.getBoolean("dark", false); }

    public void setDarkMode(boolean dark) { prefs.edit().putBoolean("dark", dark).apply(); }

    // ---------- session ----------

    /** Load everything that belongs to the signed-in account */
    public void loadAccount() {
        refreshStats();
        refreshHistory();
        refreshBilling(null);
    }

    public void logout(Runnable then) {
        Async.run(() -> { auth.signOut(); return true; }, new Async.Callback<Boolean>() {
            @Override public void onSuccess(Boolean r) { clearAccount(); toast("👋 Đã đăng xuất tài khoản!"); then.run(); }
            @Override public void onError(String m) { clearAccount(); then.run(); }
        });
    }

    /** Nothing from this account may linger for the next person on this device */
    public void clearAccount() {
        activeDoc = null;
        stats = null;
        history = new ArrayList<>();
        billing = null;
        fusionResult = null;
        jobs.clear();
        clearPendingImport();
        changed();
    }

    // ---------- dashboard / history ----------

    public void refreshStats() {
        Async.run(() -> api.get("/api/v1/user/stats").getAsJsonObject("data"), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject d) { stats = Models.GSON.fromJson(d, UserStats.class); changed(); }
            @Override public void onError(String m) { /* dashboard shows its empty state */ }
        });
    }

    public void refreshHistory() {
        Async.run(() -> api.get("/api/v1/history").getAsJsonArray("data"), new Async.Callback<JsonArray>() {
            @Override public void onSuccess(JsonArray d) {
                history = Models.GSON.fromJson(d, new TypeToken<List<DocumentItem>>() {}.getType());
                changed();
            }
            @Override public void onError(String m) { toast(m); }
        });
    }

    public void deleteLesson(String id) {
        Async.run(() -> api.delete("/api/v1/history/" + id), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                toast("Đã xóa bài học khỏi lịch sử!");
                if (activeDoc != null && activeDoc.document.id.equals(id)) activeDoc = null;
                refreshHistory();
                refreshStats();
            }
            @Override public void onError(String m) { toast(m); }
        });
    }

    /** Open a lesson and show it in the workspace */
    public void openDocument(String id, Screen then) {
        Async.run(() -> api.get("/api/v1/documents/" + id), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                setActiveFromResponse(r);
                if (then != null) navigate(then);
            }
            @Override public void onError(String m) { toast(m); }
        });
    }

    private void reloadActive() {
        if (activeDoc != null) openDocument(activeDoc.document.id, null);
    }

    private void setActiveFromResponse(JsonObject r) {
        DocumentItem doc = Models.GSON.fromJson(r.getAsJsonObject("document"), DocumentItem.class);
        JsonObject pack = r.has("studyPack") && r.get("studyPack").isJsonObject() ? r.getAsJsonObject("studyPack") : new JsonObject();
        List<QuizHistoryRecord> qh = new ArrayList<>();
        if (r.has("quizHistory") && r.get("quizHistory").isJsonArray()) {
            qh = Models.GSON.fromJson(r.get("quizHistory"), new TypeToken<List<QuizHistoryRecord>>() {}.getType());
        }
        activeDoc = new ActiveDocument(doc, new StudyPack(pack), qh);
        changed();
        refreshBilling(null); // AI usage counters move with every generation
    }

    private void updatePack(String key, JsonElement value) {
        if (activeDoc == null) return;
        activeDoc = new ActiveDocument(activeDoc.document, activeDoc.pack.with(key, value), activeDoc.quizHistory);
        changed();
    }

    // ---------- import ----------

    /** Builds the lessons/start request on a worker thread (reading and uploading the file happens here) */
    public interface StartBody { JsonObject build(AnalysisJobs.Job job) throws Exception; }

    private static final String UPLOAD_BUCKET = "study-uploads";
    private static final String PENDING_IMPORT = "pending_import";

    /** Null when a job of this kind is already running */
    private AnalysisJobs.Job beginJob(AnalysisJobs.Kind kind, String title, String stage) {
        if (jobs.isRunning(kind)) {
            toast("Một phân tích cùng loại đang chạy ngầm. Vui lòng đợi xong rồi thử lại.");
            return null;
        }
        if (host != null) host.askNotificationPermission();
        return jobs.start(kind, title, stage);
    }

    private Screen visibleScreen() { return host != null ? host.visibleScreen() : null; }

    /**
     * File import. Files go to Supabase Storage first (≤ 50MB), then the lesson is built in short server
     * steps — see {@link #runLessonSteps}. The resolver and name are captured here so the work does not depend on the screen.
     */
    public void importFile(android.content.ContentResolver resolver, android.net.Uri uri, String displayName, String stage, String depth, String subject) {
        runImport(displayName, stage, "Đã chuyển hóa tài liệu thành công!", job -> {
            FileIngest.Prepared p = FileIngest.prepare(resolver, uri);
            JsonObject start = new JsonObject();
            start.addProperty("depth", depth);
            if (subject != null) start.addProperty("subject", subject);
            start.addProperty("fileName", p.fileName);
            if (p.binary != null) {
                report(job, 5, "Đang tải tệp lên máy chủ...");
                String uid = auth.getUser() != null ? auth.getUser().id : null;
                if (uid == null) throw new ApiException(401, "Vui lòng đăng nhập để tiếp tục.", null);
                String safe = p.fileName.replaceAll("[^\\w.\\-]", "_");
                String path = uid + "/" + System.currentTimeMillis() + "-" + safe;
                api.uploadToStorage(UPLOAD_BUCKET, path, p.binary, p.mime, false);
                start.addProperty("source", "file");
                start.addProperty("storagePath", path);
                start.addProperty("mimeType", p.mime);
            } else {
                start.addProperty("source", "text");
                start.addProperty("rawText", p.text);
            }
            return start;
        });
    }

    public void importText(String rawText, String depth, String subject) {
        String t = rawText.trim();
        runImport(t.length() > 40 ? t.substring(0, 40) + "..." : t, "Đang chuẩn bị...", "Đã chuyển hóa tài liệu thành công!", job -> {
            JsonObject start = new JsonObject();
            start.addProperty("depth", depth);
            if (subject != null) start.addProperty("subject", subject);
            start.addProperty("source", "text");
            start.addProperty("rawText", rawText);
            return start;
        });
    }

    public void processVideo(String url, String depth, String subject) {
        runImport(url, "Đang chuẩn bị...", "📹 Đã trích xuất transcript từ Video!", job -> linkBody("video", url, depth, subject));
    }

    public void processUrl(String url, String depth, String subject) {
        runImport(url, "Đang chuẩn bị...", "🔗 Đã trích xuất bài viết Web thành công!", job -> linkBody("url", url, depth, subject));
    }

    private static JsonObject linkBody(String source, String url, String depth, String subject) {
        JsonObject b = new JsonObject();
        b.addProperty("source", source);
        b.addProperty("url", url);
        b.addProperty("depth", depth);
        b.addProperty("subject", subject);
        return b;
    }

    private void runImport(String title, String stage, String okMessage, StartBody body) {
        AnalysisJobs.Job job = beginJob(AnalysisJobs.Kind.IMPORT, title, stage);
        if (job == null) return;
        Async.run(() -> {
            JsonObject start = body.build(job);
            report(job, 12, "Đang đọc nội dung tài liệu...");
            JsonObject s = api.post("/api/v1/lessons/start", start);
            String id = s.get("lessonId").getAsString();
            int total = Math.max(1, s.get("totalParts").getAsInt());
            String serverTitle = s.has("title") ? s.get("title").getAsString() : title;
            Async.main(() -> job.title = serverTitle);
            savePendingImport(id, serverTitle, total, okMessage);
            return runLessonSteps(job, id, total);
        }, importCallback(job, okMessage));
    }

    /** Blocking: analyze (a few parts per call) → finish (study pack). The server keeps progress, so this can continue after any failure. */
    private JsonObject runLessonSteps(AnalysisJobs.Job job, String id, int total) throws Exception {
        try {
            int remaining = total;
            report(job, 20, total > 1 ? "Đang phân tích " + total + " phần của tài liệu..." : "AI đang phân tích kiến thức...");
            while (remaining > 0) {
                if (job.cancelled) throw new java.io.IOException("cancelled");
                JsonObject a = retry(() -> api.post("/api/v1/lessons/" + id + "/analyze", null));
                int done = a.has("done") ? a.get("done").getAsInt() : total;
                remaining = a.has("remaining") ? a.get("remaining").getAsInt() : 0;
                report(job, 20 + 55 * Math.min(done, total) / total, total > 1 ? "Đã phân tích " + done + "/" + total + " phần..." : "AI đang phân tích kiến thức...");
            }
            if (job.cancelled) throw new java.io.IOException("cancelled");
            report(job, 80, "Đang tạo ghi chú, sơ đồ tư duy, thẻ ghi nhớ và trắc nghiệm...");
            return retry(() -> api.post("/api/v1/lessons/" + id + "/finish", null));
        } catch (ApiException e) {
            // The lesson is gone or refused: continuing later cannot help. 401 can be cured by signing in again.
            if (e.status < 500 && e.status != 401) clearPendingImport();
            throw e;
        }
    }

    /** Analyze and finish are safe to repeat and use no quota, so ride out dropped connections and busy AI providers */
    private static JsonObject retry(Async.Task<JsonObject> step) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return step.run();
            } catch (ApiException e) {
                if (e.status < 500 || attempt == 3) throw e;
            } catch (java.io.IOException e) {
                if (attempt == 3) throw e;
            }
            Thread.sleep(2000L * attempt);
        }
    }

    private void report(AnalysisJobs.Job job, int percent, String stage) {
        Async.main(() -> jobs.progress(job, percent, stage));
    }

    private Async.Callback<JsonObject> importCallback(AnalysisJobs.Job job, String okMessage) {
        return new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                if (job.cancelled) return;
                clearPendingImport();
                refreshStats();
                refreshHistory();
                String id = r.getAsJsonObject("document").get("id").getAsString();
                Screen visible = visibleScreen();
                if (visible == Screen.IMPORT) {
                    // Still watching this screen: go straight to the lesson
                    jobs.finish(job, true, okMessage, id, true);
                    jobs.dismiss(job);
                    toast(okMessage);
                    setActiveFromResponse(r);
                    navigate(Screen.WORKSPACE);
                } else {
                    // Studying something else: never swap the open lesson; the banner / notification offers it
                    jobs.finish(job, true, okMessage, id, visible != null);
                    refreshBilling(null);
                }
            }
            @Override public void onError(String m) {
                if (job.cancelled) return;
                refreshHistory();
                job.resumable = prefs.contains(PENDING_IMPORT);
                jobs.finish(job, false, m, null, visibleScreen() != null);
            }
        };
    }

    private void savePendingImport(String lessonId, String title, int total, String okMessage) {
        if (auth.getUser() == null) return;
        JsonObject p = new JsonObject();
        p.addProperty("lessonId", lessonId);
        p.addProperty("title", title);
        p.addProperty("total", total);
        p.addProperty("okMessage", okMessage);
        p.addProperty("user", auth.getUser().id);
        prefs.edit().putString(PENDING_IMPORT, p.toString()).apply();
    }

    private void clearPendingImport() { prefs.edit().remove(PENDING_IMPORT).apply(); }

    /** Continues a lesson whose analysis was cut off (the phone killed the app, or a step failed). No quota is used again. */
    public void resumePendingImport() {
        String saved = prefs.getString(PENDING_IMPORT, null);
        if (saved == null || jobs.get(AnalysisJobs.Kind.IMPORT) != null) return;
        JsonObject p;
        try {
            p = com.google.gson.JsonParser.parseString(saved).getAsJsonObject();
        } catch (RuntimeException e) {
            clearPendingImport();
            return;
        }
        if (auth.getUser() == null) return;
        if (!auth.getUser().id.equals(p.get("user").getAsString())) { clearPendingImport(); return; }
        String id = p.get("lessonId").getAsString();
        int total = p.get("total").getAsInt();
        AnalysisJobs.Job job = jobs.start(AnalysisJobs.Kind.IMPORT, p.get("title").getAsString(), "Đang tiếp tục phân tích...");
        Async.run(() -> runLessonSteps(job, id, total), importCallback(job, p.get("okMessage").getAsString()));
    }

    /** Banner / notification tap: open the result, retry a failure, or show the screen of a running job */
    public void openJob(AnalysisJobs.Job job) {
        Screen screen = job.kind == AnalysisJobs.Kind.IMPORT ? Screen.IMPORT : job.kind == AnalysisJobs.Kind.PREREQUISITE ? Screen.PREREQUISITE : Screen.FUSION;
        if (job.running()) { navigate(screen); return; }
        if (job.status == AnalysisJobs.Status.ERROR) {
            if (job.resumable) {
                jobs.dismiss(job);
                resumePendingImport();
            } else {
                if (job.kind != AnalysisJobs.Kind.IMPORT) jobs.dismiss(job); // the import screen shows the error itself
                navigate(screen);
            }
            return;
        }
        jobs.dismiss(job);
        if (job.kind == AnalysisJobs.Kind.FUSION) navigate(screen);
        else openDocument(job.documentId, job.kind == AnalysisJobs.Kind.IMPORT ? Screen.WORKSPACE : screen);
    }

    public void dismissJob(AnalysisJobs.Job job) {
        if (job.kind == AnalysisJobs.Kind.IMPORT) clearPendingImport();
        jobs.dismiss(job);
    }

    // ---------- chat ----------

    public interface Reply { void reply(String text, List<String> sources); }

    public void chat(String question, List<ChatMessage> history, String socraticFocus, Reply reply) {
        if (activeDoc == null) return;
        JsonObject b = new JsonObject();
        b.addProperty("documentId", activeDoc.document.id);
        b.addProperty("question", question);
        JsonArray h = new JsonArray();
        for (ChatMessage m : history) {
            JsonObject o = new JsonObject();
            o.addProperty("sender", m.fromUser ? "user" : "ai");
            o.addProperty("text", m.text);
            h.add(o);
        }
        b.add("chatHistory", h);
        if (socraticFocus != null) {
            b.addProperty("mode", "socratic");
            b.addProperty("focus", socraticFocus);
        }
        Async.run(() -> api.post("/api/ai/chat", b), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                List<String> sources = new ArrayList<>();
                if (r.has("sources") && r.get("sources").isJsonArray()) {
                    for (com.google.gson.JsonElement e : r.getAsJsonArray("sources")) {
                        JsonObject s = e.getAsJsonObject();
                        sources.add("[" + s.get("n").getAsInt() + "] " + s.get("excerpt").getAsString());
                    }
                }
                reply.reply(r.get("answer").getAsString(), sources);
                refreshBilling(null);
            }
            @Override public void onError(String m) { reply.reply(m, new ArrayList<>()); }
        });
    }

    // ---------- mindmap ----------

    private String docPath() { return "/api/v1/documents/" + activeDoc.document.id; }

    public void addMindmapNode(String label, String detail, String parentId) { addMindmapNode(label, detail, parentId, "concept", 3); }

    public void addMindmapNode(String label, String detail, String parentId, String type, int importance) {
        if (activeDoc == null) return;
        JsonObject b = new JsonObject();
        b.addProperty("label", label);
        b.addProperty("detail", detail);
        b.addProperty("summary", detail);
        b.addProperty("type", type);
        b.addProperty("importance", importance);
        b.addProperty("parentId", parentId != null ? parentId : "root-node");
        simple(() -> api.post(docPath() + "/mindmap/nodes", b), "🌿 Đã thêm nút mới: \"" + label + "\"", r -> updatePack("mindmap", r.get("mindmap")));
    }

    public void updateMindmapNode(String nodeId, String label, String detail) { updateMindmapNode(nodeId, label, detail, null, 0); }

    public void updateMindmapNode(String nodeId, String label, String detail, String type, int importance) {
        JsonObject b = new JsonObject();
        b.addProperty("label", label);
        b.addProperty("detail", detail);
        b.addProperty("summary", detail);
        if (type != null) b.addProperty("type", type);
        if (importance > 0) b.addProperty("importance", importance);
        simple(() -> api.put(docPath() + "/mindmap/nodes/" + nodeId, b), "Đã cập nhật nút sơ đồ tư duy!", r -> updatePack("mindmap", r.get("mindmap")));
    }

    public void deleteMindmapNode(String nodeId) {
        simple(() -> api.delete(docPath() + "/mindmap/nodes/" + nodeId), "Đã xóa nút sơ đồ tư duy!", r -> updatePack("mindmap", r.get("mindmap")));
    }

    public void expandMindmapNode(Models.MindmapNode node, Done done) {
        toast("Đang mở rộng và phân tích sâu nút \"" + node.label + "\"...");
        JsonObject b = new JsonObject();
        b.add("node", Models.GSON.toJsonTree(node));
        Async.run(() -> api.post(docPath() + "/mindmap/expand", b), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                toast("Đã đào sâu và tạo các nhánh con cho \"" + node.label + "\"!");
                updatePack("mindmap", r.get("mindmap"));
                refreshBilling(null);
                done.done(true);
            }
            @Override public void onError(String m) { toast(m); done.done(false); }
        });
    }

    // ---------- flashcards ----------

    public void addFlashcard(String front, String back) { addFlashcard(front, back, "medium"); }

    public void addFlashcard(String front, String back, String difficulty) {
        JsonObject b = new JsonObject();
        b.addProperty("front", front);
        b.addProperty("back", back);
        b.addProperty("difficulty", difficulty);
        simple(() -> api.post(docPath() + "/flashcards", b), "Đã tạo thẻ ghi nhớ mới thành công!", r -> updatePack("flashcards", r.get("flashcards")));
    }

    public void deleteFlashcard(String cardId) {
        simple(() -> api.delete(docPath() + "/flashcards/" + cardId), "Đã xóa thẻ ghi nhớ!", r -> reloadActive());
    }

    private static boolean hasGamification(JsonObject r) {
        return r != null && r.has("gamification") && r.get("gamification").isJsonObject();
    }

    /** kind: open, pomodoro or freeze. On failure the device keeps its local copy. */
    public void syncGamification(String kind) {
        JsonObject b = new JsonObject();
        b.addProperty("kind", kind);
        if ("open".equals(kind)) {
            JsonObject local = Gamification.localSnapshot();
            b.add("localImport", local);
            if (local.has("ownerId")) b.add("localOwnerId", local.get("ownerId"));
        }
        Async.run(() -> api.post("/api/v1/gamification/event", b), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                if (!hasGamification(r)) return;
                Gamification.applyServer(r.getAsJsonObject("gamification"));
                if (host != null) host.announceBadges(new ArrayList<>()); // refreshes the streak counter
            }
            @Override public void onError(String m) {}
        });
    }

    public void reviewFlashcard(String cardId, String rating) {
        JsonObject b = new JsonObject();
        b.addProperty("rating", rating);
        Async.run(() -> api.post(docPath() + "/flashcards/" + cardId + "/review", b), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                String message = r.has("message") ? r.get("message").getAsString() : null;
                toast(rewardCounted(r) && message != null ? message + " · +1 tiến độ nhiệm vụ StudyCoin" : message);
                if (host != null) host.announceBadges(hasGamification(r) ? Gamification.applyServer(r.getAsJsonObject("gamification")) : Gamification.recordCardReviewed());
                // Keep the saved rating locally (no refetch per card); the Knowledge Gap Map reads it
                JsonArray cards = activeDoc.pack.raw.has("flashcards") && activeDoc.pack.raw.get("flashcards").isJsonArray()
                        ? activeDoc.pack.raw.getAsJsonArray("flashcards").deepCopy() : new JsonArray();
                for (int i = 0; i < cards.size(); i++) {
                    JsonObject c = cards.get(i).getAsJsonObject();
                    if (c.has("id") && c.get("id").getAsString().equals(cardId)) cards.set(i, r.get("card"));
                }
                updatePack("flashcards", cards);
            }
            @Override public void onError(String m) { toast(m); }
        });
    }

    public void regenerateFlashcards(Done done) {
        toast("Đang khởi tạo 10-12 Thẻ ghi nhớ mới...");
        Async.run(() -> api.post(docPath() + "/regenerate-flashcards", new JsonObject()), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) { toast("Đã sinh mới Thẻ ghi nhớ thành công!"); updatePack("flashcards", r.get("flashcards")); refreshBilling(null); done.done(true); }
            @Override public void onError(String m) { toast(m); done.done(false); }
        });
    }

    // ---------- quiz ----------

    public void regenerateQuiz(Done done) {
        toast("Đang biên soạn 10-12 câu hỏi trắc nghiệm mới...");
        Async.run(() -> api.post(docPath() + "/regenerate-quiz", new JsonObject()), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) { toast("Đã sinh mới câu hỏi trắc nghiệm thành công!"); updatePack("quiz", r.get("quiz")); refreshBilling(null); done.done(true); }
            @Override public void onError(String m) { toast(m); done.done(false); }
        });
    }

    public void addQuizQuestion(String text, List<String> options, int correctIndex, String explanation) {
        JsonObject b = new JsonObject();
        b.addProperty("questionText", text);
        b.add("options", Models.GSON.toJsonTree(options));
        b.addProperty("correctIndex", correctIndex);
        b.addProperty("explanation", explanation);
        simple(() -> api.post(docPath() + "/quiz/questions", b), "❓ Đã thêm câu hỏi trắc nghiệm mới!", r -> updatePack("quiz", r.get("quiz")));
    }

    public interface QuizScore { void score(int scorePct, int correct, int total, String feedback); }

    /** The server reports whether this lesson activity also advanced a StudyCoin mission. */
    private static boolean rewardCounted(JsonObject r) {
        if (r == null || !r.has("reward") || !r.get("reward").isJsonObject()) return false;
        JsonObject reward = r.getAsJsonObject("reward");
        return reward.has("counted") && reward.get("counted").isJsonPrimitive() && reward.get("counted").getAsBoolean();
    }

    public void submitQuiz(Map<String, Integer> answers, QuizScore cb) {
        JsonObject b = new JsonObject();
        JsonObject a = new JsonObject();
        for (Map.Entry<String, Integer> e : answers.entrySet()) a.addProperty(e.getKey(), e.getValue());
        b.add("answers", a);
        Async.run(() -> api.post("/api/v1/quiz/" + activeDoc.document.id + "/submit", b), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                cb.score(r.get("score").getAsInt(), r.get("correctCount").getAsInt(), r.get("totalQuestions").getAsInt(),
                        r.has("feedback") ? r.get("feedback").getAsString() : "");
                refreshStats();
                reloadActive(); // fresh quizHistory for the Knowledge Gap Map
                if (rewardCounted(r)) toast("⭐ Bài quiz đã được tính vào nhiệm vụ StudyCoin.");
                if (host != null) host.announceBadges(hasGamification(r) ? Gamification.applyServer(r.getAsJsonObject("gamification")) : Gamification.recordQuizCompleted(r.get("score").getAsInt()));
            }
            @Override public void onError(String m) { toast(m); }
        });
    }

    // ---------- prerequisites ----------

    public void analyzePrerequisites(String depth) {
        if (activeDoc == null) return;
        String docId = activeDoc.document.id;
        AnalysisJobs.Job job = beginJob(AnalysisJobs.Kind.PREREQUISITE, activeDoc.document.title, "AI đang phân tích kiến thức tiên quyết...");
        if (job == null) return;
        job.documentId = docId;
        jobs.setCreepCap(job, 92);
        JsonObject b = new JsonObject();
        b.addProperty("depth", depth);
        Async.run(() -> api.post("/api/v1/documents/" + docId + "/prerequisites", b), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                if (job.cancelled) return;
                JsonObject p = r.getAsJsonObject("prerequisites");
                boolean sameDoc = activeDoc != null && activeDoc.document.id.equals(docId);
                // Saved on the server either way; another open lesson must not receive this result
                if (sameDoc) updatePack("prerequisites", p);
                boolean fallback = p.has("isFallback") && p.get("isFallback").getAsBoolean();
                String message = fallback ? "AI đang bận, đã tạo phân tích sơ bộ. Hãy thử lại sau ít phút." : "Đã phân tích xong kiến thức tiên quyết!";
                refreshBilling(null);
                Screen visible = visibleScreen();
                boolean watching = sameDoc && visible == Screen.PREREQUISITE;
                jobs.finish(job, true, message, docId, visible != null);
                if (watching) {
                    jobs.dismiss(job);
                    toast(message);
                }
            }
            @Override public void onError(String m) {
                if (!job.cancelled) jobs.finish(job, false, m, null, visibleScreen() != null);
            }
        });
    }

    public void submitDiagnostic(Map<String, Integer> answers) {
        JsonObject b = new JsonObject();
        JsonObject a = new JsonObject();
        for (Map.Entry<String, Integer> e : answers.entrySet()) a.addProperty(e.getKey(), e.getValue());
        b.add("answers", a);
        simple(() -> api.post(docPath() + "/prerequisites/diagnostic", b), null, r -> updatePack("prerequisites", r.get("prerequisites")));
    }

    // ---------- fusion ----------

    public void fuse(List<String> ids) {
        AnalysisJobs.Job job = beginJob(AnalysisJobs.Kind.FUSION, "Hợp nhất " + ids.size() + " tài liệu", "AI đang phân tích đối chiếu...");
        if (job == null) return;
        jobs.setCreepCap(job, 92);
        JsonObject b = new JsonObject();
        b.add("documentIds", Models.GSON.toJsonTree(ids));
        Async.run(() -> api.post("/api/v1/fusion/analyze", b), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                if (job.cancelled) return;
                fusionResult = Models.GSON.fromJson(r.get("data"), FusionResult.class);
                String message = "✨ Đã hợp nhất & phân tích đối chiếu thành công!";
                Screen visible = visibleScreen();
                jobs.finish(job, true, message, null, visible != null);
                if (visible == Screen.FUSION) {
                    jobs.dismiss(job);
                    toast(message);
                }
                changed();
                refreshBilling(null);
            }
            @Override public void onError(String m) {
                if (!job.cancelled) jobs.finish(job, false, m, null, visibleScreen() != null);
            }
        });
    }

    // ---------- StudyMind Pro ----------

    public void refreshBilling(Done done) {
        Async.run(() -> api.get(ApiClient.billing("/me")).getAsJsonObject("data"), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject d) { billing = Models.GSON.fromJson(d, BillingInfo.class); changed(); if (done != null) done.done(true); }
            @Override public void onError(String m) { if (done != null) done.done(false); }
        });
    }

    public void createOrder(String planId, Async.Callback<PaymentOrder> cb) {
        JsonObject b = new JsonObject();
        b.addProperty("planId", planId);
        Async.run(() -> Models.GSON.fromJson(api.post(ApiClient.billing("/orders"), b).get("order"), PaymentOrder.class), cb);
    }

    public void getOrder(String orderId, Async.Callback<PaymentOrder> cb) {
        Async.run(() -> Models.GSON.fromJson(api.get(ApiClient.billing("/orders/" + orderId)).get("order"), PaymentOrder.class), new Async.Callback<PaymentOrder>() {
            @Override public void onSuccess(PaymentOrder o) {
                if ("paid".equals(o.status)) refreshBilling(null);
                cb.onSuccess(o);
            }
            @Override public void onError(String m) { cb.onError(m); }
        });
    }

    public void listOrders(Async.Callback<List<PaymentOrder>> cb) {
        Async.run(() -> Models.GSON.<List<PaymentOrder>>fromJson(api.get(ApiClient.billing("/orders")).get("orders"),
                new TypeToken<List<PaymentOrder>>() {}.getType()), cb);
    }

    public void adminOverview(Async.Callback<AdminOverview> cb) {
        Async.run(() -> Models.GSON.fromJson(api.get(ApiClient.billing("/admin/overview")).get("data"), AdminOverview.class), cb);
    }

    public void adminSetPro(String email, int days, Done done) {
        JsonObject b = new JsonObject();
        b.addProperty("email", email);
        b.addProperty("days", days);
        Async.run(() -> api.post(ApiClient.billing("/admin/members"), b), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                toast(days > 0 ? "Đã cộng " + days + " ngày Pro cho " + email : "Đã thu hồi Pro của " + email);
                done.done(true);
            }
            @Override public void onError(String m) { toast(m); done.done(false); }
        });
    }

    // ---------- profile ----------

    public void updateProfile(String fullName, String avatarUrl, Done done) {
        Async.run(() -> { auth.updateProfile(fullName, avatarUrl); return true; }, new Async.Callback<Boolean>() {
            @Override public void onSuccess(Boolean r) { toast("Đã cập nhật hồ sơ cá nhân thành công!"); done.done(true); }
            @Override public void onError(String m) { toast(m); done.done(false); }
        });
    }

    /** "Tải ảnh từ máy": stored in the public "avatars" bucket under the user's own folder */
    public void uploadAvatar(byte[] jpeg, Async.Callback<String> cb) {
        Async.run(() -> {
            String uid = auth.getUser() != null ? auth.getUser().id : null;
            if (uid == null) throw new ApiException(401, "Vui lòng đăng nhập để tiếp tục.", null);
            // A new name each time, so cached copies of the previous picture are never shown
            String path = uid + "/avatar-" + System.currentTimeMillis() + ".jpg";
            api.uploadToStorage("avatars", path, jpeg, "image/jpeg", true);
            return ApiClient.publicUrl("avatars", path);
        }, new Async.Callback<String>() {
            @Override public void onSuccess(String url) { toast("Tải ảnh đại diện thành công!"); cb.onSuccess(url); }
            @Override public void onError(String m) { toast("Lỗi tải ảnh đại diện!"); cb.onError(m); }
        });
    }

    // ---------- helpers ----------

    private interface OnJson { void on(JsonObject r); }

    private void simple(Async.Task<JsonObject> task, String okMessage, OnJson then) {
        Async.run(task, new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) { toast(okMessage); then.on(r); }
            @Override public void onError(String m) { toast(m); }
        });
    }
}
