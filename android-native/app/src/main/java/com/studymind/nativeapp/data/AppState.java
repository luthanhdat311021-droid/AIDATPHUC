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

    public enum Screen { DASHBOARD, IMPORT, HISTORY, WORKSPACE, PREREQUISITE, GAPS, MINDMAP, FLASHCARD, QUIZ, FUSION, PRO, ADMIN }

    public interface Listener { void onStateChanged(); }

    public interface Host {
        void navigate(Screen screen);
        void toast(String message);
        void openUpgrade(String reason);
        void announceBadges(List<Gamification.Badge> badges);
    }

    /** Callback that only needs to know when the call finished */
    public interface Done { void done(boolean ok); }

    private final AuthRepository auth;
    private final ApiClient api;
    private final SharedPreferences prefs;
    private final Set<Listener> listeners = new HashSet<>();
    private Host host;

    private ActiveDocument activeDoc;
    private UserStats stats;
    private List<DocumentItem> history = new ArrayList<>();
    private BillingInfo billing;
    private FusionResult fusionResult;

    public AppState(Context context, AuthRepository auth, ApiClient api) {
        this.auth = auth;
        this.api = api;
        this.prefs = context.getSharedPreferences("studymind_prefs", Context.MODE_PRIVATE);
        api.setUpgradeListener(reason -> { if (host != null) host.openUpgrade(reason); });
    }

    // ---------- observation ----------

    public void setHost(Host h) { host = h; }

    public void addListener(Listener l) { listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() { for (Listener l : new ArrayList<>(listeners)) l.onStateChanged(); }

    private void toast(String m) { if (host != null && m != null) host.toast(m); }

    public void navigate(Screen s) { if (host != null) host.navigate(s); }

    public void openUpgrade(String reason) { if (host != null) host.openUpgrade(reason); }

    // ---------- getters ----------

    public AuthRepository auth() { return auth; }

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

    /** Import progress for the "Đang phân tích" screen (called on the main thread) */
    public interface Progress { void progress(int percent, String stage); }

    /**
     * Text or file import. Files go to Supabase Storage first (≤ 50MB), then the lesson is built in short
     * server steps — start (read the source) → analyze (a few parts per call) → finish (study pack) — so long
     * documents never hit a per-request time limit. outputs = {notes, mindmap, flashcards, quiz}: the web
     * "Sản phẩm muốn tạo" choice, kept for parity; the server always builds the full pack.
     */
    public void uploadDocument(byte[] file, String fileName, String mime, String rawText, String language, String depth, boolean[] outputs, String subject, Progress progress, Done done) {
        Async.run(() -> {
            JsonObject start = new JsonObject();
            start.addProperty("depth", depth);
            if (subject != null) start.addProperty("subject", subject);
            if (fileName != null) start.addProperty("fileName", fileName);
            if (file != null) {
                report(progress, 5, "Đang tải tệp lên máy chủ...");
                String uid = auth.getUser() != null ? auth.getUser().id : null;
                if (uid == null) throw new ApiException(401, "Vui lòng đăng nhập để tiếp tục.", null);
                String safe = fileName.replaceAll("[^\\w.\\-]", "_");
                String path = uid + "/" + System.currentTimeMillis() + "-" + safe;
                api.uploadToStorage(UPLOAD_BUCKET, path, file, mime, false);
                start.addProperty("source", "file");
                start.addProperty("storagePath", path);
                start.addProperty("mimeType", mime);
            } else {
                start.addProperty("source", "text");
                start.addProperty("rawText", rawText);
            }
            return buildLesson(start, progress);
        }, importCallback("Đã chuyển hóa tài liệu thành công!", done));
    }

    public void processVideo(String url, String depth, String subject, Progress progress, Done done) {
        JsonObject b = new JsonObject();
        b.addProperty("source", "video");
        b.addProperty("url", url);
        b.addProperty("depth", depth);
        b.addProperty("subject", subject);
        Async.run(() -> buildLesson(b, progress), importCallback("📹 Đã trích xuất transcript từ Video!", done));
    }

    public void processUrl(String url, String depth, String subject, Progress progress, Done done) {
        JsonObject b = new JsonObject();
        b.addProperty("source", "url");
        b.addProperty("url", url);
        b.addProperty("depth", depth);
        b.addProperty("subject", subject);
        Async.run(() -> buildLesson(b, progress), importCallback("🔗 Đã trích xuất bài viết Web thành công!", done));
    }

    private static final String UPLOAD_BUCKET = "study-uploads";

    /** Blocking: runs the start → analyze → finish steps and returns the finished lesson ({document, studyPack}) */
    private JsonObject buildLesson(JsonObject start, Progress progress) throws Exception {
        report(progress, 12, "Đang đọc nội dung tài liệu...");
        JsonObject s = api.post("/api/v1/lessons/start", start);
        String id = s.get("lessonId").getAsString();
        int total = Math.max(1, s.get("totalParts").getAsInt());
        int remaining = total;
        report(progress, 20, total > 1 ? "Đang phân tích " + total + " phần của tài liệu..." : "AI đang phân tích kiến thức...");
        while (remaining > 0) {
            JsonObject a = retry(() -> api.post("/api/v1/lessons/" + id + "/analyze", null));
            int done = a.has("done") ? a.get("done").getAsInt() : total;
            remaining = a.has("remaining") ? a.get("remaining").getAsInt() : 0;
            report(progress, 20 + 55 * done / total, total > 1 ? "Đã phân tích " + done + "/" + total + " phần..." : "AI đang phân tích kiến thức...");
        }
        report(progress, 80, "Đang tạo ghi chú, sơ đồ tư duy, thẻ ghi nhớ và trắc nghiệm...");
        JsonObject r = retry(() -> api.post("/api/v1/lessons/" + id + "/finish", null));
        report(progress, 100, "Hoàn tất!");
        return r;
    }

    /** One more try for steps that are safe to repeat (a failed AI call or a dropped connection) */
    private static JsonObject retry(Async.Task<JsonObject> step) throws Exception {
        try {
            return step.run();
        } catch (ApiException e) {
            if (e.status < 500) throw e;
        } catch (java.io.IOException e) {
            // network hiccup: try once more below
        }
        return step.run();
    }

    private static void report(Progress p, int percent, String stage) {
        if (p != null) Async.main(() -> p.progress(percent, stage));
    }

    private Async.Callback<JsonObject> importCallback(String okMessage, Done done) {
        return new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                toast(okMessage);
                setActiveFromResponse(r);
                refreshStats();
                refreshHistory();
                done.done(true);
                navigate(Screen.WORKSPACE);
            }
            @Override public void onError(String m) { toast(m); refreshHistory(); done.done(false); }
        };
    }

    // ---------- chat ----------

    public interface Reply { void reply(String text); }

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
        Async.run(() -> api.post("/api/ai/chat", b).get("answer").getAsString(), new Async.Callback<String>() {
            @Override public void onSuccess(String a) { reply.reply(a); refreshBilling(null); }
            @Override public void onError(String m) { reply.reply(m); }
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

    public void reviewFlashcard(String cardId, String rating) {
        JsonObject b = new JsonObject();
        b.addProperty("rating", rating);
        Async.run(() -> api.post(docPath() + "/flashcards/" + cardId + "/review", b), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                toast(r.has("message") ? r.get("message").getAsString() : null);
                if (host != null) host.announceBadges(Gamification.recordCardReviewed());
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
                if (host != null) host.announceBadges(Gamification.recordQuizCompleted(r.get("score").getAsInt()));
            }
            @Override public void onError(String m) { toast(m); }
        });
    }

    // ---------- prerequisites ----------

    public void analyzePrerequisites(String depth, Done done) {
        JsonObject b = new JsonObject();
        b.addProperty("depth", depth);
        Async.run(() -> api.post(docPath() + "/prerequisites", b), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                JsonObject p = r.getAsJsonObject("prerequisites");
                updatePack("prerequisites", p);
                boolean fallback = p.has("isFallback") && p.get("isFallback").getAsBoolean();
                toast(fallback ? "AI đang bận, đã tạo phân tích sơ bộ. Hãy thử lại sau ít phút." : "Đã phân tích xong kiến thức tiên quyết!");
                refreshBilling(null);
                done.done(true);
            }
            @Override public void onError(String m) { toast(m); done.done(false); }
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

    public void fuse(List<String> ids, Done done) {
        JsonObject b = new JsonObject();
        b.add("documentIds", Models.GSON.toJsonTree(ids));
        Async.run(() -> api.post("/api/v1/fusion/analyze", b), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                fusionResult = Models.GSON.fromJson(r.get("data"), FusionResult.class);
                toast("✨ Đã hợp nhất & phân tích đối chiếu thành công!");
                changed();
                refreshBilling(null);
                done.done(true);
            }
            @Override public void onError(String m) { toast(m); done.done(false); }
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
