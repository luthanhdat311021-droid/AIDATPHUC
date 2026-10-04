import { createClient } from '@supabase/supabase-js';

// The anon/publishable key is public by design; Row Level Security on the database is what protects the data
export const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL || 'https://gwynjmlqymojrdlpzukn.supabase.co';
export const SUPABASE_ANON_KEY = import.meta.env.VITE_SUPABASE_ANON_KEY || 'sb_publishable_HdnEe4-ApmYewkEGVmab_Q_Gc-B1hqc';

// Deep link the Android app registers in AndroidManifest.xml; must also be in Supabase → Redirect URLs
export const NATIVE_AUTH_REDIRECT = 'com.studymind.app://auth/callback';

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
