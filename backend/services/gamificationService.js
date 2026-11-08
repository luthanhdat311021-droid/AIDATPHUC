// Streak, counters and badges are stored and calculated by the database (migration 010).
// Failures return null: a missing migration or key must never break the review or quiz that triggered it.
import { createClient } from '@supabase/supabase-js';

const url = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL || 'https://gwynjmlqymojrdlpzukn.supabase.co';
const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
const admin = key ? createClient(url, key, { auth: { persistSession: false } }) : null;

export async function applyGamificationEvent(userId, kind, localImport = null) {
  if (!admin) return null;
  try {
    const { data, error } = await admin.rpc('studymind_gamification_apply', { p_user: userId, p_kind: kind, p_import: localImport });
    if (error) throw error;
    return { ...data, userId };
  } catch (err) {
    console.warn(`Gamification event "${kind}" failed:`, err.message);
    return null;
  }
}
