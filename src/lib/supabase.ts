import { createClient } from '@supabase/supabase-js';

// The anon/publishable key is public by design; Row Level Security on the database is what protects the data
export const supabase = createClient(
  import.meta.env.VITE_SUPABASE_URL || 'https://gwynjmlqymojrdlpzukn.supabase.co',
  import.meta.env.VITE_SUPABASE_ANON_KEY || 'sb_publishable_HdnEe4-ApmYewkEGVmab_Q_Gc-B1hqc'
);
