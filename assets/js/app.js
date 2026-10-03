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
function card(x){return `<article class="card listing-card"><div class="cardtop"><span class="pill">${x.verified?'✓ Verified':'Local listing'}</span><button class="iconbtn ${isSaved(x.id)?'saved':''}" onclick="toggleSaved('${x.id}')" aria-label="Save profile">${isSaved(x.id)?'♥':'♡'}</button></div><h3>${esc(x.name)}</h3><p class="muted">${esc(x.category)} · ${esc(x.type)} · ${esc(x.location)}</p><p>${esc(x.description)}</p><div class="actions"><a class="button" href="profile.html?id=${encodeURIComponent(x.id)}">View profile</a>${x.phone?`<a class="button secondary" href="tel:${esc(x.phone)}">Call</a>`:''}</div></article>`}
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
 const wa=waNumber(x.phone), saved=isSaved(x.id); let site='';
 if(x.website){let u=String(x.website).trim(); if(!/^https?:\/\//i.test(u))u='https://'+u; site=`<a class="button secondary" href="${esc(u)}" target="_blank" rel="noopener noreferrer">Visit website ↗</a>`}
 const reviews=await loadReviews(x.id);
 const avg=reviews.length?(reviews.reduce((sum,r)=>sum+Number(r.rating||0),0)/reviews.length).toFixed(1):'0.0';
 const reviewList=reviews.length?reviews.map(r=>`<article class="review"><div><strong>${stars(r.rating)}</strong> <span class="muted">${new Date(r.created_at).toLocaleDateString()}</span></div><p>${esc(r.review||'No written comment.')}</p></article>`).join(''):'<p class="muted">No reviews yet. Be the first to review this profile.</p>';
 $('#profile').innerHTML=`<div class="profile card"><div class="profile-head"><span class="pill">${x.verified?'✓ Verified':'Local listing'}</span><button class="iconbtn ${saved?'saved':''}" onclick="toggleSaved('${x.id}');showProfile()">${saved?'♥ Saved':'♡ Save'}</button></div><h1>${esc(x.name)}</h1><p class="profile-meta">${esc(x.category)} · ${esc(x.type)} · ${esc(x.location)}</p><div class="rating-summary"><strong>★ ${avg}</strong> <span class="muted">(${reviews.length} review${reviews.length===1?'':'s'})</span></div><div class="profile-section"><h2>About</h2><p>${esc(x.description)}</p></div><div class="profile-section"><h2>Contact</h2><p class="contact-number">${esc(x.phone||'Contact details available soon.')}</p><div class="actions">${x.phone?`<a class="button" href="tel:${esc(x.phone)}">☎ Call</a><a class="button whatsapp" href="https://wa.me/${wa}" target="_blank" rel="noopener noreferrer">WhatsApp</a>`:''}${site}</div></div><div class="profile-section"><h2>Reviews</h2><form class="review-form" onsubmit="event.preventDefault();submitReview('${x.id}')"><select id="reviewRating" required><option value="">Your rating</option><option value="5">5 — Excellent</option><option value="4">4 — Very good</option><option value="3">3 — Good</option><option value="2">2 — Fair</option><option value="1">1 — Poor</option></select><textarea id="reviewText" placeholder="Write a review (optional)"></textarea><button>Submit review</button><p id="reviewMsg" class="muted"></p></form><div class="reviews">${reviewList}</div></div><p class="muted">Share this profile with someone who needs this service.</p><div class="actions"><button class="secondary" onclick="navigator.clipboard?.writeText(location.href).then(()=>this.textContent='Link copied ✓')">Copy profile link</button><a class="button secondary" href="discover.html">← Back to Discover</a></div></div>`;
}
async function login(e){e.preventDefault();const {error}=await maltSupabase.auth.signInWithPassword({email:$('#email').value,password:$('#password').value});if(error){$('#msg').textContent=error.message;return}location='dashboard.html'}
async function register(e){e.preventDefault();const {data,error}=await maltSupabase.auth.signUp({email:$('#email').value,password:$('#password').value,options:{data:{full_name:$('#name').value,role:$('#role').value}}});if(error){$('#msg').textContent=error.message;return}$('#msg').textContent=data.session?'Account created.':'Account created. You can now log in.';setTimeout(()=>location='login.html',700)}
async function dashboard(){
 const {data:{user}}=await maltSupabase.auth.getUser(); if(!user){location='login.html';return}
 $('#hello').textContent='Hello, '+(user.user_metadata?.full_name||user.email.split('@')[0])+' 👋';
 const {data,error}=await maltSupabase.from('listings').select('*').eq('owner_id',user.id).order('created_at',{ascending:false});
 if(error){$('#myListings').innerHTML='<div class="card">'+esc(error.message)+'</div>';return}
 $('#myListings').innerHTML=(data||[]).map(x=>`<article class="card"><span class="pill">${esc(x.status)}</span><h3>${esc(x.name)}</h3><p class="muted">${esc(x.category)} · ${esc(x.location)}</p></article>`).join('')||'<div class="card">No submissions yet.</div>';
}
async function createProfile(e){
 e.preventDefault(); const {data:{user}}=await maltSupabase.auth.getUser(); if(!user){location='login.html';return}
 const payload={name:$('#pn').value,category:$('#pc').value,type:$('#pt').value,location:$('#pl').value,description:$('#pd').value,phone:$('#pp').value||null,website:$('#pw').value||null,verified:false,status:'pending',owner_id:user.id};
 const {error}=await maltSupabase.from('listings').insert(payload);
 $('#msg').textContent=error?error.message:'✓ Profile submitted online for admin review.'; if(!error)e.target.reset();
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
 $('#adminListings').innerHTML=rows.map(x=>`<article class="card"><span class="pill">${esc(x.status)}</span><h3>${esc(x.name)}</h3><p class="muted">${esc(x.category)} · ${esc(x.type)} · ${esc(x.location)}</p><p>${esc(x.description)}</p>${x.phone?`<p><strong>Phone:</strong> ${esc(x.phone)}</p>`:''}${x.website?`<p><a href="${esc(x.website)}" target="_blank" rel="noopener">Website →</a></p>`:''}<div class="actions">${x.status==='pending'?`<button onclick="setStatus('${x.id}','active')">✓ Approve</button><button class="secondary" onclick="setStatus('${x.id}','rejected')">✕ Reject</button>`:x.status==='active'?`<button class="secondary" onclick="setStatus('${x.id}','rejected')">Reject</button>`:`<button onclick="setStatus('${x.id}','active')">Approve again</button>`}</div></article>`).join('')||'<div class="card"><h3>No listings yet.</h3><p>New profiles will appear here after submission.</p></div>';
}
async function setStatus(id,status){
 const {data,error}=await maltSupabase.rpc('malt_set_listing_status',{p_listing_id:id,p_status:status});
 if(error||!data){alert(error?.message||'Update failed');return} location.reload();
}
async function events(){let q=($('#eq')?.value||'').toLowerCase();$('#events').innerHTML=EVENTS.filter(x=>(x.name+' '+x.description+' '+x.location).toLowerCase().includes(q)).map(x=>`<article class="card"><span class="pill">${esc(x.date||'')}</span><h3>${esc(x.name)}</h3><p class="muted">${esc(x.category||'Event')} · ${esc(x.location)}</p><p>${esc(x.description)}</p></article>`).join('')||'<div class="card">No events found.</div>'}
async function opps(){let q=($('#oq')?.value||'').toLowerCase();$('#opps').innerHTML=OPPORTUNITIES.filter(x=>(x.name+' '+x.description+' '+x.location).toLowerCase().includes(q)).map(x=>`<article class="card"><span class="pill">${esc(x.type)}</span><h3>${esc(x.name)}</h3><p class="muted">${esc(x.location)}</p><p>${esc(x.description)}</p></article>`).join('')||'<div class="card">No opportunities found.</div>'}

async function init(){

 if(!online())return;
 await loadData();
 if($('#categories'))$('#categories').innerHTML=CATEGORIES.map(x=>`<a class="cat" href="discover.html?category=${encodeURIComponent(x)}"><b>✦</b>${esc(x)}<small>Discover local options</small></a>`).join('');
 if($('#cat')){CATEGORIES.forEach(x=>$('#cat').insertAdjacentHTML('beforeend',`<option>${esc(x)}</option>`));render();$('#searchBtn')?.addEventListener('click',render);['q','loc','cat','type'].forEach(id=>$('#'+id)?.addEventListener('input',render))}
 if($('#profile'))showProfile();
 if($('#createForm')){CATEGORIES.forEach(x=>$('#pc').insertAdjacentHTML('beforeend',`<option>${esc(x)}</option>`));$('#createForm').addEventListener('submit',createProfile)}
 if($('#loginForm'))$('#loginForm').addEventListener('submit',login);
 if($('#registerForm'))$('#registerForm').addEventListener('submit',register);
 if($('#hello'))dashboard();
 if($('#logout'))$('#logout').addEventListener('click',async()=>{await maltSupabase.auth.signOut();location='login.html'});
 if($('#adminListings'))admin();
 if($('#events')){$('#eq').addEventListener('input',events);events()}
 if($('#opps')){$('#oq').addEventListener('input',opps);opps()}
}
init();
