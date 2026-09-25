import { createClient } from '@supabase/supabase-js';
import dotenv from 'dotenv';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const HISTORY_FILE_PATH = process.env.VERCEL 
  ? path.join('/tmp', 'history_db.json') 
  : path.join(__dirname, '..', 'history_db.json');

const supabaseUrl = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL || 'https://gwynjmlqymojrdlpzukn.supabase.co';
const supabaseAnonKey = process.env.SUPABASE_ANON_KEY || process.env.VITE_SUPABASE_ANON_KEY || 'sb_publishable_HdnEe4-ApmYewkEGVmab_Q_Gc-B1hqc';

const isSupabaseConfigured = Boolean(
  supabaseUrl && 
  supabaseAnonKey && 
  !supabaseUrl.includes('your-project') &&
  !supabaseUrl.includes('your_supabase') &&
  !supabaseAnonKey.includes('your-supabase') &&
  !supabaseAnonKey.includes('your_supabase') &&
  supabaseAnonKey.length > 20
);

export const supabase = isSupabaseConfigured
  ? createClient(supabaseUrl, supabaseAnonKey)
  : null;

// Persistent Local Disk Storage & In-Memory Fallback
function loadHistoryFromDisk() {
  const map = new Map();
  
  // Default initial document
  const defaultDoc = {
    id: "doc-1",
    title: "Sinh học Tế bào - Ty thể và Chu trình chuyển hóa",
    fileType: "PDF",
    fileSize: "2.4 MB",
    pageCount: 15,
    updatedAt: new Date().toISOString(),
    status: "COMPLETED",
    tags: ["Sinh học", "Tế bào", "Ty thể"],
    rawText: "Ty thể (Mitochondria) là bào quan chuyển hóa năng lượng chính của tế bào nhân thực. Ty thể gồm 2 lớp màng: màng ngoài trơn nhẵn chứa protein porin, màng trong gấp nếp sâu tạo các mào (cristae) chứa phức hợp ATP Synthase và chuỗi truyền electron. Chất nền (matrix) của ty thể chứa ADN vòng kép trần và Ribosome 70S nhân sơ, chứng minh nguồn gốc nội cộng sinh.",
    quizHistory: []
  };

  map.set(defaultDoc.id, defaultDoc);

  try {
    if (fs.existsSync(HISTORY_FILE_PATH)) {
      const raw = fs.readFileSync(HISTORY_FILE_PATH, 'utf-8');
      const list = JSON.parse(raw);
      if (Array.isArray(list) && list.length > 0) {
        list.forEach(item => {
          if (item && item.id) {
            map.set(item.id, item);
          }
        });
      }
    } else {
      // Save default document to disk on first run
      saveHistoryToDisk(map);
    }
  } catch (err) {
    console.error("Failed to load history_db.json:", err.message);
  }
  return map;
}

function saveHistoryToDisk(storeMap) {
  try {
    const list = Array.from(storeMap.values());
    fs.writeFileSync(HISTORY_FILE_PATH, JSON.stringify(list, null, 2), 'utf-8');
  } catch (err) {
    console.error("Failed to save history_db.json:", err.message);
  }
}

const memoryHistoryStore = loadHistoryFromDisk();

export const supabaseService = {
  isConfigured() {
    return isSupabaseConfigured;
  },

  async getAllLessonHistory() {
    if (isSupabaseConfigured && supabase) {
      try {
        const { data, error } = await supabase
          .from('learning_sessions')
          .select('*')
          .order('updated_at', { ascending: false });

        if (!error && Array.isArray(data) && data.length > 0) {
          const mapped = data.map(item => ({
            id: item.id,
            userId: 'usr-1',
            title: item.title || item.source_name || 'Bài học chưa đặt tên',
            fileType: (item.input_type || 'PDF').toUpperCase(),
            fileSize: '1.5 MB',
            pageCount: 5,
            duration: '15 phút',
            updatedAt: item.updated_at || item.created_at || new Date().toISOString(),
            createdAt: item.created_at || new Date().toISOString(),
            status: 'COMPLETED',
            tags: [item.input_type ? item.input_type.toUpperCase() : 'TÀI LIỆU'],
            rawText: item.structured_note?.summary || '',
            studyPack: {
              note: item.structured_note,
              notes: item.structured_note,
              prerequisites: item.structured_note?.prerequisites || null,
              mindmap: item.mindmap,
              flashcards: item.flashcards || [],
              quiz: item.quiz || []
            },
            quizHistory: []
          }));

          // Populate cache so fast offline fallback works
          mapped.forEach(item => memoryHistoryStore.set(item.id, item));
          return mapped;
        } else if (error) {
          console.warn('⚠️ [Supabase] Error fetching learning_sessions, fallback to disk store:', error.message);
        }
      } catch (err) {
        console.error('❌ [Supabase Exception]:', err.message);
      }
    }

    // Fallback disk/memory store
    return Array.from(memoryHistoryStore.values());
  },

  async getLessonHistoryById(id) {
    if (isSupabaseConfigured && supabase) {
      try {
        const { data, error } = await supabase
          .from('learning_sessions')
          .select('*')
          .eq('id', id)
          .maybeSingle();

        if (!error && data) {
          return {
            id: data.id,
            userId: 'usr-1',
            title: data.title || data.source_name || 'Bài học',
            fileType: (data.input_type || 'PDF').toUpperCase(),
            fileSize: '1.5 MB',
            pageCount: 5,
            duration: '15 phút',
            updatedAt: data.updated_at || data.created_at || new Date().toISOString(),
            createdAt: data.created_at || new Date().toISOString(),
            status: 'COMPLETED',
            tags: [data.input_type ? data.input_type.toUpperCase() : 'TÀI LIỆU'],
            rawText: data.structured_note?.summary || '',
            studyPack: {
              note: data.structured_note,
              notes: data.structured_note,
              prerequisites: data.structured_note?.prerequisites || null,
              mindmap: data.mindmap,
              flashcards: data.flashcards || [],
              quiz: data.quiz || []
            },
            quizHistory: []
          };
        }
      } catch (err) {
        console.warn(`⚠️ [Supabase] Could not fetch session ${id}:`, err.message);
      }
    }

    return memoryHistoryStore.get(id) || null;
  },

  async saveLessonHistory(documentItem, studyPack = null) {
    const nowIso = new Date().toISOString();
    const payload = {
      id: documentItem.id,
      title: documentItem.title,
      created_at: documentItem.createdAt || documentItem.updatedAt || nowIso,
      input_type: documentItem.fileType ? documentItem.fileType.toLowerCase() : 'pdf',
      source_name: documentItem.title,
      // prerequisites ride inside the structured_note JSON column (no dedicated column in learning_sessions)
      structured_note: (studyPack?.note || studyPack?.notes || studyPack?.prerequisites)
        ? { ...(studyPack?.note || studyPack?.notes || {}), prerequisites: studyPack?.prerequisites || null }
        : null,
      mindmap: studyPack?.mindmap || null,
      flashcards: studyPack?.flashcards || [],
      quiz: studyPack?.quiz || [],
      updated_at: nowIso
    };

    // Update in-memory & disk storage
    const existing = memoryHistoryStore.get(documentItem.id) || {};
    const updatedItem = {
      ...existing,
      ...documentItem,
      studyPack: studyPack || existing.studyPack || null,
      quizHistory: existing.quizHistory || [],
      updatedAt: nowIso
    };

    memoryHistoryStore.set(documentItem.id, updatedItem);
    saveHistoryToDisk(memoryHistoryStore);

    if (isSupabaseConfigured && supabase) {
      try {
        const { error } = await supabase
          .from('learning_sessions')
          .upsert(payload, { onConflict: 'id' });

        if (error) {
          console.warn('⚠️ [Supabase] Save error:', error.message);
        } else {
          console.log(`✅ [Supabase] Saved lesson to learning_sessions for ${documentItem.id}`);
        }
      } catch (err) {
        console.error('❌ [Supabase Exception on Save]:', err.message);
      }
    }

    return updatedItem;
  },

  async recordQuizResult(docId, score, correctCount, totalQuestions, feedback) {
    const quizAttempt = {
      id: `qhist-${Date.now()}`,
      score,
      correctCount,
      totalQuestions,
      completedAt: new Date().toISOString(),
      feedback
    };

    // In-memory & disk update
    const lesson = memoryHistoryStore.get(docId);
    if (lesson) {
      lesson.quizHistory = lesson.quizHistory || [];
      lesson.quizHistory.unshift(quizAttempt);
      lesson.updatedAt = quizAttempt.completedAt;
      saveHistoryToDisk(memoryHistoryStore);
    }

    return quizAttempt;
  },

  async deleteLessonHistory(id) {
    memoryHistoryStore.delete(id);
    saveHistoryToDisk(memoryHistoryStore);

    if (isSupabaseConfigured && supabase) {
      try {
        const { error } = await supabase
          .from('learning_sessions')
          .delete()
          .eq('id', id);

        if (error) {
          console.warn(`⚠️ [Supabase] Error deleting ${id}:`, error.message);
        } else {
          console.log(`✅ [Supabase] Deleted session ${id}`);
        }
      } catch (err) {
        console.error('❌ [Supabase Exception on Delete]:', err.message);
      }
    }

    return true;
  },

  // ==========================================
  // 🔑 AUTHENTICATION & USER MANAGEMENT
  // ==========================================
  async signUpUser(email, password, fullName) {
    const defaultAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80";

    if (isSupabaseConfigured && supabase) {
      try {
        const { data, error } = await supabase.auth.signUp({
          email,
          password,
          options: {
            data: {
              full_name: fullName,
              avatar_url: defaultAvatar
            }
          }
        });

        if (error) throw error;

        const user = data.user;
        return {
          success: true,
          user: {
            id: user.id,
            email: user.email,
            fullName: fullName || user.email.split('@')[0],
            avatarUrl: defaultAvatar,
            membershipTier: "Premium",
            createdAt: user.created_at
          },
          session: data.session
        };
      } catch (err) {
        console.warn('⚠️ [Supabase Auth] SignUp failed, fallback to local register:', err.message);
      }
    }

    // Fallback Local Registration
    const newUser = {
      id: `usr-${Date.now()}`,
      email,
      fullName: fullName || email.split('@')[0],
      avatarUrl: defaultAvatar,
      membershipTier: "Premium",
      createdAt: new Date().toISOString()
    };

    return {
      success: true,
      user: newUser,
      session: { token: `local-token-${Date.now()}` }
    };
  },

  async signInUser(email, password) {
    if (isSupabaseConfigured && supabase) {
      try {
        const { data, error } = await supabase.auth.signInWithPassword({
          email,
          password
        });

        if (error) throw error;

        const user = data.user;
        return {
          success: true,
          user: {
            id: user.id,
            email: user.email,
            fullName: user.user_metadata?.full_name || user.email.split('@')[0],
            avatarUrl: user.user_metadata?.avatar_url || "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80",
            membershipTier: "Premium",
            createdAt: user.created_at
          },
          session: data.session
        };
      } catch (err) {
        console.warn('⚠️ [Supabase Auth] SignIn failed, fallback to local auth:', err.message);
      }
    }

    // Fallback Local Login
    return {
      success: true,
      user: {
        id: "usr-1",
        email: email || "minhtri@studymind.ai",
        fullName: email ? (email.split('@')[0] === 'demo' ? 'Người dùng Demo' : email.split('@')[0]) : "Nguyễn Minh Trí",
        avatarUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80",
        membershipTier: "Premium",
        createdAt: new Date().toISOString()
      },
      session: { token: `local-token-${Date.now()}` }
    };
  },

  async signOutUser() {
    if (isSupabaseConfigured && supabase) {
      try {
        await supabase.auth.signOut();
      } catch (err) {
        console.warn('⚠️ [Supabase Auth] SignOut exception:', err.message);
      }
    }
    return { success: true };
  }
};
