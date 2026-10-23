(() => {
  const $ = id => document.getElementById(id);
  const configured = window.ELOGBOOK_CONFIG && /^https:\/\//.test(window.ELOGBOOK_CONFIG.supabaseUrl) && !window.ELOGBOOK_CONFIG.supabaseUrl.includes("PASTE_") && window.ELOGBOOK_CONFIG.supabaseAnonKey && !window.ELOGBOOK_CONFIG.supabaseAnonKey.includes("PASTE_");
  let client = null, currentUser = null, currentRole = "student";
  const say = (id, message, ok=false) => { $(id).textContent = message; $(id).classList.toggle("ok",ok); };
  const today = new Date();
  const localDate = new Date(today.getTime()-today.getTimezoneOffset()*60000).toISOString().slice(0,10);
  $("entryDate").value = localDate;
  const monday = new Date(today); monday.setDate(today.getDate()-((today.getDay()+6)%7));
  $("weekStart").value = new Date(monday.getTime()-monday.getTimezoneOffset()*60000).toISOString().slice(0,10);
  if (configured && window.supabase) {
    client = window.supabase.createClient(window.ELOGBOOK_CONFIG.supabaseUrl, window.ELOGBOOK_CONFIG.supabaseAnonKey);
    $("connection").textContent = "Online portal";
  } else {
    $("connection").textContent = "Setup required";
    say("authMessage","The portal is ready, but it needs the dedicated E-Logbook database URL and publishable key in config.js.");
    $("signIn").disabled = $("signUp").disabled = true;
  }
  async function showSession() {
    if (!client) return;
    const {data:{session},error} = await client.auth.getSession();
    if (error) return say("authMessage",error.message);
    currentUser = session?.user || null;
    $("authPanel").hidden = !!currentUser; $("appPanel").hidden = !currentUser;
    if (!currentUser) {
      currentRole = "student";
      $("studentPanel").hidden = false;
      $("supervisorPanel").hidden = true;
      return;
    }
    await ensureProfile();
    const canReview = currentRole === "field_supervisor" || currentRole === "institution_supervisor";
    $("studentPanel").hidden = canReview;
    $("supervisorPanel").hidden = !canReview;
    await loadEntries();
    await loadWeekly();
    if (canReview) await loadReviewQueue();
  }
  async function ensureProfile() {
    if (!client || !currentUser) return;
    const {data,error} = await client.from("profiles").select("id,role,full_name").eq("id",currentUser.id).maybeSingle();
    if (error) { say("entryMessage","Profile check failed: "+error.message); return; }
    if (data) {
      currentRole = data.role || "student";
      return;
    }
    if (!data) {
      const fallback = (currentUser.email || "student").split("@")[0];
      const fullName = (currentUser.user_metadata && currentUser.user_metadata.full_name) || fallback;
      const {error:insertError} = await client.from("profiles").insert({id:currentUser.id,full_name:fullName,role:"student"});
      if (insertError) say("entryMessage","Account profile could not be created: "+insertError.message);
      else currentRole = "student";
    }
  }
  $("authForm").addEventListener("submit", async e => {
    e.preventDefault(); if (!client) return;
    $("signIn").disabled = true; say("authMessage","Signing in…");
    const {error} = await client.auth.signInWithPassword({email:$("email").value.trim(),password:$("password").value});
    $("signIn").disabled = false;
    if (error) say("authMessage",error.message); else {say("authMessage","Signed in.",true);await showSession();}
  });
  $("signUp").addEventListener("click", async () => {
    if (!client) return;
    $("signUp").disabled = true; say("authMessage","Creating account…");
    const {data,error} = await client.auth.signUp({email:$("email").value.trim(),password:$("password").value,options:{emailRedirectTo:window.location.origin+window.location.pathname}});
    $("signUp").disabled = false;
    if (error) say("authMessage",error.message);
    else if (!data.session) say("authMessage","Account created. Check your email to confirm it, then sign in.",true);
    else {say("authMessage","Account created.",true);await showSession();}
  });
  $("signOut").addEventListener("click", async () => { if (client) {await client.auth.signOut();currentUser=null;await showSession();} });
  $("refresh").addEventListener("click",loadEntries);
  $("refreshWeekly").addEventListener("click",loadWeekly);
  $("weeklyForm").addEventListener("submit", saveWeekly);
  $("refreshReviews").addEventListener("click",loadReviewQueue);
  $("entryForm").addEventListener("submit",async e=>{
    e.preventDefault(); if (!client || !currentUser) return;
    const row={student_id:currentUser.id,entry_date:$("entryDate").value,work_done:$("workDone").value.trim(),tools_used:$("toolsUsed").value.trim(),skills_learned:$("skillsLearned").value.trim(),challenges:$("challenges").value.trim(),recommendations:$("recommendations").value.trim(),client_updated_at:new Date().toISOString(),updated_at:new Date().toISOString()};
    if(!row.work_done) return say("entryMessage","Please describe the work done today.");
    say("entryMessage","Saving…");
    const {error}=await client.from("daily_entries").upsert(row,{onConflict:"student_id,entry_date"});
    if(error) say("entryMessage","Could not save: "+error.message);
    else {say("entryMessage","Daily entry saved online.",true);$("workDone").value=$("toolsUsed").value=$("skillsLearned").value=$("challenges").value=$("recommendations").value="";await loadEntries();}
  });

  async function saveWeekly(e) {
    e.preventDefault(); if (!client || !currentUser) return;
    const weekStart = $("weekStart").value;
    const summary = $("weeklySummary").value.trim();
    if (!weekStart || !summary) return say("weeklyMessage","Choose the week starting date and enter your summary.");
    const {error} = await client.from("weekly_summaries").upsert({
      student_id: currentUser.id,
      week_start: weekStart,
      summary,
      skills_learned: $("weeklySkills").value.trim(),
      challenges: $("weeklyChallenges").value.trim(),
      client_updated_at: new Date().toISOString(),
      updated_at: new Date().toISOString()
    }, {onConflict:"student_id,week_start"});
    if (error) return say("weeklyMessage","Could not save weekly summary: "+error.message);
    say("weeklyMessage","Weekly summary saved online.",true);
    $("weeklySummary").value = $("weeklySkills").value = $("weeklyChallenges").value = "";
    await loadWeekly();
  }
  async function loadWeekly() {
    if (!client || !currentUser) return;
    const {data,error} = await client.from("weekly_summaries")
      .select("id,week_start,summary,skills_learned,challenges,supervisor_comments,supervisor_signed_at,updated_at")
      .eq("student_id",currentUser.id).order("week_start",{ascending:false}).limit(30);
    if (error) { $("weeklyEntries").textContent = "Could not load weekly summaries: "+error.message; return; }
    $("weeklyEntries").replaceChildren();
    if (!data.length) {
      const p=document.createElement("p"); p.className="muted"; p.textContent="No weekly summaries yet. Complete your first summary above."; $("weeklyEntries").append(p); return;
    }
    for (const item of data) {
      const card=document.createElement("article"); card.className="entry";
      const h=document.createElement("h3"); h.textContent="Week starting "+new Date(item.week_start+"T12:00:00").toLocaleDateString(undefined,{year:"numeric",month:"short",day:"numeric"}); card.append(h);
      for (const [label,value] of [["Summary",item.summary],["Skills learned",item.skills_learned],["Challenges",item.challenges],["Supervisor comments",item.supervisor_comments]]) {
        if(value){const p=document.createElement("p");p.textContent=label+": "+value;card.append(p);}
      }
      const state=document.createElement("p");state.textContent=item.supervisor_signed_at?"Review status: Reviewed":"Review status: Awaiting supervisor review";card.append(state);
      $("weeklyEntries").append(card);
    }
  }
  async function loadReviewQueue() {
    if (!client || !currentUser || !["field_supervisor","institution_supervisor"].includes(currentRole)) return;
    say("reviewMessage","Loading your assigned students…");
    $("reviewQueue").replaceChildren();
    const {data: assignments,error: assignmentError} = await client.from("supervisor_assignments")
      .select("student_id,assignment_type").eq("supervisor_id",currentUser.id);
    if (assignmentError) return say("reviewMessage","Could not load supervisor assignments: "+assignmentError.message);
    const assignedIds = [...new Set((assignments || [])
      .filter(a => a.assignment_type === currentRole).map(a => a.student_id))];
    if (!assignedIds.length) {
      say("reviewMessage","No students are assigned to this supervisor account yet.");
      return;
    }
    const [profileResult,summaryResult] = await Promise.all([
      client.from("profiles").select("id,full_name").in("id",assignedIds),
      client.from("weekly_summaries")
        .select("id,student_id,week_start,summary,skills_learned,challenges,supervisor_comments,supervisor_signed_at")
        .in("student_id",assignedIds).order("week_start",{ascending:false}).limit(100)
    ]);
    if (profileResult.error) return say("reviewMessage","Could not load student names: "+profileResult.error.message);
    if (summaryResult.error) return say("reviewMessage","Could not load weekly reports: "+summaryResult.error.message);
    const names = Object.fromEntries((profileResult.data || []).map(p => [p.id,p.full_name || "Student"]));
    $("reviewQueue").replaceChildren();
    const summaries = summaryResult.data || [];
    if (!summaries.length) {
      say("reviewMessage","Your assigned students do not have weekly summaries yet.");
      return;
    }
    say("reviewMessage",summaries.length+" weekly report(s) assigned to you.");
    for (const item of summaries) {
      const card=document.createElement("article"); card.className="entry review-entry";
      const heading=document.createElement("h3"); heading.textContent=names[item.student_id] || "Assigned student"; card.append(heading);
      const week=document.createElement("p"); week.textContent="Week starting "+new Date(item.week_start+"T12:00:00").toLocaleDateString(undefined,{year:"numeric",month:"short",day:"numeric"}); card.append(week);
      for (const [label,value] of [["Work completed",item.summary],["Skills learned",item.skills_learned],["Challenges",item.challenges]]) {
        if (value) { const p=document.createElement("p"); p.textContent=label+": "+value; card.append(p); }
      }
      const fieldLabel=document.createElement("label"); fieldLabel.textContent="Supervisor comments";
      const comments=document.createElement("textarea"); comments.rows=3; comments.maxLength=4000; comments.value=item.supervisor_comments || "";
      fieldLabel.append(comments); card.append(fieldLabel);
      const state=document.createElement("p"); state.textContent=item.supervisor_signed_at
        ? "Reviewed on "+new Date(item.supervisor_signed_at).toLocaleString()
        : "Awaiting supervisor review";
      card.append(state);
      const save=document.createElement("button"); save.type="button"; save.textContent="Save review and mark reviewed";
      save.addEventListener("click",async()=>{
        save.disabled=true; say("reviewMessage","Saving supervisor review…");
        const {error}=await client.from("weekly_summaries").update({
          supervisor_comments:comments.value.trim(),
          supervisor_signed_at:new Date().toISOString(),
          updated_at:new Date().toISOString()
        }).eq("id",item.id);
        save.disabled=false;
        if(error){say("reviewMessage","Review could not be saved: "+error.message);return;}
        say("reviewMessage","Supervisor review saved.",true);
        await loadReviewQueue();
      });
      card.append(save); $("reviewQueue").append(card);
    }
  }

  async function loadEntries(){
    if(!client||!currentUser)return;
    $("syncState").textContent=navigator.onLine?"Online":"Offline";
    const {data,error}=await client.from("daily_entries").select("id,entry_date,work_done,tools_used,skills_learned,challenges,recommendations,updated_at").eq("student_id",currentUser.id).order("entry_date",{ascending:false}).limit(30);
    if(error){$("entries").textContent="Could not load records: "+error.message;return;}
    $("dailyCount").textContent=data.length;
    $("entries").replaceChildren();
    if(!data.length){const p=document.createElement("p");p.className="muted";p.textContent="No daily entries yet. Add your first record above."; $("entries").append(p);return;}
    for(const item of data){
      const card=document.createElement("article");card.className="entry";
      const h=document.createElement("h3");h.textContent=new Date(item.entry_date+"T12:00:00").toLocaleDateString(undefined,{weekday:"short",year:"numeric",month:"short",day:"numeric"});card.append(h);
      for(const [label,value] of [["Work done",item.work_done],["Tools used",item.tools_used],["Skills learned",item.skills_learned],["Challenges",item.challenges],["Recommendations",item.recommendations]]) if(value){const p=document.createElement("p");p.textContent=label+": "+value;card.append(p);}
      $("entries").append(card);
    }
  }
  window.addEventListener("online",()=>{ $("syncState").textContent="Online"; });
  window.addEventListener("offline",()=>{ $("syncState").textContent="Offline"; });
  if(client) showSession();
})();