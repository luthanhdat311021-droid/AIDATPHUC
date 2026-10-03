import { createClient } from '@supabase/supabase-js';
import dotenv from 'dotenv';

dotenv.config();

const supabaseUrl = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL || 'https://gwynjmlqymojrdlpzukn.supabase.co';
const supabaseAnonKey = process.env.SUPABASE_ANON_KEY || process.env.VITE_SUPABASE_ANON_KEY || 'sb_publishable_HdnEe4-ApmYewkEGVmab_Q_Gc-B1hqc';

const authClient = createClient(supabaseUrl, supabaseAnonKey, { auth: { persistSession: false } });

// One row of learning_sessions → the lesson shape the frontend expects
const toLesson = (row) => ({
  id: row.id,
  title: row.title || row.source_name || 'Bài học chưa đặt tên',
  fileType: (row.input_type || 'PDF').toUpperCase(),
  fileSize: '1.5 MB',
  pageCount: 5,
  duration: '15 phút',
  updatedAt: row.updated_at || row.created_at,
  createdAt: row.created_at,
  status: 'COMPLETED',
  tags: [row.input_type ? row.input_type.toUpperCase() : 'TÀI LIỆU'],
  rawText: row.raw_text || row.structured_note?.summary || '',
  studyPack: {
    knowledgeBase: row.knowledge_base || null,
    note: row.structured_note,
    notes: row.structured_note,
    prerequisites: row.structured_note?.prerequisites || null,
    mindmap: row.mindmap,
    flashcards: row.flashcards || [],
    quiz: row.quiz || []
  },
  quizHistory: row.quiz_history || []
});

const fail = (action, error) => {
  throw new Error(`Lỗi cơ sở dữ liệu khi ${action}: ${error.message}`);
};

export const supabaseService = {
  isConfigured() {
    return true;
  },

  /** Verify a Supabase access token; returns the user or null */
  async getUserFromToken(token) {
    const { data, error } = await authClient.auth.getUser(token);
    return error ? null : data.user;
  },

  /** Client that acts as the signed-in user, so Row Level Security scopes every query to their rows */
  clientFor(token) {
    return createClient(supabaseUrl, supabaseAnonKey, {
      auth: { persistSession: false },
      global: { headers: { Authorization: `Bearer ${token}` } }
    });
  },

  async getAllLessonHistory(client) {
    const { data, error } = await client.from('learning_sessions').select('*').order('updated_at', { ascending: false });
    if (error) fail('tải lịch sử bài học', error);
    return data.map(toLesson);
  },

  async getLessonHistoryById(id, client) {
    const { data, error } = await client.from('learning_sessions').select('*').eq('id', id).maybeSingle();
    if (error) fail('tải bài học', error);
    return data ? toLesson(data) : null;
  },

  async saveLessonHistory(lesson, studyPack, client) {
    const nowIso = new Date().toISOString();
    const pack = studyPack || {};
    const notes = pack.note || pack.notes;
    const { error } = await client.from('learning_sessions').upsert({
      id: lesson.id,
      title: lesson.title,
      created_at: lesson.createdAt || nowIso,
      input_type: lesson.fileType ? lesson.fileType.toLowerCase() : 'pdf',
      source_name: lesson.title,
      // prerequisites ride inside the structured_note JSON column; both columns are NOT NULL in the table
      structured_note: { ...(notes || {}), prerequisites: pack.prerequisites || null },
      mindmap: pack.mindmap || {},
      flashcards: pack.flashcards || [],
      quiz: pack.quiz || [],
      knowledge_base: pack.knowledgeBase || null,
      raw_text: lesson.rawText || null,
      updated_at: nowIso
    }, { onConflict: 'id' });
    if (error) fail('lưu bài học', error);
  },

  async recordQuizResult(lesson, score, correctCount, totalQuestions, feedback, results, client) {
    const attempt = { id: `qhist-${Date.now()}`, score, correctCount, totalQuestions, completedAt: new Date().toISOString(), feedback, results };
    const { error } = await client
      .from('learning_sessions')
      .update({ quiz_history: [attempt, ...(lesson.quizHistory || [])], updated_at: attempt.completedAt })
      .eq('id', lesson.id);
    if (error) fail('lưu kết quả trắc nghiệm', error);
    return attempt;
  },

  async deleteLessonHistory(id, client) {
    const { error } = await client.from('learning_sessions').delete().eq('id', id);
    if (error) fail('xóa bài học', error);
  }
};
