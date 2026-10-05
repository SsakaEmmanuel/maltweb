const $=s=>document.querySelector(s);
const esc=x=>String(x??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
const online=()=>!!window.maltSupabase;

async function loadData(){
 if(!online())return;
 const [l,e,o]=await Promise.all([
  maltSupabase.from('listings').select('*').eq('status','active').order('verified',{ascending:false}).order('created_at',{ascending:false}),
  maltSupabase.from('events').select('*').eq('status','active').order('date',{ascending:true}),
  maltSupabase.from('opportunities').select('*').eq('status','active').order('created_at',{ascending:false})
 ]);
 if(!l.error)LISTINGS=l.data||[]; if(!e.error)EVENTS=e.data||[]; if(!o.error)OPPORTUNITIES=o.data||[];
}
function waNumber(phone){let n=String(phone||'').replace(/\D/g,''); if(!n)return ''; if(n.startsWith('0'))n='256'+n.slice(1); if(!n.startsWith('256'))n='256'+n; return n;}
function isSaved(id){try{return JSON.parse(localStorage.getItem('maltweb_favourites')||'[]').includes(String(id))}catch{return false}}
function toggleSaved(id){let a=[];try{a=JSON.parse(localStorage.getItem('maltweb_favourites')||'[]')}catch{};id=String(id);a=a.includes(id)?a.filter(x=>x!==id):[...a,id];localStorage.setItem('maltweb_favourites',JSON.stringify(a));render()}
window.toggleSaved=toggleSaved;

async function logInteraction(listingId,eventType,metadata={}){
 if(!online()||!listingId)return;
 try{ await maltSupabase.rpc('malt_log_interaction',{p_listing_id:listingId,p_event_type:eventType,p_metadata:metadata}); }catch(e){console.warn('interaction log failed',e)}
}
async function trackAndGo(listingId,eventType,url){await logInteraction(listingId,eventType); if(url)location.href=url;}
window.trackAndGo=trackAndGo;
async function loadOwnerInteractions(listingIds){
 if(!listingIds?.length)return [];
 const {data,error}=await maltSupabase.from('listing_interactions').select('id,listing_id,actor_user_id,event_type,metadata,created_at').in('listing_id',listingIds).order('created_at',{ascending:false});
 return error?[]:(data||[]);
}
async function loadInteractionDashboard(user,role,mine){
 const box=$('#interactionDashboard'); if(!box)return;
 if(role==='user'){
  const {data,error}=await maltSupabase.from('listing_interactions').select('id,listing_id,event_type,created_at,metadata').eq('actor_user_id',user.id).order('created_at',{ascending:false}).limit(100);
  if(error){box.innerHTML='<div class="card"><h3>Interaction history</h3><p class="muted">'+esc(error.message)+'</p></div>';return;}
  const ids=[...new Set((data||[]).map(x=>x.listing_id))];
  let names={};
  if(ids.length){const r=await maltSupabase.from('listings').select('id,name,type').in('id',ids);(r.data||[]).forEach(x=>names[x.id]=x);}
  const counts={profile_view:0,call:0,whatsapp:0,website:0};(data||[]).forEach(x=>counts[x.event_type]=(counts[x.event_type]||0)+1);
  const rows=(data||[]).slice(0,20).map(x=>`<article class="interaction-row"><strong>${esc(names[x.listing_id]?.name||'Listing')}</strong><span>${esc((x.event_type||'interaction').replaceAll('_',' '))}</span><small>${new Date(x.created_at).toLocaleString()}</small></article>`).join('')||'<p class="muted">No interactions yet. Start by contacting a local listing.</p>';
  box.innerHTML=`<section class="card"><div class="profile-head"><div><span class="eyebrow">MY ACTIVITY</span><h2>Interaction history</h2></div></div><div class="stats"><div class="stat"><strong>${data?.length||0}</strong><span>Total</span></div><div class="stat"><strong>${counts.call||0}</strong><span>Calls</span></div><div class="stat"><strong>${counts.whatsapp||0}</strong><span>WhatsApp</span></div><div class="stat"><strong>${counts.profile_view||0}</strong><span>Profile views</span></div></div><div class="interaction-list">${rows}</div></section>`;
  return;
 }
 if(['business','service_provider','organization'].includes(role)){
  const active=mine.filter(x=>x.status==='active');
  const ints=await loadOwnerInteractions(active.map(x=>x.id));
  const counts={profile_view:0,call:0,whatsapp:0,website:0};ints.forEach(x=>counts[x.event_type]=(counts[x.event_type]||0)+1);
  const byListing={};ints.forEach(x=>{byListing[x.listing_id]=(byListing[x.listing_id]||0)+1});
  const cards=active.map(x=>`<article class="interaction-row"><strong>${esc(x.name)}</strong><span>${byListing[x.id]||0} interaction${byListing[x.id]===1?'':'s'}</span><a href="profile.html?id=${encodeURIComponent(x.id)}">View profile</a></article>`).join('')||'<p class="muted">Approve a profile to start receiving interaction data.</p>';
  box.innerHTML=`<section class="card"><div class="profile-head"><div><span class="eyebrow">CUSTOMER ACTIVITY</span><h2>Customer interactions</h2></div></div><div class="stats"><div class="stat"><strong>${ints.length}</strong><span>Total</span></div><div class="stat"><strong>${counts.profile_view||0}</strong><span>Profile views</span></div><div class="stat"><strong>${counts.call||0}</strong><span>Calls</span></div><div class="stat"><strong>${counts.whatsapp||0}</strong><span>WhatsApp</span></div></div><p class="muted">Customer contact actions are counted here. Customer identities are only shown when available through authenticated interactions.</p><div class="interaction-list">${cards}</div></section>`;
  return;
 }
 box.innerHTML='';
}
async function loadReviews(listingId){
 const {data,error}=await maltSupabase.from('reviews').select('id,user_id,rating,review,created_at').eq('listing_id',listingId).order('created_at',{ascending:false});
 return error?[]:(data||[]);
}
function stars(n){return '★'.repeat(Number(n)||0)+'☆'.repeat(5-(Number(n)||0));}
async function submitReview(listingId){
 const {data:{user}}=await maltSupabase.auth.getUser();
 if(!user){location='login.html';return;}
 const rating=Number($('#reviewRating')?.value||0), text=$('#reviewText')?.value.trim()||'';
 if(!rating||rating<1||rating>5){$('#reviewMsg').textContent='Please choose a rating from 1 to 5.';return;}
 const {error}=await maltSupabase.from('reviews').upsert({listing_id:listingId,user_id:user.id,rating,review:text||null},{onConflict:'listing_id,user_id'});
 if(error){$('#reviewMsg').textContent=error.message;return;}
 $('#reviewMsg').textContent='Review saved ✓';
 await showProfile();
}
window.submitReview=submitReview;
function card(x){return `<article class="card listing-card">${x.logo_url?`<img class="listing-logo" src="${esc(x.logo_url)}" alt="">`:''}<div class="cardtop"><span class="pill">${x.verified?'✓ Verified':'Local listing'}</span><button class="iconbtn ${isSaved(x.id)?'saved':''}" onclick="toggleSaved('${x.id}')" aria-label="Save profile">${isSaved(x.id)?'♥':'♡'}</button></div><h3>${esc(x.name)}</h3><p class="muted">${esc(x.category)} · ${esc(x.type)} · ${esc(x.location)}</p><p>${esc(x.description)}</p><div class="actions"><a class="button" href="profile.html?id=${encodeURIComponent(x.id)}">View profile</a>${x.phone?`<a class="button secondary" href="tel:${esc(x.phone)}" onclick="logInteraction('${x.id}','call')">Call</a>`:''}</div></article>`}
function render(){
 let p=new URLSearchParams(location.search);
 let q=($('#q')?.value||p.get('q')||'').toLowerCase(), loc=($('#loc')?.value||p.get('location')||'').toLowerCase(), cat=$('#cat')?.value||p.get('category')||'', type=$('#type')?.value||'';
 let a=LISTINGS.filter(x=>(!q||(x.name+' '+x.category+' '+x.description+' '+x.type+' '+x.location).toLowerCase().includes(q))&&(!loc||String(x.location||'').toLowerCase().includes(loc))&&(!cat||x.category===cat)&&(!type||x.type===type));
 if($('#count'))$('#count').textContent=`${a.length} result${a.length===1?'':'s'} found`;
 if($('#results'))$('#results').innerHTML=a.map(card).join('')||'<div class="card"><h3>No active listings yet.</h3><p>Approved profiles will appear here.</p></div>';
}
async function showProfile(){
 const id=new URLSearchParams(location.search).get('id'); const x=LISTINGS.find(a=>String(a.id)===String(id));
 if(!x){$('#profile').innerHTML='<div class="card"><h2>Profile not found</h2><p>This listing may still be pending review.</p></div>';return}
 logInteraction(x.id,'profile_view');
 const wa=waNumber(x.phone), saved=isSaved(x.id); let site='';
 if(x.website){let u=String(x.website).trim(); if(!/^https?:\/\//i.test(u))u='https://'+u; site=`<a class="button secondary" href="${esc(u)}" target="_blank" rel="noopener noreferrer" onclick="logInteraction('${x.id}','website')">Visit website ↗</a>`}
 const reviews=await loadReviews(x.id);
 const avg=reviews.length?(reviews.reduce((sum,r)=>sum+Number(r.rating||0),0)/reviews.length).toFixed(1):'0.0';
 const reviewList=reviews.length?reviews.map(r=>`<article class="review"><div><strong>${stars(r.rating)}</strong> <span class="muted">${new Date(r.created_at).toLocaleDateString()}</span></div><p>${esc(r.review||'No written comment.')}</p></article>`).join(''):'<p class="muted">No reviews yet.</p>';
 let reviewBox='<p class="muted">Log in as an Ordinary User to leave a rating or review.</p>';
 try{const {data:{user}}=await maltSupabase.auth.getUser(); if(user){const rr=await getAccountRole(user); if(rr==='user')reviewBox=`<form class="review-form" onsubmit="event.preventDefault();submitReview('${x.id}')"><select id="reviewRating" required><option value="">Your rating</option><option value="5">5 — Excellent</option><option value="4">4 — Very good</option><option value="3">3 — Good</option><option value="2">2 — Fair</option><option value="1">1 — Poor</option></select><textarea id="reviewText" placeholder="Write a review (optional)"></textarea><button>Submit review</button><p id="reviewMsg" class="muted"></p></form>`; else reviewBox='<p class="muted">Customer reviews are for Ordinary Users. You can view feedback below.</p>';}}catch(e){}
 $('#profile').innerHTML=`<div class="profile card">${x.logo_url?`<img class="profile-logo" src="${esc(x.logo_url)}" alt="${esc(x.name)} logo">`:''}<div class="profile-head"><span class="pill">${x.verified?'✓ Verified':'Local listing'}</span><button class="iconbtn ${saved?'saved':''}" onclick="toggleSaved('${x.id}');showProfile()">${saved?'♥ Saved':'♡ Save'}</button></div><h1>${esc(x.name)}</h1><p class="profile-meta">${esc(x.category)} · ${esc(x.type)} · ${esc(x.location)}</p><div class="rating-summary"><strong>★ ${avg}</strong> <span class="muted">(${reviews.length} review${reviews.length===1?'':'s'})</span></div><div class="profile-section"><h2>About</h2><p>${esc(x.description)}</p>${x.price?`<p><strong>Rate:</strong> ${esc(x.price)}${x.price_unit?' / '+esc(x.price_unit):''}</p>`:''}${x.service_area?`<p><strong>Service area:</strong> ${esc(x.service_area)}</p>`:''}</div><div class="profile-section"><h2>Contact</h2><p class="contact-number">${esc(x.phone||'Contact details available soon.')}</p><div class="actions">${x.phone?`<a class="button" href="tel:${esc(x.phone)}" onclick="logInteraction('${x.id}','call')">☎ Call</a><a class="button whatsapp" href="https://wa.me/${wa}" target="_blank" rel="noopener noreferrer" onclick="logInteraction('${x.id}','whatsapp')">WhatsApp</a>`:''}${site}</div></div><div class="profile-section"><h2>Reviews</h2>${reviewBox}<div class="reviews">${reviewList}</div></div><p class="muted">Share this profile with someone who needs this service.</p><div class="actions"><button class="secondary" onclick="navigator.clipboard?.writeText(location.href).then(()=>this.textContent='Link copied ✓')">Copy profile link</button><a class="button secondary" href="discover.html">← Back to Discover</a></div></div>`;
}
async function login(e){e.preventDefault();const {error}=await maltSupabase.auth.signInWithPassword({email:$('#email').value,password:$('#password').value});if(error){$('#msg').textContent=error.message;return}location='dashboard.html'}
async function register(e){e.preventDefault();const rawRole=$('#role').value||'user';const roleMap={'Ordinary User':'user','Business / Organization':'business','Business':'business','Service Provider':'service_provider','Organization':'organization'};const role=roleMap[rawRole]||rawRole;const {data,error}=await maltSupabase.auth.signUp({email:$('#email').value,password:$('#password').value,options:{data:{full_name:$('#name').value,role}}});if(error){$('#msg').textContent=error.message;return}if(data.user){const r=await maltSupabase.from('malt_user_roles').upsert({user_id:data.user.id,role},{onConflict:'user_id'});if(r.error){$('#msg').textContent='Account created, but role setup failed: '+r.error.message;return;}}$('#msg').textContent=data.session?'Account created.':'Account created. You can now log in.';setTimeout(()=>location='login.html',700)}
async function getAccountRole(user){
 const adminRes=await maltSupabase.rpc('malt_is_admin');
 if(!adminRes.error&&adminRes.data)return 'admin';
 const r=await maltSupabase.rpc('malt_get_my_role');
 const dbRole=(!r.error&&r.data)?String(r.data).toLowerCase():'';
 const metaRole=String(user.user_metadata?.role||'').toLowerCase();
 return ['admin','business','service_provider','organization','user'].includes(dbRole)?dbRole:(['admin','business','service_provider','organization','user'].includes(metaRole)?metaRole:'user');
}
function roleLabel(role){return ({user:'Ordinary User',business:'Business',service_provider:'Service Provider',organization:'Organization',admin:'Administrator'})[role]||'Ordinary User'}
async function dashboard(){
 const {data:{user}}=await maltSupabase.auth.getUser(); if(!user){location='login.html';return}
 const role=await getAccountRole(user);
 $('#hello').textContent='Hello, '+(user.user_metadata?.full_name||user.email.split('@')[0])+' 👋';
 if($('#rolePill'))$('#rolePill').textContent=roleLabel(role);
 if($('#roleTitle'))$('#roleTitle').textContent=role==='admin'?'Administrator dashboard':role==='user'?'Personal dashboard':roleLabel(role)+' dashboard';
 const intro={user:'Discover local options, save favourites, review profiles and contact providers.',business:'Manage your business presence on MALTWEB and publish your business profile.',service_provider:'Showcase your services, receive contacts and keep your service profile current.',organization:'Publish your organization and connect with people in your local community.',admin:'Review listings and manage the MALTWEB platform.'}[role]||'Manage your MALTWEB account.';
 if($('#roleIntro'))$('#roleIntro').textContent=intro;
 const adminNav=$('#adminNav');
 if(adminNav) adminNav.hidden=role!=='admin';
 const createNav=$('#createNav'); if(createNav && role==='service_provider'){createNav.textContent='Create service';createNav.href='create-service.html';}
 if($('#roleActions')){
   const links=[];
   if(role!=='admin')links.push('<a class="button" href="discover.html">Discover local options</a>');
   if(role==='service_provider')links.push('<a class="button" href="create-service.html">+ New service</a>');
   else if(role!=='user'&&role!=='admin')links.push('<a class="button" href="create-profile.html">+ Create '+esc(roleLabel(role))+' profile</a>');
   if(role==='admin')links.push('<a class="button" href="admin.html">Open admin review</a>');
   if(role==='user')links.push('<a class="button" href="create-profile.html">+ Create a profile</a>');
   $('#roleActions').innerHTML=links.join('');
 }
 const {data,error}=await maltSupabase.from('listings').select('*').eq('owner_id',user.id).order('created_at',{ascending:false});
 if(error){$('#myListings').innerHTML='<div class="card">'+esc(error.message)+'</div>';return}
 const mine=data||[];
 if($('#providerStats')){
   const services=mine.filter(x=>x.type==='Service Provider');
   $('#providerStats').innerHTML=`<div class="stat"><strong>${services.length}</strong><span>My services</span></div><div class="stat"><strong>${services.filter(x=>x.status==='active').length}</strong><span>Active</span></div><div class="stat"><strong>${services.filter(x=>x.status==='pending').length}</strong><span>Pending</span></div>`;
   $('#providerWorkspace').hidden=role!=='service_provider';
 }
 $('#myListings').innerHTML=mine.map(x=>`<article class="card"><div class="profile-head"><span class="pill">${esc(x.status)}</span>${x.type==='Service Provider'?`<span class="muted">Service</span>`:''}</div><h3>${esc(x.name)}</h3><p class="muted">${esc(x.category)} · ${esc(x.type)} · ${esc(x.location)}</p>${x.price?`<p><strong>Rate:</strong> ${esc(x.price)}${x.price_unit?' / '+esc(x.price_unit):''}</p>`:''}<div class="actions">${x.status==='active'?`<a class="button" href="profile.html?id=${encodeURIComponent(x.id)}">View public profile</a>`:''}<a class="button secondary" href="edit-profile.html?id=${encodeURIComponent(x.id)}">✎ ${x.type==='Service Provider'?'Edit service':'Edit'}</a><button class="secondary" onclick="deleteMyListing('${x.id}')">Delete</button></div></article>`).join('')||'<div class="card">No submissions yet.</div>';
 await loadInteractionDashboard(user,role,mine);
}
async function deleteMyListing(id){
 if(!confirm('Delete this profile? This cannot be undone.'))return;
 const {data,error}=await maltSupabase.rpc('malt_delete_my_listing',{p_listing_id:id});
 if(error||!data){alert(error?.message||'Delete failed');return;}
 location.reload();
}
window.deleteMyListing=deleteMyListing;
async function prepareEditProfile(){
 const {data:{user}}=await maltSupabase.auth.getUser(); if(!user){location='login.html';return;}
 const role=await getAccountRole(user); if(role!=='service_provider'&&role!=='business'&&role!=='organization'){ $('#editMsg').textContent='This account does not have a managed business/service profile.'; return; }
 const id=new URLSearchParams(location.search).get('id'); if(!id){$('#editMsg').textContent='Missing profile ID.';return;}
 const {data,error}=await maltSupabase.from('listings').select('*').eq('id',id).eq('owner_id',user.id).maybeSingle();
 if(error||!data){$('#editMsg').textContent=error?.message||'Profile not found or you do not own it.';return;}
 $('#editRole').textContent=roleLabel(role);
 if(data.type==='Service Provider'){const h=document.querySelector('h1');if(h)h.textContent='Edit service';const e=document.querySelector('.eyebrow');if(e)e.textContent='SERVICE PROVIDER WORKSPACE';}
 $('#epn').value=data.name||''; $('#epc').value=data.category||''; $('#epl').value=data.location||''; $('#epd').value=data.description||''; $('#epp').value=data.phone||''; $('#epw').value=data.website||''; $('#eprice').value=data.price||''; $('#epriceUnit').value=data.price_unit||''; $('#eparea').value=data.service_area||'';
 $('#editForm').dataset.id=id;
}
async function editProfile(e){
 e.preventDefault(); const id=e.target.dataset.id; if(!id){$('#editMsg').textContent='Missing profile ID.';return;}
 const payload={p_listing_id:id,p_name:$('#epn').value.trim(),p_category:$('#epc').value,p_location:$('#epl').value.trim(),p_description:$('#epd').value.trim(),p_phone:$('#epp').value.trim()||null,p_website:$('#epw').value.trim()||null,p_price:$('#eprice').value.trim()||null,p_price_unit:$('#epriceUnit').value.trim()||null,p_service_area:$('#eparea').value.trim()||null};
 const {data,error}=await maltSupabase.rpc('malt_update_my_listing',payload);
 if(error||!data){$('#editMsg').textContent=error?.message||'Update failed.';return;}
 $('#editMsg').textContent='✓ Updated and sent for admin review again.'; setTimeout(()=>location='dashboard.html',800);
}
window.editProfile=editProfile;
async function createProfile(e){
 e.preventDefault(); const {data:{user}}=await maltSupabase.auth.getUser(); if(!user){location='login.html';return}
 const role=await getAccountRole(user); if(role==='admin'){ $('#msg').textContent='Administrators manage listings from the Admin panel.'; return; }
 const allowed={user:['Service Provider','Business','Organization','Community'],business:['Business'],service_provider:['Service Provider'],organization:['Organization']}[role]||['Service Provider'];
 const selected=$('#pt').value; if(!allowed.includes(selected)){ $('#msg').textContent='Your '+roleLabel(role)+' account can only publish: '+allowed.join(', ')+'.'; return; }
 let logo_url=null; if($('#plogo')?.files?.[0] && window.v27Upload){ try{ logo_url=await v27Upload($('#plogo').files[0],'listing-logo'); }catch(err){ $('#msg').textContent='Logo upload failed: '+err.message; return; }} const payload={name:$('#pn').value,category:$('#pc').value,type:selected,location:$('#pl').value,description:$('#pd').value,phone:$('#pp').value||null,website:$('#pw').value||null,logo_url,price:$('#pprice')?.value.trim()||null,price_unit:$('#ppriceUnit')?.value.trim()||null,service_area:$('#parea')?.value.trim()||null,verified:false,status:'pending',owner_id:user.id};
 const {error}=await maltSupabase.from('listings').insert(payload);
 $('#msg').textContent=error?error.message:'✓ Profile submitted online for admin review.'; if(!error)e.target.reset();
}
async function prepareCreateService(){
 const {data:{user}}=await maltSupabase.auth.getUser(); if(!user){location='login.html';return}
 const role=await getAccountRole(user); if(role!=='service_provider'){location='create-profile.html';return}
 if($('#sc'))CATEGORIES.forEach(x=>$('#sc').insertAdjacentHTML('beforeend',`<option>${esc(x)}</option>`));
}
async function createService(e){
 e.preventDefault(); const {data:{user}}=await maltSupabase.auth.getUser(); if(!user){location='login.html';return}
 const role=await getAccountRole(user); if(role!=='service_provider'){ $('#serviceMsg').textContent='Only Service Provider accounts can publish services.';return; }
 const payload={name:$('#sn').value.trim(),category:$('#sc').value, type:'Service Provider',location:$('#sl').value.trim(),description:$('#sd').value.trim(),phone:$('#sp').value.trim()||null,website:$('#sw').value.trim()||null,price:$('#sprice').value.trim()||null,price_unit:$('#spriceUnit').value.trim()||null,service_area:$('#sarea').value.trim()||null,verified:false,status:'pending',owner_id:user.id};
 const {error}=await maltSupabase.from('listings').insert(payload);
 $('#serviceMsg').textContent=error?error.message:'✓ Service submitted for admin review.';
 if(!error){e.target.reset();setTimeout(()=>location='dashboard.html',900);}
}
async function admin(){
 const {data:{user}}=await maltSupabase.auth.getUser();
 if(!user){$('#adminMsg').textContent='Please log in first.';return}
 const {data,error}=await maltSupabase.rpc('malt_is_admin');
 if(error||!data){$('#adminMsg').textContent='Admin access is not enabled for this account yet.';return}
 $('#adminMsg').textContent='Admin access granted. Loading submissions…';
 const r=await maltSupabase.from('listings').select('*').in('status',['pending','active','rejected']).order('created_at',{ascending:false});
 if(r.error){$('#adminMsg').textContent='Admin access granted, but listings could not be loaded.';$('#adminListings').innerHTML='<div class="card"><h3>Listings could not be loaded</h3><p>'+esc(r.error.message)+'</p><p class="muted">Run the v15 admin RLS migration in Supabase SQL Editor, then refresh this page.</p></div>';return}
 const rows=r.data||[];
 rows.sort((a,b)=>({pending:0,active:1,rejected:2}[a.status]??9)-({pending:0,active:1,rejected:2}[b.status]??9) || new Date(b.created_at)-new Date(a.created_at));
 const pending=rows.filter(x=>x.status==='pending').length;
 $('#adminMsg').textContent=`Admin access granted. ${pending} pending submission${pending===1?'':'s'}.`;
 $('#adminListings').innerHTML=rows.map(x=>`<article class="card"><span class="pill">${esc(x.status)}</span><h3>${esc(x.name)}</h3><p class="muted">${esc(x.category)} · ${esc(x.type)} · ${esc(x.location)}</p><p>${esc(x.description)}</p>${x.phone?`<p><strong>Phone:</strong> ${esc(x.phone)}</p>`:''}${x.website?`<p><a href="${esc(x.website)}" target="_blank" rel="noopener">Website →</a></p>`:''}${x.price?`<p><strong>Rate:</strong> ${esc(x.price)}${x.price_unit?' / '+esc(x.price_unit):''}</p>`:''}${x.service_area?`<p><strong>Service area:</strong> ${esc(x.service_area)}</p>`:''}<div class="actions">${x.status==='pending'?`<button onclick="setStatus('${x.id}','active')">✓ Approve</button><button class="secondary" onclick="setStatus('${x.id}','rejected')">✕ Reject</button>`:x.status==='active'?`<button class="secondary" onclick="setStatus('${x.id}','rejected')">Reject</button>`:`<button onclick="setStatus('${x.id}','active')">Approve again</button>`}</div></article>`).join('')||'<div class="card"><h3>No listings yet.</h3><p>New profiles will appear here after submission.</p></div>';
}
async function setStatus(id,status){
 const {data,error}=await maltSupabase.rpc('malt_set_listing_status',{p_listing_id:id,p_status:status});
 if(error||!data){alert(error?.message||'Update failed');return} location.reload();
}
async function events(){let q=($('#eq')?.value||'').toLowerCase();$('#events').innerHTML=EVENTS.filter(x=>(x.name+' '+x.description+' '+x.location).toLowerCase().includes(q)).map(x=>`<article class="card"><span class="pill">${esc(x.date||'')}</span><h3>${esc(x.name)}</h3><p class="muted">${esc(x.category||'Event')} · ${esc(x.location)}</p><p>${esc(x.description)}</p></article>`).join('')||'<div class="card">No events found.</div>'}
async function opps(){let q=($('#oq')?.value||'').toLowerCase();$('#opps').innerHTML=OPPORTUNITIES.filter(x=>(x.name+' '+x.description+' '+x.location).toLowerCase().includes(q)).map(x=>`<article class="card"><span class="pill">${esc(x.type)}</span><h3>${esc(x.name)}</h3><p class="muted">${esc(x.location)}</p><p>${esc(x.description)}</p></article>`).join('')||'<div class="card">No opportunities found.</div>'}

async function prepareCreateProfile(){
 const {data:{user}}=await maltSupabase.auth.getUser(); if(!user){location='login.html';return}
 const role=await getAccountRole(user);
 if(role==='service_provider'){location.replace('create-service.html');return;}
 const select=$('#pt');
 const allowed={user:['Service Provider','Business','Organization','Community'],business:['Business'],service_provider:['Service Provider'],organization:['Organization']}[role]||['Service Provider'];
 if($('#profileRole'))$('#profileRole').textContent=roleLabel(role);
 if($('#profileRoleMsg'))$('#profileRoleMsg').textContent='This account can publish: '+allowed.join(', ')+'.';
 if(select){select.innerHTML=allowed.map(x=>`<option value="${esc(x)}">${esc(x)}</option>`).join('');}
}
async function init(){

 if(!online())return;
 await loadData();
 if($('#categories'))$('#categories').innerHTML=CATEGORIES.map(x=>`<a class="cat" href="discover.html?category=${encodeURIComponent(x)}"><b>✦</b>${esc(x)}<small>Discover local options</small></a>`).join('');
 if($('#cat')){CATEGORIES.forEach(x=>$('#cat').insertAdjacentHTML('beforeend',`<option>${esc(x)}</option>`));render();$('#searchBtn')?.addEventListener('click',render);['q','loc','cat','type'].forEach(id=>$('#'+id)?.addEventListener('input',render))}
 if($('#profile'))showProfile();
 if($('#createForm')){CATEGORIES.forEach(x=>$('#pc').insertAdjacentHTML('beforeend',`<option>${esc(x)}</option>`));$('#createForm').addEventListener('submit',createProfile);prepareCreateProfile()}
 if($('#serviceForm')){$('#serviceForm').addEventListener('submit',createService);prepareCreateService()}
 if($('#editForm')){CATEGORIES.forEach(x=>$('#epc').insertAdjacentHTML('beforeend',`<option>${esc(x)}</option>`));$('#editForm').addEventListener('submit',editProfile);prepareEditProfile()}
 if($('#loginForm'))$('#loginForm').addEventListener('submit',login);
 if($('#registerForm'))$('#registerForm').addEventListener('submit',register);
 if($('#hello'))dashboard();
 if($('#logout'))$('#logout').addEventListener('click',async()=>{await maltSupabase.auth.signOut();location='login.html'});
 if($('#adminListings'))admin();
 if($('#events')){$('#eq').addEventListener('input',events);events()}
 if($('#opps')){$('#oq').addEventListener('input',opps);opps()}
}
init();
