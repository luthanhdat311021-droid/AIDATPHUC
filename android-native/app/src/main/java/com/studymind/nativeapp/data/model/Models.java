package com.studymind.nativeapp.data.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON shapes of the StudyMind backend, mirroring src/types/index.ts of the web app.
 * Fields are public and filled by Gson; lists default to empty so screens never null-check them.
 */
public final class Models {
    private Models() {}

    public static final Gson GSON = new Gson();

    public static class DocumentItem {
        public String id = "";
        public String title = "";
        public String fileType = "";
        public String fileSize;
        public Integer pageCount;
        public String duration;
        public String updatedAt = "";
        public String createdAt;
        public String status = "COMPLETED";
        public List<String> tags = new ArrayList<>();
        public String rawText;
        /** Only present in /history rows */
        public JsonObject studyPack;
        public List<QuizHistoryRecord> quizHistory = new ArrayList<>();
    }

    public static class WeeklyQuiz { public int current; public int target = 50; }

    public static class UserStats {
        public int totalDocuments;
        public int weeklyDocAdded;
        public String flashcardProgress = "0/0";
        public Integer retentionRatePercentage;
        public String averageQuizScore;
        public String quizScoreDiff;
        public int quizAttempts;
        public WeeklyQuiz weeklyQuizCount = new WeeklyQuiz();
        public int streakDays;
        public List<DocumentItem> recentDocuments = new ArrayList<>();
    }

    public static class NotesItem { public String label = ""; public String text = ""; }

    public static class NotesSection { public String heading = ""; public List<NotesItem> items = new ArrayList<>(); }

    public static class AINotes { public String summaryTitle; public List<NotesSection> sections = new ArrayList<>(); }

    public static class MindmapNode {
        public String id = "";
        public String label = "";
        public String shortLabel;
        public String type;
        public String summary;
        public String detail;
        public Integer importance;
        public Integer level;
        public List<String> subDetails = new ArrayList<>();
        public String parentId;
    }

    public static class MindmapEdge { public String id; public String source = ""; public String target = ""; public String type; }

    public static class MindmapMeta { public String depthLabel; public Integer defaultVisibleLevel; }

    public static class AIMindmap {
        public String rootLabel;
        public MindmapNode root;
        public List<MindmapNode> nodes = new ArrayList<>();
        public List<MindmapEdge> edges = new ArrayList<>();
        public MindmapMeta metadata;
    }

    public static class Flashcard {
        public String id = "";
        public String front = "";
        public String back = "";
        public String difficulty = "medium";
        public String conceptId;
        public String lastRating;
        public String lastReviewed;
    }

    public static class QuizQuestion {
        public String id = "";
        public int questionNumber;
        public String questionText = "";
        public List<String> options = new ArrayList<>();
        public int correctIndex;
        public String explanation = "";
        public String conceptId;
    }

    public static class AIQuiz {
        public String title = "Đề trắc nghiệm AI";
        public String subject = "";
        public int timeLimitMinutes = 15;
        public List<QuizQuestion> questions = new ArrayList<>();
    }

    public static class Prerequisite {
        public String id = "";
        public String concept = "";
        public String priority = "recommended";
        public String level = "";
        public String whyNeeded = "";
        public String consequenceIfMissing = "";
    }

    public static class PrerequisiteDependency {
        public String from = "";
        public String fromType = "core"; // prerequisite | core | application
        public String to = "";
        public String toType = "core";
        public String relation = "";
    }

    public static class DiagnosticQuestion {
        public String id = "";
        public String testedPrerequisiteId;
        public String question = "";
        public List<String> options = new ArrayList<>();
        public int correctIndex;
        public String explanation = "";
    }

    public static class DiagnosticPreTest {
        public String title = "";
        public String instructions = "";
        public int passScore;
        public List<DiagnosticQuestion> questions = new ArrayList<>();
    }

    public static class Bottleneck { public String concept = ""; public String description = ""; public String advice = ""; }

    public static class LearningStep { public int step; public String type = ""; public String title = ""; public String description = ""; }

    public static class BridgeItem { public String prerequisiteId; public String concept = ""; public String quickReview = ""; }

    public static class DiagnosticResult { public String questionId = ""; public String prerequisiteId; public boolean correct; }

    public static class LastDiagnostic { public String completedAt = ""; public List<DiagnosticResult> results = new ArrayList<>(); }

    public static class PrerequisiteAnalysis {
        public boolean isFallback;
        public String analysisDepth;
        public String documentTitle = "";
        public String subjectArea = "";
        public String targetAudienceLevel = "";
        public String overallReadinessNote = "";
        public List<Prerequisite> prerequisites = new ArrayList<>();
        public List<PrerequisiteDependency> dependencyGraph = new ArrayList<>();
        public Bottleneck cognitiveBottleneck;
        public List<LearningStep> learningPath = new ArrayList<>();
        public List<BridgeItem> quickBridgeSummary = new ArrayList<>();
        public DiagnosticPreTest diagnosticPreTest = new DiagnosticPreTest();
        public LastDiagnostic lastDiagnostic;
    }

    public static class KbConcept { public String id = ""; public String name = ""; public String description; public Integer importance; }

    public static class KbRelationship { public String source = ""; public String target = ""; public String type = ""; }

    public static class KbMisconception { public String misconception = ""; public String correction; public String conceptId; }

    public static class KnowledgeBase {
        public List<KbConcept> concepts = new ArrayList<>();
        public List<KbRelationship> relationships = new ArrayList<>();
        public List<KbMisconception> misconceptions = new ArrayList<>();
    }

    public static class QuizResult { public String questionId = ""; public String conceptId; public boolean correct; }

    public static class QuizHistoryRecord {
        public String id = "";
        public int score;
        public int correctCount;
        public int totalQuestions;
        public String completedAt = "";
        public String feedback;
        public List<QuizResult> results = new ArrayList<>();
    }

    /**
     * Study pack kept as raw JSON. AI output varies (quiz can be [] or an object), so every part is
     * decoded on its own and a malformed part reads as "missing" instead of breaking the whole lesson.
     */
    public static class StudyPack {
        public final JsonObject raw;

        public StudyPack(JsonObject raw) { this.raw = raw != null ? raw : new JsonObject(); }

        private <T> T part(String key, Class<T> type) {
            JsonElement el = raw.get(key);
            if (el == null || el.isJsonNull() || !el.isJsonObject()) return null;
            try { return GSON.fromJson(el, type); } catch (RuntimeException e) { return null; }
        }

        public KnowledgeBase knowledgeBase() { return part("knowledgeBase", KnowledgeBase.class); }

        public AINotes notes() {
            AINotes n = part("notes", AINotes.class);
            return n != null ? n : part("note", AINotes.class);
        }

        public AIMindmap mindmap() { return part("mindmap", AIMindmap.class); }

        public AIQuiz quiz() {
            AIQuiz q = part("quiz", AIQuiz.class);
            return q != null && q.questions != null && !q.questions.isEmpty() ? q : null;
        }

        public PrerequisiteAnalysis prerequisites() {
            PrerequisiteAnalysis p = part("prerequisites", PrerequisiteAnalysis.class);
            if (p == null) return null;
            boolean empty = p.prerequisites.isEmpty() && (p.diagnosticPreTest == null || p.diagnosticPreTest.questions.isEmpty());
            return empty ? null : p;
        }

        public List<Flashcard> flashcards() {
            List<Flashcard> out = new ArrayList<>();
            JsonElement el = raw.get("flashcards");
            if (el == null || !el.isJsonArray()) return out;
            for (JsonElement c : (JsonArray) el) {
                try { out.add(GSON.fromJson(c, Flashcard.class)); } catch (RuntimeException ignored) {}
            }
            return out;
        }

        /** Copy with one part replaced (after regenerate / review calls) */
        public StudyPack with(String key, JsonElement value) {
            JsonObject copy = raw.deepCopy();
            copy.add(key, value);
            return new StudyPack(copy);
        }
    }

    public static class ActiveDocument {
        public final DocumentItem document;
        public final StudyPack pack;
        public final List<QuizHistoryRecord> quizHistory;

        public ActiveDocument(DocumentItem document, StudyPack pack, List<QuizHistoryRecord> quizHistory) {
            this.document = document;
            this.pack = pack;
            this.quizHistory = quizHistory != null ? quizHistory : new ArrayList<>();
        }
    }

    // ---- StudyMind Pro ----

    public static class PlanInfo { public String id = ""; public String name = ""; public int days = 30; public long amount; public String badge; }

    public static class Limits { public int generate; public int chat; }

    public static class BillingInfo {
        public String role = "user";
        public String plan = "free";
        public String proUntil;
        public boolean isPro;
        public boolean isAdmin;
        public boolean enabled;
        public Limits limits = new Limits();
        public Limits usage = new Limits();
        public List<PlanInfo> plans = new ArrayList<>();
    }

    public static class BankInfo { public String accountNumber = ""; public String bankCode = ""; public String accountName = ""; }

    public static class PaymentOrder {
        public String id = "";
        public String code = "";
        public String planId = "";
        public String planName;
        public int days;
        public long amount;
        public String status = "pending";
        public String createdAt = "";
        public String expiresAt = "";
        public String paidAt;
        public String qrUrl;
        public BankInfo bank;
        public String email;
    }

    public static class AdminTransaction {
        public long sepay_id;
        public String order_id;
        public long amount;
        public String content;
        public String gateway;
        public String result = "";
        public String received_at = "";
    }

    public static class AdminMember { public String id = ""; public String email; public boolean isAdmin; public boolean isPro; public String proUntil; }

    public static class AdminOverview {
        public long revenue;
        public int paidCount;
        public List<PaymentOrder> orders = new ArrayList<>();
        public List<AdminTransaction> transactions = new ArrayList<>();
        public List<AdminMember> members = new ArrayList<>();
    }

    // ---- Knowledge Fusion ----

    public static class FusionDocRef { public String id = ""; public String title = ""; public String statement = ""; }

    public static class KnowledgeConflict {
        public String topic = "";
        public FusionDocRef docA = new FusionDocRef();
        public FusionDocRef docB = new FusionDocRef();
        public String explanation = "";
        public String recommendation = "";
    }

    public static class UniqueInsight { public String docId = ""; public String docTitle = ""; public String domain; public List<String> insights = new ArrayList<>(); }

    public static class DocRef { public String id = ""; public String title = ""; }

    public static class CommonConcept { public String concept = ""; public String definition = ""; public List<String> sources = new ArrayList<>(); }

    public static class FusionResult {
        public String fusionTitle = "";
        public String unifiedSummary = "";
        public List<CommonConcept> commonConcepts = new ArrayList<>();
        public List<UniqueInsight> uniqueInsights = new ArrayList<>();
        public List<KnowledgeConflict> conflicts = new ArrayList<>();
        public List<DocRef> comparedDocs = new ArrayList<>();
        public AIMindmap mergedMindmap;
    }

    public static class AppUser {
        public final String id;
        public final String email;
        public final String fullName;
        public final String avatarUrl;

        public AppUser(String id, String email, String fullName, String avatarUrl) {
            this.id = id;
            this.email = email;
            this.fullName = fullName;
            this.avatarUrl = avatarUrl;
        }
    }

    public static class ChatMessage {
        public final boolean fromUser;
        public final String text;

        public ChatMessage(boolean fromUser, String text) {
            this.fromUser = fromUser;
            this.text = text;
        }
    }
}
