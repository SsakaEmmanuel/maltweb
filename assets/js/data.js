const CATEGORIES=['Schools','Churches','Mosques','Traders','Services','Graphics Designers','Web Developers','Charity & Outreach','Entertainment','Projects','Deals','Other'];

// Demo data is used only until your Supabase project is connected.
const DEMO_LISTINGS=[
{id:'maltweb',name:'MALTWEB',category:'Web Developers',type:'Service Provider',location:'Kampala',verified:true,description:'A local digital ecosystem for discovering businesses, services, organizations, events and opportunities.',phone:'+256 700 000000'},
{id:'emma',name:'Emma Graphics',category:'Graphics Designers',type:'Service Provider',location:'Kampala',verified:true,description:'Creative graphics, posters, branding and social media designs.',phone:'+256 700 000002'},
{id:'kasiiso',name:'Kasiiso SDA Church',category:'Churches',type:'Organization',location:'Kasana',verified:true,description:'A local church community serving members and visitors.'},
{id:'school',name:'Bright Future School',category:'Schools',type:'Organization',location:'Luweero',verified:false,description:'Learning and education services for the community.'},
{id:'tech',name:'TechFix Uganda',category:'Services',type:'Service Provider',location:'Kampala',verified:false,description:'Computer maintenance and technology support.'},
{id:'hub',name:'Community Action Hub',category:'Charity & Outreach',type:'Community',location:'Wakiso',verified:true,description:'Community projects, outreach and volunteer activities.'}
];
const DEMO_EVENTS=[
{name:'Linya Egaali Concert 2027',category:'Entertainment',location:'Kampala',date:'2027-01-16',description:'A community music and worship event.'},
{name:'Community Innovation Meetup',category:'Projects',location:'Kampala',date:'2026-11-21',description:'Meet people building useful local projects.'},
{name:'Local Business Networking Day',category:'Traders',location:'Wakiso',date:'2026-12-05',description:'Connect with local business owners.'}
];
const DEMO_OPPORTUNITIES=[
{name:'Junior Web Developer',type:'Job',location:'Kampala',description:'Entry-level web development opportunity.'},
{name:'Community Project Volunteer',type:'Volunteer',location:'Wakiso',description:'Help with local outreach.'},
{name:'Small Business Growth Project',type:'Project',location:'Uganda',description:'Connect local businesses with digital visibility.'}
];

let LISTINGS=DEMO_LISTINGS;
let EVENTS=DEMO_EVENTS;
let OPPORTUNITIES=DEMO_OPPORTUNITIES;
