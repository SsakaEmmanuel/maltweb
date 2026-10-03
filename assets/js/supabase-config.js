// MALTWEB Supabase browser configuration.
// This uses the public/publishable key only. Never put a service-role/secret key here.
const MALT_SUPABASE_URL = 'https://kwekebkniywzcpvmdvka.supabase.co';
const MALT_SUPABASE_ANON_KEY = 'sb_publishable_aPv6JZQSWC-v3Y0k3TB_ew__HW2qcBA';
let maltSupabase = null;
if (window.supabase) maltSupabase = window.supabase.createClient(MALT_SUPABASE_URL, MALT_SUPABASE_ANON_KEY);
window.maltSupabase = maltSupabase;
