package com.studymind.nativeapp.data;

import com.studymind.nativeapp.data.model.Models.BridgeItem;
import com.studymind.nativeapp.data.model.Models.DiagnosticResult;
import com.studymind.nativeapp.data.model.Models.Flashcard;
import com.studymind.nativeapp.data.model.Models.KbConcept;
import com.studymind.nativeapp.data.model.Models.KbMisconception;
import com.studymind.nativeapp.data.model.Models.KbRelationship;
import com.studymind.nativeapp.data.model.Models.KnowledgeBase;
import com.studymind.nativeapp.data.model.Models.Prerequisite;
import com.studymind.nativeapp.data.model.Models.PrerequisiteAnalysis;
import com.studymind.nativeapp.data.model.Models.PrerequisiteDependency;
import com.studymind.nativeapp.data.model.Models.QuizHistoryRecord;
import com.studymind.nativeapp.data.model.Models.QuizQuestion;
import com.studymind.nativeapp.data.model.Models.QuizResult;
import com.studymind.nativeapp.data.model.Models.StudyPack;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Knowledge Gap Map — Java port of src/utils/knowledgeGaps.ts (same rules, same results).
 * Every wrong answer is traced back through the concept dependency graph to the deepest prerequisite
 * the learner has also shown weakness in: the root gap, not the symptom.
 */
public final class KnowledgeGaps {
    private KnowledgeGaps() {}

    public static class GapNode {
        public final String id;
        public final String label;
        public final String kind; // concept | prerequisite
        public String level;
        public String description;
        public int importance = 3;
        public int wrong;
        public int right;
        public String status = "untested"; // weak | mastered | untested

        GapNode(String id, String label, String kind) {
            this.id = id;
            this.label = label;
            this.kind = kind;
        }
    }

    public static class RootGap {
        public GapNode node;
        public List<GapNode> explains = new ArrayList<>();
        public List<List<String>> paths = new ArrayList<>();
        public List<GapNode> suspects = new ArrayList<>();
        public List<KbMisconception> misconceptions = new ArrayList<>();
        public String bridge;
    }

    public static class Analysis {
        public final Map<String, GapNode> nodes = new LinkedHashMap<>();
        public final List<String[]> requires = new ArrayList<>();
        public final List<RootGap> roots = new ArrayList<>();
        public int evidenceCount;
    }

    /** Diacritic- and punctuation-insensitive key, so "Cân bằng hóa học" matches "can bang hoa hoc" */
    public static String normalizeName(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]", "")
                .replace('đ', 'd').replace('Đ', 'd').toLowerCase();
        return n.replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static Analysis analyze(StudyPack pack, List<QuizHistoryRecord> quizHistory) {
        Analysis a = new Analysis();
        Map<String, GapNode> nodes = a.nodes;

        KnowledgeBase kb = pack.knowledgeBase();
        if (kb == null) kb = new KnowledgeBase();
        PrerequisiteAnalysis pre = pack.prerequisites();

        for (KbConcept c : safe(kb.concepts)) {
            GapNode n = add(nodes, c.id, c.name, "concept");
            n.description = c.description;
            n.importance = c.importance != null ? c.importance : 3;
        }
        if (pre != null) {
            for (Prerequisite p : safe(pre.prerequisites)) {
                GapNode n = add(nodes, p.id, p.concept, "prerequisite");
                n.level = p.level;
                n.description = p.whyNeeded;
                n.importance = "critical".equals(p.priority) ? 5 : 3;
            }
        }

        Set<String> linked = new HashSet<>();
        for (KbRelationship r : safe(kb.relationships)) {
            if ("depends_on".equals(r.type) || "type_of".equals(r.type)) link(a, linked, r.source, r.target);
            else if ("prerequisite_of".equals(r.type)) link(a, linked, r.target, r.source);
        }
        if (pre != null) {
            for (PrerequisiteDependency d : safe(pre.dependencyGraph)) link(a, linked, resolve(nodes, d.to), resolve(nodes, d.from));
        }

        // quiz_history is newest first: the latest outcome per question wins, so re-learning clears a gap
        Set<String> answered = new HashSet<>();
        Map<String, String> questionText = new HashMap<>();
        if (pack.quiz() != null) for (QuizQuestion q : pack.quiz().questions) questionText.put(q.id, q.questionText);
        for (QuizHistoryRecord attempt : safe(quizHistory)) {
            for (QuizResult r : safe(attempt.results)) {
                if (!answered.add(r.questionId)) continue;
                String id = r.conceptId != null && nodes.containsKey(r.conceptId) ? r.conceptId : conceptIn(nodes, questionText.get(r.questionId));
                record(a, id, r.correct);
            }
        }
        for (Flashcard card : pack.flashcards()) {
            if (!"hard".equals(card.lastRating) && !"easy".equals(card.lastRating)) continue;
            String id = card.conceptId != null && nodes.containsKey(card.conceptId) ? card.conceptId : conceptIn(nodes, card.front);
            record(a, id, "easy".equals(card.lastRating));
        }
        if (pre != null && pre.lastDiagnostic != null) {
            for (DiagnosticResult r : safe(pre.lastDiagnostic.results)) record(a, r.prerequisiteId, r.correct);
        }

        for (GapNode n : nodes.values()) {
            n.status = n.wrong == 0 && n.right == 0 ? "untested" : n.right > n.wrong ? "mastered" : "weak";
        }

        Map<String, List<String>> prereqsOf = new HashMap<>();
        for (String[] e : a.requires) prereqsOf.computeIfAbsent(e[0], k -> new ArrayList<>()).add(e[1]);

        Map<String, Set<String>> explains = new LinkedHashMap<>();
        Map<String, List<List<String>>> paths = new HashMap<>();
        Map<String, Set<String>> suspects = new HashMap<>();

        for (GapNode failed : new ArrayList<>(nodes.values())) {
            if (!"weak".equals(failed.status)) continue;
            // BFS upstream; parent points back toward the failed concept. Mastered prerequisites are ruled out.
            Map<String, String> parent = new HashMap<>();
            parent.put(failed.id, "");
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(failed.id);
            List<String> weakUp = new ArrayList<>();
            List<String> untestedUp = new ArrayList<>();
            while (!queue.isEmpty()) {
                String id = queue.poll();
                for (String p : prereqsOf.getOrDefault(id, new ArrayList<>())) {
                    String status = nodes.get(p).status;
                    if (parent.containsKey(p) || "mastered".equals(status)) continue;
                    parent.put(p, id);
                    queue.add(p);
                    ("weak".equals(status) ? weakUp : untestedUp).add(p);
                }
            }

            // Deepest weak prerequisites: those not sitting between the failed concept and another weak one
            Set<String> shadowed = new HashSet<>();
            for (String w : weakUp) {
                List<String> path = pathTo(parent, failed.id, w);
                for (int i = 0; i < path.size() - 1; i++) shadowed.add(path.get(i));
            }
            List<String> roots = new ArrayList<>();
            for (String w : weakUp) if (!shadowed.contains(w)) roots.add(w);

            if (roots.isEmpty()) {
                // No upstream weakness on record: the gap is this concept; untested prerequisites stay suspects
                explains.computeIfAbsent(failed.id, k -> new LinkedHashSet<>()).add(failed.id);
                List<String> self = new ArrayList<>();
                self.add(failed.id);
                paths.computeIfAbsent(failed.id, k -> new ArrayList<>()).add(self);
                suspects.computeIfAbsent(failed.id, k -> new LinkedHashSet<>()).addAll(untestedUp);
            } else {
                for (String root : roots) {
                    explains.computeIfAbsent(root, k -> new LinkedHashSet<>()).add(failed.id);
                    paths.computeIfAbsent(root, k -> new ArrayList<>()).add(pathTo(parent, failed.id, root));
                    suspects.computeIfAbsent(root, k -> new LinkedHashSet<>());
                }
            }
        }

        for (Map.Entry<String, Set<String>> e : explains.entrySet()) {
            RootGap g = new RootGap();
            g.node = nodes.get(e.getKey());
            for (String id : e.getValue()) g.explains.add(nodes.get(id));
            g.paths = paths.getOrDefault(e.getKey(), new ArrayList<>());
            List<GapNode> sus = new ArrayList<>();
            for (String id : suspects.getOrDefault(e.getKey(), new HashSet<>())) sus.add(nodes.get(id));
            sus.sort((x, y) -> y.importance - x.importance);
            g.suspects = sus.subList(0, Math.min(3, sus.size()));
            for (KbMisconception m : safe(kb.misconceptions)) if (e.getKey().equals(m.conceptId)) g.misconceptions.add(m);
            if (pre != null) {
                for (BridgeItem b : safe(pre.quickBridgeSummary)) {
                    if (e.getKey().equals(b.prerequisiteId)) { g.bridge = b.quickReview; break; }
                }
            }
            a.roots.add(g);
        }
        a.roots.sort((x, y) -> {
            if (y.explains.size() != x.explains.size()) return y.explains.size() - x.explains.size();
            if (y.node.wrong != x.node.wrong) return y.node.wrong - x.node.wrong;
            return y.node.importance - x.node.importance;
        });
        return a;
    }

    private static <T> Collection<T> safe(Collection<T> c) { return c != null ? c : new ArrayList<>(); }

    private static GapNode add(Map<String, GapNode> nodes, String id, String label, String kind) {
        GapNode n = nodes.get(id);
        if (n == null) {
            n = new GapNode(id, label, kind);
            nodes.put(id, n);
        }
        return n;
    }

    /** The AI writes graph ends as free text: exact name first, else the longest overlapping name */
    private static String resolve(Map<String, GapNode> nodes, String name) {
        String key = normalizeName(name);
        if (key.isEmpty()) return "";
        String best = "";
        int bestLen = 0;
        for (GapNode n : nodes.values()) {
            String k = normalizeName(n.label);
            if (k.equals(key)) return n.id;
            if (k.length() >= 4 && key.length() >= 4 && (k.contains(key) || key.contains(k)) && k.length() > bestLen) {
                best = n.id;
                bestLen = k.length();
            }
        }
        return !best.isEmpty() ? best : add(nodes, "name:" + key, name, "concept").id;
    }

    /** Fallback for questions/cards without a usable conceptId: the longest concept name found in the text */
    private static String conceptIn(Map<String, GapNode> nodes, String text) {
        String hay = " " + normalizeName(text) + " ";
        String best = "";
        int bestLen = 0;
        for (GapNode n : nodes.values()) {
            String k = normalizeName(n.label);
            if ("concept".equals(n.kind) && k.length() >= 3 && hay.contains(" " + k + " ") && k.length() > bestLen) {
                best = n.id;
                bestLen = k.length();
            }
        }
        return best;
    }

    private static void link(Analysis a, Set<String> linked, String from, String to) {
        String key = from + ">" + to;
        if (from == null || to == null || from.equals(to) || !a.nodes.containsKey(from) || !a.nodes.containsKey(to) || linked.contains(key)) return;
        linked.add(key);
        a.requires.add(new String[]{from, to});
    }

    private static void record(Analysis a, String id, boolean correct) {
        GapNode n = id != null && !id.isEmpty() ? a.nodes.get(id) : null;
        if (n == null) return;
        if (correct) n.right++; else n.wrong++;
        a.evidenceCount++;
    }

    private static List<String> pathTo(Map<String, String> parent, String failedId, String root) {
        List<String> path = new ArrayList<>();
        path.add(root);
        while (!path.get(0).equals(failedId)) path.add(0, parent.get(path.get(0)));
        return path;
    }
}
