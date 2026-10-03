/* MALTWEB ONLINE DATABASE CONFIG
   Connected to the MALTWEB Supabase project.
   This browser file uses a Supabase publishable key only.
   NEVER put a service_role/secret key or database password here.
*/
const MALT_SUPABASE_URL = 'https://kwekebkniywzcpvmdvka.supabase.co';
const MALT_SUPABASE_ANON_KEY = 'sb_publishable_aPv6JZQSWC-v3Y0k3TB_ew__HW2qcBA';

let maltSupabase = null;
if (window.supabase && MALT_SUPABASE_URL.startsWith('http') && MALT_SUPABASE_ANON_KEY) {
  maltSupabase = window.supabase.createClient(MALT_SUPABASE_URL, MALT_SUPABASE_ANON_KEY);
}
window.maltSupabase = maltSupabase;
