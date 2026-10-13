import { createClient } from "npm:@supabase/supabase-js@2";
const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, apikey, content-type, x-client-info","Access-Control-Allow-Methods":"GET, OPTIONS"};
function reply(body:unknown,status=200){return new Response(JSON.stringify(body),{status,headers:{...cors,"Content-Type":"application/json; charset=utf-8"}})}
Deno.serve(async req=>{
 if(req.method==="OPTIONS")return new Response("ok",{headers:cors});
 if(req.method!=="GET")return reply({error:"Method not allowed."},405);
 const url=Deno.env.get("SUPABASE_URL"),anon=Deno.env.get("SUPABASE_ANON_KEY"),secret=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
 if(!url||!anon||!secret)return reply({error:"Edge Function secrets are not configured."},500);
 const auth=req.headers.get("Authorization")||"",token=auth.startsWith("Bearer ")?auth.slice(7):"";
 if(!token)return reply({error:"Sign in as an administrator first."},401);
 const callerClient=createClient(url,anon,{auth:{persistSession:false,autoRefreshToken:false},global:{headers:{Authorization:`Bearer ${token}`}}});
 const {data:cd,error:ce}=await callerClient.auth.getUser(token);if(ce||!cd.user)return reply({error:"Session invalid or expired. Sign in again."},401);
 const db=createClient(url,secret,{auth:{persistSession:false,autoRefreshToken:false}});
 const rr=await db.from("malt_user_roles").select("role").eq("user_id",cd.user.id).maybeSingle();
 if(rr.error)return reply({error:"Could not verify administrator permissions. Check malt_user_roles."},500);
 let admin=String(rr.data?.role||"").toLowerCase()==="admin";
 if(!admin){const rpc=await callerClient.rpc("malt_is_admin");admin=!rpc.error&&rpc.data===true}
 if(!admin)return reply({error:"Administrator access only."},403);
 try{
  const authUsers:any[]=[];
  for(let page=1;page<=100;page++){const {data,error}=await db.auth.admin.listUsers({page,perPage:100});if(error)throw Error(`Could not read Auth users: ${error.message}`);const batch=data.users||[];authUsers.push(...batch);if(batch.length<100)break}
  if(authUsers.length>=10000)return reply({error:"Directory safety limit reached; pagination is needed for projects with 10,000+ users."},500);
  const ids=authUsers.map(u=>u.id);if(!ids.length)return reply({users:[]});
  const [pr,roles,lr]=await Promise.all([
   db.from("account_profiles").select("user_id,full_name,phone,bio,avatar_url,updated_at").in("user_id",ids),
   db.from("malt_user_roles").select("user_id,role").in("user_id",ids),
   db.from("listings").select("id,owner_id,name,type,status,location,created_at").in("owner_id",ids).order("created_at",{ascending:false})
  ]);
  if(pr.error)throw Error(`Could not read account profiles: ${pr.error.message}`);
  if(roles.error)throw Error(`Could not read roles: ${roles.error.message}`);
  if(lr.error)throw Error(`Could not read public profiles: ${lr.error.message}`);
  const pm=new Map((pr.data||[]).map(p=>[p.user_id,p])),rm=new Map((roles.data||[]).map(r=>[r.user_id,r.role])),lm=new Map();
  for(const l of lr.data||[])if(!lm.has(l.owner_id))lm.set(l.owner_id,l);
  const users=authUsers.map(u=>({id:u.id,email:u.email||null,created_at:u.created_at||null,confirmed_at:u.email_confirmed_at||u.confirmed_at||null,role:rm.get(u.id)||"user",profile:pm.get(u.id)||null,listing:lm.get(u.id)||null})).sort((a,b)=>String(b.created_at||"").localeCompare(String(a.created_at||"")));
  return reply({users});
 }catch(e){console.error("admin-users failed",e);return reply({error:e instanceof Error?e.message:"Unexpected server error."},500)}
});
