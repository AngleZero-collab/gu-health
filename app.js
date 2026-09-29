'use strict';
const $=(selector,scope=document)=>scope.querySelector(selector);
const $$=(selector,scope=document)=>[...scope.querySelectorAll(selector)];
const STORAGE_KEY='guhealth-web-v2';
const POLICY_REVIEW_DATE='2026-08-31';
const OFFICIAL_CANCER='https://www.hpa.gov.tw/Pages/List.aspx?nodeid=211';
const OFFICIAL_ADULT='https://www.mohw.gov.tw/cp-2704-82262-1.html';
const today=()=>new Date().toLocaleDateString('sv-SE');
const defaultState={
  version:2,demo:true,
  profile:{name:'林媽媽（示範）',birth:'1954-03-15',sex:'女',city:'台北市',indigenous:false,polio:false,familyColon:false,familyLung:false,smokes:false,betel:false,packYears:0,quitYears:-1},
  records:[
    {id:'demo-adult',kind:'screening',key:'adult',title:'成人預防保健',date:'2026-04-10',note:'示範資料：已完成成人預防保健服務。',flag:false},
    {id:'demo-colon',kind:'screening',key:'colon',title:'大腸癌篩檢',date:'2026-06-18',note:'示範資料：已完成採便檢查。',flag:false},
    {id:'demo-dental',kind:'screening',key:'dental',title:'牙科定期追蹤',date:'2026-08-23',note:'示範資料：例行口腔追蹤。',flag:false},
    {id:'demo-note',kind:'note',key:'',title:'陪媽媽一起散步',date:'2026-08-29',note:'示範資料：到公園走了 20 分鐘。',flag:false}
  ]
};
function loadState(){
  try{
    const data=JSON.parse(localStorage.getItem(STORAGE_KEY));
    if(data?.profile&&Array.isArray(data.records)){
      if(data.demo){
        defaultState.records.filter(record=>record.kind==='screening').forEach(record=>{if(!data.records.some(saved=>saved.id===record.id))data.records.push(structuredClone(record));});
      }
      return data;
    }
  }catch{}
  return structuredClone(defaultState);
}
let state=loadState();
let currentView='home';
let activePolicy=null;
let vision={eye:0,left:'',right:''};
const policies=[
  {id:'adult',title:'成人預防保健',summary:'從血壓、血糖到腎功能，認識自己的健康狀況。',rule:'30–39 歲每 5 年；40–64 歲每 3 年；65 歲以上每年。指定身分另有例外。',eligible:p=>age()>=30,months:p=>age()>=65||(p.indigenous&&age()>=55)||(p.polio&&age()>=35)?12:age()>=40?36:60,source:OFFICIAL_ADULT,prep:'攜帶健保卡；先向院所確認是否需要空腹，不要自行停藥。'},
  {id:'colon',title:'大腸癌篩檢',summary:'使用採便檢查，協助及早發現大腸異常。',rule:'45–74 歲，或 40–44 歲且一等親有大腸癌病史者，每 2 年 1 次。',eligible:p=>(age()>=45&&age()<=74)||(age()>=40&&age()<=44&&p.familyColon),months:()=>24,source:OFFICIAL_CANCER,prep:'先向合約院所領取採便管，依院所指示採檢並送回。'},
  {id:'breast',title:'乳癌篩檢',summary:'以乳房 X 光攝影協助及早發現乳房變化。',rule:'40–74 歲女性，每 2 年 1 次。',eligible:p=>p.sex==='女'&&age()>=40&&age()<=74,months:()=>24,source:OFFICIAL_CANCER,prep:'請預約並告知是否懷孕、哺乳或有乳房手術史。'},
  {id:'cervical',title:'子宮頸癌篩檢',summary:'透過抹片定期追蹤子宮頸細胞變化。',rule:'25–29 歲女性每 3 年；30 歲以上女性每年補助 1 次。',eligible:p=>p.sex==='女'&&age()>=25,months:()=>age()<30?36:12,source:OFFICIAL_CANCER,prep:'請向婦產科確認檢查前注意事項與個人適用性。'},
  {id:'hpv',title:'子宮頸病毒檢查',summary:'了解人類乳突病毒感染狀況。',rule:'35、45、65 歲女性各可接受 1 次補助檢測。',eligible:p=>p.sex==='女'&&[35,45,65].includes(age()),months:()=>0,source:OFFICIAL_CANCER,prep:'檢測結果需由醫事人員說明，不要自行判定癌症。'},
  {id:'lung',title:'肺癌篩檢',summary:'針對符合條件的高風險族群安排肺部檢查。',rule:'具肺癌一等親家族史，或 50–74 歲且符合吸菸條件者，每 2 年 1 次。',eligible:p=>(p.familyLung&&age()<=74&&age()>=(p.sex==='女'?40:45))||(age()>=50&&age()<=74&&Number(p.packYears)>=20&&(p.smokes||(Number(p.quitYears)>=0&&Number(p.quitYears)<=15))),months:()=>24,source:OFFICIAL_CANCER,prep:'由醫師確認家族史、吸菸史、適用資格與檢查風險。'},
  {id:'oral',title:'口腔癌篩檢',summary:'留意口腔黏膜、潰瘍與異常變化。',rule:'30 歲以上吸菸或曾嚼檳榔者；部分原住民族群另有條件，每 2 年 1 次。',eligible:p=>(age()>=30&&(p.smokes||p.betel))||(age()>=18&&age()<=29&&p.indigenous&&p.betel),months:()=>24,source:OFFICIAL_CANCER,prep:'攜帶健保卡，向牙科或耳鼻喉科詢問。持續不癒的潰瘍請直接就醫。'},
  {id:'dental',title:'牙科定期追蹤',public:false,summary:'一般牙科追蹤，不等同公費癌症篩檢。',rule:'頻率與費用由牙醫評估。',eligible:()=>true,months:()=>0,source:'https://www.hpa.gov.tw/',prep:'攜帶過去治療紀錄，告知用藥與身體狀況。'},
  {id:'eye',title:'眼科定期追蹤',public:false,summary:'一般眼科追蹤，並非人人適用的免費篩檢。',rule:'頻率與費用請諮詢眼科。',eligible:()=>true,months:()=>0,source:'https://www.nei.nih.gov/eye-health-information/eye-conditions-and-diseases/age-related-macular-degeneration',prep:'視線突然變形、黑影或視力下降，請儘速就醫。'}
];
function save(){try{localStorage.setItem(STORAGE_KEY,JSON.stringify(state));}catch{toast('瀏覽器無法儲存資料，請檢查隱私設定。');}}
function parseDate(value){const [y,m,d]=String(value).split('-').map(Number);return new Date(y,m-1,d);}
function ageAt(birth,date=new Date()){const b=parseDate(birth);let n=date.getFullYear()-b.getFullYear();if(date.getMonth()<b.getMonth()||(date.getMonth()===b.getMonth()&&date.getDate()<b.getDate()))n--;return n;}
function age(){return ageAt(state.profile.birth);}
function addMonths(value,months){const d=parseDate(value);d.setMonth(d.getMonth()+months);return d;}
function lastRecord(key){return state.records.filter(r=>r.kind==='screening'&&r.key===key).sort((a,b)=>b.date.localeCompare(a.date))[0]||null;}
function frequency(policy){if(policy.id==='hpv')return '35、45、65 歲各 1 次';const m=policy.months(state.profile);return m===12?'每年 1 次':m>12?`每 ${m/12} 年 1 次`:'依醫師建議';}
function policyStatus(policy){
  const last=lastRecord(policy.id);if(!last)return '尚無受檢日期紀錄';
  if(policy.id==='hpv'&&ageAt(state.profile.birth,parseDate(last.date))===age())return `已登錄：${last.date}`;
  const next=addMonths(last.date,policy.months(state.profile));return next>new Date()?`已登錄：${last.date}`:`可再次向院所確認（上次：${last.date}）`;
}
function isDue(policy){
  if(policy.public===false||!policy.eligible(state.profile))return false;
  const last=lastRecord(policy.id);if(!last)return true;
  if(policy.id==='hpv')return ageAt(state.profile.birth,parseDate(last.date))!==age();
  return addMonths(last.date,policy.months(state.profile))<=new Date();
}
function displayDate(value){const [y,m,d]=String(value).split('-');return `${y} 年 ${Number(m)} 月 ${Number(d)} 日`;}
function taskMeta(policy,last){
  if(!last)return `尚未登錄完成日期・${frequency(policy)}`;
  if(policy.id==='hpv')return isDue(policy)?`上次完成：${displayDate(last.date)}・目前階段待向院所確認`:`完成日期：${displayDate(last.date)}・本階段已登錄`;
  const next=addMonths(last.date,policy.months(state.profile));const nextValue=next.toLocaleDateString('sv-SE');
  return isDue(policy)?`上次完成：${displayDate(last.date)}・可再次向院所確認`:`上次完成：${displayDate(last.date)}・${displayDate(nextValue)}後再確認`;
}
function icon(name){return `<svg class="icon" aria-hidden="true"><use href="#i-${name}"></use></svg>`;}
function toast(message){const el=$('#toast');el.textContent=message;el.classList.add('show');clearTimeout(toast.timer);toast.timer=setTimeout(()=>el.classList.remove('show'),3500);}
function renderAll(){renderHome();renderRecords();populatePolicySelect();}
function renderHome(){
  const due=policies.filter(isDue);$('#homeTitle').textContent=`${state.profile.name}，有 ${due.length} 項健檢可確認`;
  $('#homeSummary').textContent=`${age()} 歲・${state.profile.city||'未填縣市'}。依您填寫的資料初步整理。`;
  $('#profileBadge').textContent=state.demo?'示範資料':'本機健康檔案';
  const list=$('#screeningList');list.replaceChildren();$('#screeningEmpty').hidden=due.length!==0;
  due.forEach(policy=>{
    const card=document.createElement('article');card.className='screening-card';
    const h=document.createElement('h3');h.textContent=policy.title;
    const status=document.createElement('p');status.className='status';status.textContent='可向院所確認';
    const summary=document.createElement('p');summary.className='summary';summary.textContent=policy.summary;
    const freq=document.createElement('p');freq.className='frequency';freq.textContent=frequency(policy);
    const actions=document.createElement('div');actions.className='card-actions';
    const phone=document.createElement('a');phone.className='primary-button phone-link';phone.href='tel:';phone.innerHTML=`${icon('phone')}打電話預約`;phone.addEventListener('click',()=>toast('請輸入院所電話；也可以先使用地圖查詢。'));
    const map=document.createElement('a');map.className='secondary-button map-link';map.target='_blank';map.rel='noopener';map.href=mapsUrl(policy);map.innerHTML=`${icon('map')}尋找附近院所`;
    const detail=document.createElement('button');detail.className='detail-link';detail.type='button';detail.textContent='查看資格與注意事項';detail.addEventListener('click',()=>openPolicy(policy));
    actions.append(phone,map,detail);card.append(h,status,summary,freq,actions);list.append(card);
  });
}
function mapsUrl(policy){return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${state.profile.city||'台灣'} ${policy.title} 院所`)}`;}
function openPolicy(policy){
  activePolicy=policy;$('#screeningDialogTitle').textContent=policy.title;$('#screeningDialogSummary').textContent=policy.summary;
  $('#screeningDialogRule').textContent=policy.rule;$('#screeningDialogFrequency').textContent=frequency(policy);$('#screeningDialogStatus').textContent=policyStatus(policy);$('#screeningDialogPrep').textContent=policy.prep;
  $('#screeningMapLink').href=mapsUrl(policy);$('#screeningSourceLink').href=policy.source;$('#screeningDialog').showModal();
}
function navigate(view){
  if(!['home','vision','records'].includes(view))view='home';currentView=view;
  $$('.view').forEach(section=>{const active=section.id===`view-${view}`;section.hidden=!active;section.classList.toggle('active',active);});
  $$('.nav-button').forEach(button=>{const active=button.dataset.nav===view;button.classList.toggle('active',active);active?button.setAttribute('aria-current','page'):button.removeAttribute('aria-current');});
  $('#guideButton').hidden=view!=='home';history.replaceState(null,'',`#${view}`);window.scrollTo({top:0,behavior:'smooth'});
}
function openProfile(){
  const form=$('#profileForm');for(const [key,value] of Object.entries(state.profile)){const field=form.elements[key];if(!field)continue;if(field.type==='checkbox')field.checked=Boolean(value);else field.value=value;}
  form.elements.consent.checked=false;$('#profileDialog').showModal();
}
function renderRecords(){
  renderScreeningProgress();
  const list=$('#recordList');list.replaceChildren();const records=[...state.records].sort((a,b)=>b.date.localeCompare(a.date)||String(b.id).localeCompare(String(a.id)));
  $('#recordEmpty').hidden=records.length!==0;let day='';
  records.forEach(record=>{
    if(day!==record.date){day=record.date;const date=document.createElement('div');date.className='record-date';date.textContent=day;list.append(date);}
    const card=document.createElement('article');card.className='record-card';const h=document.createElement('h3');h.textContent=record.title;const p=document.createElement('p');p.textContent=record.note||'自行登錄的健康紀錄。';card.append(h,p);
    if(record.flag){const flag=document.createElement('span');flag.className='record-flag';flag.textContent='有變化，建議諮詢醫師';card.append(flag);}
    const remove=document.createElement('button');remove.className='text-button';remove.type='button';remove.textContent='刪除這筆紀錄';remove.addEventListener('click',()=>{if(confirm(`確定刪除「${record.title}」？這個動作無法復原。`)){state.records=state.records.filter(r=>r.id!==record.id);save();renderAll();toast('紀錄已刪除');}});card.append(remove);list.append(card);
  });
}
function renderScreeningProgress(){
  const eligible=policies.filter(policy=>policy.public!==false&&policy.eligible(state.profile));
  const current=eligible.filter(policy=>!isDue(policy));const due=eligible.filter(isDue);const total=eligible.length;
  $('#screeningProgressTitle').textContent=`${state.profile.name}的健檢進度`;
  $('#screeningProgressCount').textContent=total?`${current.length} / ${total} 項已登錄`:'目前沒有適用項目';
  $('#screeningProgressSummary').textContent=total?`${current.length} 項目前已登錄，${due.length} 項待向院所確認。`:'依目前健康檔案，尚未整理出適用的公費篩檢項目。';
  const track=$('#screeningProgressTrack');track.setAttribute('aria-valuemax',String(total));track.setAttribute('aria-valuenow',String(current.length));
  $('#screeningProgressFill').style.width=`${total?Math.round(current.length/total*100):0}%`;
  const list=$('#screeningTaskList');list.replaceChildren();
  [...due,...current].forEach(policy=>{
    const pending=isDue(policy);const last=lastRecord(policy.id);const card=document.createElement('article');card.className=`screening-task ${pending?'pending':'complete'}`;
    const marker=document.createElement('div');marker.className='task-marker';marker.innerHTML=icon(pending?'file':'check');
    const copy=document.createElement('div');copy.className='task-copy';const h=document.createElement('h3');h.textContent=policy.title;const meta=document.createElement('p');meta.textContent=taskMeta(policy,last);copy.append(h,meta);
    const controls=document.createElement('div');controls.className='task-controls';const status=document.createElement('span');status.className='task-status';status.textContent=pending?'待確認':'目前已完成';
    const record=document.createElement('button');record.type='button';record.className=pending?'primary-button task-button':'secondary-button task-button';record.textContent=last?'更新完成日期':'登錄已完成';record.addEventListener('click',()=>openRecordDialog(policy.id));
    const detail=document.createElement('button');detail.type='button';detail.className='text-button task-button';detail.textContent='查看資格';detail.addEventListener('click',()=>openPolicy(policy));
    controls.append(status,record,detail);card.append(marker,copy,controls);list.append(card);
  });
  if(!total){const empty=document.createElement('div');empty.className='progress-empty';empty.textContent='請先編輯健康檔案，或向家庭醫學科詢問適合的預防照護。';list.append(empty);}
}
function populatePolicySelect(){const select=$('#recordPolicy');select.replaceChildren();policies.forEach(p=>select.add(new Option(p.title,p.id)));}
function updateRecordFields(){
  const kind=$('#recordKind').value;$('#recordTitleField').hidden=kind!=='note';$('#recordPolicyField').hidden=kind!=='screening';$('#recordBpFields').hidden=kind!=='bp';
}
function openRecordDialog(policyId=''){
  const form=$('#recordForm');form.reset();form.elements.date.value=today();form.elements.kind.value=policyId?'screening':'note';updateRecordFields();if(policyId)form.elements.policy.value=policyId;$('#recordDialog').showModal();
}
function addRecord(event){
  event.preventDefault();const form=event.currentTarget;const data=new FormData(form);const kind=data.get('kind');const date=String(data.get('date'));
  if(!date||date>today()){toast('完成紀錄不能使用未來日期。');return;}
  let title='',key='',note=String(data.get('note')||'').trim(),flag=false;
  if(kind==='note'){title=String(data.get('title')||'').trim();if(!title){toast('請填寫標題。');return;}}
  if(kind==='screening'){key=String(data.get('policy'));title=policies.find(p=>p.id===key)?.title||'已完成的檢查';}
  if(kind==='bp'){const sys=Number(data.get('systolic')),dia=Number(data.get('diastolic'));if(sys<30||sys>300||dia<20||dia>200||sys<=dia){toast('請確認血壓數值與順序。');return;}title=`血壓量測 ${sys} / ${dia} mmHg`;note=[note,'僅記錄數值，不自動診斷。'].filter(Boolean).join('\n');}
  state.records.push({id:crypto.randomUUID(),kind,key,title,date,note,flag});state.demo=false;save();form.reset();form.elements.date.value=today();updateRecordFields();$('#recordDialog').close();renderAll();navigate('records');toast('紀錄已儲存');
}
function startVision(){vision={eye:0,left:'',right:''};$('#visionLanding').hidden=true;$('#visionTest').hidden=false;$('#visionGridArea').hidden=false;$('#visionAnswerArea').hidden=false;$('#visionResult').hidden=true;renderVisionEye();}
function renderVisionEye(){$('#visionInstruction').textContent=vision.eye===0?'請遮住右眼，用左眼看中央黑點':'請遮住左眼，用右眼看中央黑點';}
function answerVision(value){if(vision.eye===0){vision.left=value;vision.eye=1;renderVisionEye();return;}vision.right=value;showVisionResult();}
function showVisionResult(){
  $('#visionGridArea').hidden=true;$('#visionAnswerArea').hidden=true;$('#visionResult').hidden=false;
  $('#visionResultText').textContent=`左眼：${vision.left}\n右眼：${vision.right}`;const changed=vision.left!=='看得很清楚'||vision.right!=='看得很清楚';
  $('#visionResultAdvice').innerHTML=changed?'<strong>建議聯絡眼科</strong><p>您回報有變化或看不清楚；若是突然出現，請儘速就醫。</p>':'<strong>請持續留意</strong><p>看得清楚也不能排除疾病，請依眼科醫師建議追蹤。</p>';
}
function finishVision(saveResult){
  if(saveResult){const changed=vision.left!=='看得很清楚'||vision.right!=='看得很清楚';state.records.push({id:crypto.randomUUID(),kind:'vision',key:'amsler',title:'方格視覺自我觀察',date:today(),note:`左眼：${vision.left}\n右眼：${vision.right}\n主觀觀察，未經臨床校準，非診斷結果。`,flag:changed});state.demo=false;save();renderAll();toast('觀察已儲存');navigate('records');}
  $('#visionTest').hidden=true;$('#visionLanding').hidden=false;if(!saveResult)navigate('vision');
}
function guideReply(question){
  const q=question.toLowerCase();
  if(/胸痛|喘不過氣|呼吸困難|昏倒|嘴歪|手腳無力|說話不清|不想活/.test(q))return '我不能進行診斷。若現在胸痛、呼吸困難、突然無力或有立即危險，請立刻撥打 119，或請身邊的人協助去急診。不要等待網頁回答或自行開車。';
  if(/眼|視力|看不清|黑影|線條/.test(q))return '我不能判定眼睛是否有疾病。若視力突然下降、線條變形或出現黑影，請儘速就醫。平時的視覺困擾，可以向眼科詢問。';
  if(/藥|劑量|停藥|加藥/.test(q))return '我不能建議自行調整藥物或劑量。請帶著藥袋詢問原開藥醫師或藥師，不要自行停藥或加藥。若有急性嚴重不適，請立即就醫。';
  if(/篩檢|健檢|檢查/.test(q))return '可以在首頁查看初步符合的篩檢項目。實際資格、時間與費用請向院所確認，也可以詢問家庭醫學科。若已有不舒服的症狀，請直接就醫，不要只等待篩檢。';
  return '我是提供基本衛教的小幫手，不能進行醫療診斷。身體持續不舒服或不知道要看哪一科，可以先詢問家庭醫學科。若突然出現嚴重不適，請立即就醫。';
}
function askGuide(){const input=$('#guideQuestion');const answer=$('#guideAnswer');const value=input.value.trim();if(!value){toast('請先輸入想詢問的內容。');return;}answer.textContent=guideReply(value);answer.hidden=false;input.value='';}
function exportData(){const data={format:'guhealth-web-backup',version:2,exportedAt:new Date().toISOString(),policyReviewDate:POLICY_REVIEW_DATE,profile:state.profile,records:state.records};const blob=new Blob([JSON.stringify(data,null,2)],{type:'application/json'});const link=document.createElement('a');link.href=URL.createObjectURL(blob);link.download=`guhealth-${today()}.json`;link.click();setTimeout(()=>URL.revokeObjectURL(link.href),1000);toast('備份已下載，請妥善保管。');}
$$('[data-nav]').forEach(el=>el.addEventListener('click',event=>{event.preventDefault();navigate(el.dataset.nav);}));
$('#profileButton').addEventListener('click',openProfile);$('#switchProfileButton').addEventListener('click',openProfile);
$$('[data-close-dialog]').forEach(button=>button.addEventListener('click',()=>document.getElementById(button.dataset.closeDialog).close()));
$('#profileForm').addEventListener('submit',event=>{event.preventDefault();const form=event.currentTarget;if(!form.reportValidity())return;const data=new FormData(form);state.profile={name:String(data.get('name')).trim(),birth:String(data.get('birth')),sex:String(data.get('sex')),city:String(data.get('city')||'').trim(),indigenous:data.has('indigenous'),polio:data.has('polio'),familyColon:data.has('familyColon'),familyLung:data.has('familyLung'),smokes:data.has('smokes'),betel:data.has('betel'),packYears:Number(data.get('packYears')||0),quitYears:Number(data.get('quitYears')??-1)};state.demo=false;save();$('#profileDialog').close();renderAll();toast('健康檔案已儲存在這個瀏覽器');});
$('#addRecordButton').addEventListener('click',()=>openRecordDialog());
$('#recordKind').addEventListener('change',updateRecordFields);$('#recordForm').addEventListener('submit',addRecord);$('#exportButton').addEventListener('click',exportData);
$('#startVisionButton').addEventListener('click',startVision);$('#exitVisionButton').addEventListener('click',()=>finishVision(false));$$('[data-vision-answer]').forEach(button=>button.addEventListener('click',()=>answerVision(button.dataset.visionAnswer)));$('#saveVisionButton').addEventListener('click',()=>finishVision(true));$('#discardVisionButton').addEventListener('click',()=>finishVision(false));
$('#guideButton').addEventListener('click',()=>{$('#guideAnswer').hidden=true;$('#guideAnswer').textContent='';$('#guideQuestion').value='';$('#guideDialog').showModal();});$('#askGuideButton').addEventListener('click',askGuide);
$$('dialog').forEach(dialog=>dialog.addEventListener('click',event=>{if(event.target===dialog)dialog.close();}));
window.addEventListener('hashchange',()=>navigate(location.hash.slice(1)||'home'));
renderAll();navigate(location.hash.slice(1)||'home');
if('serviceWorker'in navigator)window.addEventListener('load',()=>navigator.serviceWorker.register('./sw.js?v=8').catch(()=>{}));

