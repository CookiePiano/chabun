/* 차분 · 밤공기 방향 전체 화면 시안. 아이콘 일부는 Feather Icons(MIT) 경로를 사용했습니다. */
const W=1682,H=1210;
const Q=new URLSearchParams(location.search);
const state={s:Q.get('s')||'parked',t:Q.get('t')||'night',ctx:Q.get('ctx')==='1',zone:Q.get('zone')==='1',call:+(Q.get('call')||0),g:+(Q.get('g')||0),h:Q.get('h')==='0'?0:1,open:Q.get('open')==='1',step:+(Q.get('step')||1),w:Q.get('w')||'3',auto:true,bare:Q.get('bare')==='1'};

const SCREENS=[
 ['주차 후 화면',[['parked','통화 메모'],['parked-empty','통화가 없을 때']]],
 ['오피스 모드',[['office-on','켜는 중'],['office-work','작업'],['office-meet','회의'],['office-low','배터리 부족'],['office-set','설정 바꾸기'],['office-off','원래대로 되돌리는 중'],['office-pause','멈춤'],['office-lock','잠김 (차가 움직일 때)']]],
 ['주행 중 작은 창',[['drive-idle','대기'],['drive-adjust','통화 중, 실내 조절'],['drive-miss','통화 중, 못 들은 구간'],['drive-ready','통화 종료 후 (Gleo가 물어봄)'],['drive-speak','Gleo와 대화 (요약을 들려줌)'],['drive-listen','Gleo와 대화 (묻고 답함)'],['drive-refuse','차량이 조작을 거부함'],['drive-off','자동 조절을 껐을 때']]],
 ['그 밖의 화면',[['consent','첫 실행 동의'],['card','상태 카드 (위젯)']]]
];
const NOTES={
 'parked':'통화 이름을 누르면 메모가 바뀌고, <b>전체 내용</b>을 누르면 아래에서 올라옵니다. <b>오피스 모드</b>를 누르면 켜는 순서로 이어집니다.',
 'parked-empty':'받은 통화가 없고 확인하지 않은 연락도 없을 때입니다. 연락 줄은 표시하지 않습니다.',
 'office-on':'운전대 → 시트 → 방석 → 등받이 → 실내 환경 순서로 자동 진행됩니다. <b>멈춤</b>을 누르면 바로 멈춥니다.',
 'office-work':'켜기가 끝나면 앞좌석을 크게 당겨, 허벅지 위에 노트북을 올리고 작업하는 모습을 보여 줍니다. <b>작업 | 회의</b>를 눌러 바꿉니다.',
 'office-meet':'얼굴 조명(무드등 흰색)과 옆 창 가림으로 바뀝니다. 시트는 다시 움직이지 않습니다.',
 'office-low':'차 배터리가 낮아 노트북 출력을 낮춘 상태입니다.',
 'office-set':'목표값을 − / + 로 바꿉니다. 바꾼 값은 다음에도 유지됩니다.',
 'office-off':'켤 때의 반대 순서로 되돌립니다. 시트를 먼저, 운전대를 마지막에 움직입니다.',
 'office-pause':'움직이는 도중 멈춘 상태입니다.',
 'office-lock':'차가 움직이는 동안에는 켤 수 없습니다.',
 'consent':'처음 실행했을 때 주차 중에만 보여 줍니다.',
 'card':'다른 앱을 쓰는 동안 시스템 화면에 놓이는 상태 카드입니다. 아이콘 하나와 두 줄입니다.'
};
const GLEO_NOTE=' Gleo가 묻고 운전자가 답하는 말이 대화처럼 차례로 쌓입니다. 지금 말은 크게, 지난 말은 작고 흐리게 위로 밀려납니다. 처음부터 끝까지 자동으로 이어집니다.';
const DRIVE_NOTE='왼쪽은 내비게이션(다른 앱), 오른쪽이 차분의 작은 창입니다. 창 크기는 아직 재지 않아 앱 창의 1/3과 1/2로 가정했습니다.';

/* ───────── 예시 자료 ───────── */
const PEOPLE={
 '김 팀장':{bg:'#C9D8FF',cloth:'#1F2A44',skin:'#F1C9A5',hair:'#2A2320',style:'short',glasses:1,collar:1},
 '최 수석':{bg:'#E4DDF8',cloth:'#3B3F55',skin:'#EEC4A0',hair:'#9C9CA6',style:'part',collar:1},
 '박 대리':{bg:'#FFE1B8',cloth:'#2F7F73',skin:'#F6D5BA',hair:'#3B2A22',style:'bob'},
 '이 과장':{bg:'#D3EEDD',cloth:'#4B5D7A',skin:'#EDC29C',hair:'#1F1B1A',style:'part',collar:1},
 '어머니':{bg:'#FFDDE8',cloth:'#A9507A',skin:'#F3CDB0',hair:'#4A3A36',style:'perm'}
};
function avatar(name){
 const p=PEOPLE[name]||{style:'none'};let g;
 if(p.style==='none'){ // 사진이나 그림을 등록하지 않은 연락처: 기본 사람 모양
  g=`<rect width="64" height="64" fill="#8D97AD"/><circle cx="32" cy="25" r="11" fill="#E9ECF3"/><path d="M10 66C10 49 20 42 32 42s22 7 22 24Z" fill="#E9ECF3"/>`;
 }else{
  const hair={
   short:`<path d="M20 28C17.5 13 25 9.5 32 9.5S46.5 13 44 28C42.5 22 39.5 19 32 19s-10.5 3-12 9Z" fill="${p.hair}"/>`,
   part:`<path d="M20 28.5C17 13 26 9.5 33 9.5c8.500 0 13.500 5 11 19C43 22.500 40.500 20 36.500 19.500 30 20 23.500 20 20 28.500Z" fill="${p.hair}"/>`,
   bob:`<path d="M17.500 40C14.500 20 21.500 9.500 32 9.500S49.500 20 46.500 40L41.500 40C43.500 31 43.500 24.500 40.500 20.500 35.500 23 28 22.500 23.500 20.500 20.500 24.500 20.500 31 22.500 40Z" fill="${p.hair}"/>`,
   perm:`<g fill="${p.hair}"><circle cx="23" cy="18" r="6"/><circle cx="30" cy="13.500" r="6.500"/><circle cx="38" cy="14.500" r="6.500"/><circle cx="43" cy="21" r="5.500"/><circle cx="20.500" cy="25" r="5"/><circle cx="44.500" cy="27" r="4.500"/></g>`
  }[p.style];
  g=`<rect width="64" height="64" fill="${p.bg}"/>${p.style==='bob'?`<path d="M17 42C13.500 20 21 9 32 9s18.500 11 15 33Z" fill="${p.hair}"/>`:''}
   <path d="M8 68C8 50 19 44 32 44s24 6 24 24Z" fill="${p.cloth}"/>${p.collar?'<path d="M26.500 44 32 51l5.500-7Z" fill="#fff"/>':''}
   <rect x="27.500" y="36" width="9" height="10" rx="4" fill="${p.skin}" style="filter:brightness(.93)"/>
   <ellipse cx="32" cy="27.500" rx="11" ry="12.500" fill="${p.skin}"/>${hair}
   ${p.glasses?'<g fill="none" stroke="#26262b" stroke-width="1.300"><rect x="23.500" y="26" width="7" height="5.200" rx="2.200"/><rect x="33.500" y="26" width="7" height="5.200" rx="2.200"/><path d="M30.500 28h3"/></g>':''}`;
 }
 return `<span class="avatar" role="img" aria-label="${name}"><svg viewBox="0 0 64 64">${g}</svg></span>`;
}
const WHY={turn:{label:'좌회전 중',icon:'turn'},wind:{label:'실내 소음',icon:'wind'},rain:{label:'폭우',icon:'rain'}};
const calls=[
 {name:'김 팀장',at:'오후 1:52',len:'8분 12초',short:'8분',end:'8:12',fresh:true,seed:7,
  sum:[['울산 2공장 납기를 11월 28일로 옮깁니다.',0],['변경 공문은 내가 오늘 오후에 고객사로 보내기로 했습니다.',1],['견적은 3% 올려 다시 내고, 인상 근거는 내가 금요일까지 공유합니다.',1]],
  missed:[{from:.325,to:.395,at:'2:40',why:'turn',q:'검수 인력은 두 명이 더 필요합니다. 다음 주 월요일부터 가능합니다.'},{from:.752,to:.812,at:'6:10',why:'wind',q:'현장 안전 점검은 목요일 오전 10시로 잡았습니다.'}],
  lines:[['0:05','김 팀장','울산 2공장 납품 일정부터 확인하겠습니다.'],['0:14','나','네, 일정표 보면서 듣고 있습니다.'],['0:31','김 팀장','부품 입고가 사흘 늦어져서 납기를 11월 28일로 옮겨야 합니다.'],['1:10','김 팀장','고객사에는 오늘 중으로 변경 공문을 보내 주세요.'],['1:22','나','공문은 제가 오후에 바로 보내겠습니다.'],['2:40','김 팀장','검수 인력은 두 명이 더 필요합니다. 다음 주 월요일부터 가능합니다.',1],['4:02','김 팀장','견적은 기존 금액에서 3퍼센트 올려 다시 내겠습니다.'],['4:20','나','인상 근거는 제가 정리해서 금요일까지 공유하겠습니다.'],['6:10','김 팀장','현장 안전 점검은 목요일 오전 10시로 잡았습니다.',1],['7:48','김 팀장','다음 통화는 같은 시간에 이어서 하겠습니다.']]},
 {name:'최 수석',at:'오전 11:20',len:'5분 3초',short:'5분',end:'5:03',fresh:false,seed:19,
  sum:[['화성 라인 설비 반입이 다음 달 10일로 일주일 밀렸습니다.',0],['견적 비교표는 내가 목요일까지 만들어 공유하기로 했습니다.',1],['안전 교육은 18일 오후 2시, 본관 대회의실입니다.',0]],
  missed:[{from:.55,to:.64,at:'2:48',why:'rain',q:'시운전에는 인력이 다섯 명 정도 필요합니다.'}],
  lines:[['0:04','최 수석','화성 라인 증설 건으로 전화드렸습니다.'],['0:20','최 수석','설비 반입이 다음 달 3일에서 10일로 일주일 밀렸습니다.'],['0:41','나','네, 반입 일정은 제가 협력사에 다시 확인해 볼게요.'],['1:30','최 수석','견적은 이번 주 금요일까지 두 군데에서 더 받아 주세요.'],['1:44','나','견적 비교표는 제가 목요일까지 만들어서 공유드리겠습니다.'],['2:48','최 수석','시운전에는 인력이 다섯 명 정도 필요합니다.',1],['3:50','최 수석','안전 교육은 18일 오후 2시에 본관 대회의실에서 진행합니다.'],['4:52','나','네, 수고하셨습니다.']]},
 {name:'박 대리',at:'오전 9:05',len:'1분 48초',short:'2분',end:'1:48',fresh:false,seed:31,
  sum:[['오후 3시 회의 자료가 공유 폴더에 올라와 있습니다.',0],['회의실이 7층 소회의실로 바뀌었습니다.',0],['참석 여부는 내가 점심 전에 답하기로 했습니다.',1]],
  missed:[],
  lines:[['0:03','박 대리','오후 3시 회의 자료를 공유 폴더에 올려 두었습니다.'],['0:21','박 대리','회의실은 7층 소회의실로 바뀌었습니다.'],['0:40','나','알겠습니다. 참석 여부는 점심 전에 답드릴게요.'],['1:35','박 대리','네, 그럼 오후에 뵙겠습니다.']]}
];
const UNSEEN={calls:2,callWho:['이 과장','어머니'],msgs:5,msgWho:'박 대리 외 2명'};
const STEPS=['운전대를 위로 올립니다','시트를 뒤로 밀고 조금 낮춥니다','방석 앞쪽을 낮추고 짧게 합니다','등받이를 세우고 허리 받침을 냅니다','조명, 가리개, 공조, 문 잠금, USB-C를 맞춥니다'];
const BACK=['조명, 가리개, 공조, USB-C를 되돌립니다','등받이와 허리 받침을 되돌립니다','방석을 되돌립니다','시트를 운전 위치로 옮깁니다','운전대를 내립니다'];
/* 통화 후 Gleo AI 대화 예시. who: gleo는 Gleo가 말함, me는 운전자가 말함 */
const GLEO=[
 {who:'gleo',t:'통화 요약을 듣고 싶으신가요?'},
 {who:'me',t:'네'},
 {who:'gleo',t:'김 팀장과의 통화입니다. 납기를 11월 28일로 옮기기로 했습니다.'},
 {who:'gleo',t:'변경 공문은 오늘 오후에 직접 보내기로 하셨습니다.'},
 {who:'gleo',tag:'못 들은 부분',t:'좌회전하시는 동안, 검수 인력이 두 명 더 필요하다는 이야기가 있었습니다.'},
 {who:'gleo',t:'궁금하신 사항이 있으신가요?'},
 {who:'me',t:'납기 언제라고 했지?'},
 {who:'gleo',t:'11월 28일입니다.'},
 {who:'me',t:'검수 인력은 언제부터 된대?'},
 {who:'gleo',t:'다음 주 월요일부터 가능하다고 했습니다.'},
 {who:'me',t:'됐어, 고마워'},
 {who:'gleo',t:'네. 통화 메모는 주차 후에 다시 보실 수 있습니다.'}
];
const SETTINGS=[['시트 위치','뒤로 12cm'],['시트 높이','2cm 낮춤'],['등받이','세움'],['독서등','밝기 70%'],['무드등','밝기 30%'],['송풍','1단']];

function ic(n,s=32,w=2.4){
 const p={
  down:'<polyline points="6 9 12 15 18 9"/>',up:'<polyline points="18 15 12 9 6 15"/>',
  check:'<polyline points="20 6 9 17 4 12"/>',minus:'<line x1="5" y1="12" x2="19" y2="12"/>',plus:'<line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>',
  turn:'<polyline points="9 14 4 9 9 4"/><path d="M20 20v-7a4 4 0 0 0-4-4H4"/>',
  wind:'<path d="M9.59 4.59A2 2 0 1 1 11 8H2m10.59 11.41A2 2 0 1 0 14 16H2m15.73-8.27A2.5 2.5 0 1 1 19.5 12H2"/>',
  rain:'<line x1="16" y1="13" x2="16" y2="21"/><line x1="8" y1="13" x2="8" y2="21"/><line x1="12" y1="15" x2="12" y2="23"/><path d="M20 16.58A5 5 0 0 0 18 7h-1.26A8 8 0 1 0 4 15.25"/>',
  laptop:'<rect x="4" y="5" width="16" height="11" rx="2"/><line x1="2" y1="19.5" x2="22" y2="19.5"/>',
  lock:'<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
  sun:'<circle cx="12" cy="12" r="4.500"/><line x1="12" y1="2" x2="12" y2="4"/><line x1="12" y1="20" x2="12" y2="22"/><line x1="2" y1="12" x2="4" y2="12"/><line x1="20" y1="12" x2="22" y2="12"/><line x1="4.900" y1="4.900" x2="6.300" y2="6.300"/><line x1="17.700" y1="17.700" x2="19.100" y2="19.100"/><line x1="4.900" y1="19.100" x2="6.300" y2="17.700"/><line x1="17.700" y1="6.300" x2="19.100" y2="4.900"/>',
  shade:'<rect x="4" y="3" width="16" height="18" rx="2"/><line x1="4" y1="8" x2="20" y2="8"/><line x1="4" y1="13" x2="20" y2="13"/>',
  zap:'<polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>',
  pause:'<line x1="8" y1="5" x2="8" y2="19"/><line x1="16" y1="5" x2="16" y2="19"/>',
  back:'<polyline points="1 4 1 10 7 10"/><path d="M3.510 15a9 9 0 1 0 2.130-9.360L1 10"/>',
  chat:'<path d="M21 11.500a8.380 8.380 0 0 1-.900 3.800 8.500 8.500 0 0 1-7.600 4.700 8.380 8.380 0 0 1-3.800-.900L3 21l1.900-5.700a8.380 8.380 0 0 1-.900-3.800 8.500 8.500 0 0 1 4.700-7.600 8.380 8.380 0 0 1 3.800-.900h.500a8.480 8.480 0 0 1 8 8v.500z"/>',
  note:'<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>',
  alert:'<path d="M10.290 3.860L1.820 18a2 2 0 0 0 1.710 3h16.940a2 2 0 0 0 1.710-3L13.710 3.860a2 2 0 0 0-3.420 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.010" y2="17"/>',
  phone:'<path d="M22 16.920v3a2 2 0 0 1-2.180 2 19.790 19.790 0 0 1-8.630-3.070 19.500 19.500 0 0 1-6-6 19.790 19.790 0 0 1-3.070-8.670A2 2 0 0 1 4.110 2h3a2 2 0 0 1 2 1.720 12.840 12.840 0 0 0 .700 2.810 2 2 0 0 1-.450 2.110L8.090 9.910a16 16 0 0 0 6 6l1.270-1.270a2 2 0 0 1 2.110-.450 12.840 12.840 0 0 0 2.810.700A2 2 0 0 1 22 16.920z"/>',
  off:'<path d="M18.360 6.640a9 9 0 1 1-12.730 0"/><line x1="12" y1="2" x2="12" y2="12"/>',
  mic:'<path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/>',
  mark:'<path d="M3 9c3-3 6-3 9 0s6 3 9 0"/><path d="M3 15c3-3 6-3 9 0s6 3 9 0"/>',
  windx:'<path pathLength="1" d="M9.59 4.59A2 2 0 1 1 11 8H2"/><path pathLength="1" d="M12.59 19.41A2 2 0 1 0 14 16H2"/><path pathLength="1" d="M17.73 7.73A2.5 2.5 0 1 1 19.5 12H2"/>'
 }[n];
 return `<svg class="${n==='windx'?'windx':''}" width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${p}</svg>`;
}
const gleo=s=>`<img class="gleo" src="img/gleo.png" width="${s}" height="${s}" alt="Gleo AI">`;
const mine=f=>f?'<em class="mine">내 약속</em>':'';
function bars(c,n){
 let s=c.seed*131+7,o='';const r=()=>(s=(s*9301+49297)%233280)/233280;
 for(let i=0;i<n;i++){const p=i/(n-1);const env=.3+.7*Math.abs(Math.sin(p*8.1+c.seed));const v=Math.max(.1,env*(.4+.6*r()));
  const m=c.missed.some(x=>p>=x.from&&p<=x.to);o+=`<i class="${m?'m':''}" style="height:${Math.round(v*100)}%;--i:${i}"></i>`;}
 return o;
}
function ribbon(c,n,plain){
 const bands=c.missed.map(m=>`<span class="band" style="left:${(m.from*100-.6).toFixed(2)}%;width:${((m.to-m.from)*100+1.2).toFixed(2)}%"></span>`).join('');
 const mk=c.missed.map(m=>`<span class="mk" style="left:${(m.from*100-.6).toFixed(2)}%">${ic(WHY[m.why].icon,26)}${WHY[m.why].label}</span>`).join('');
 return `<div class="ribbon" role="img" aria-label="통화 흐름. 못 들은 구간 ${c.missed.length}곳">${bands}<div class="bars">${bars(c,n)}</div>${plain?'':`<div class="marks"><span style="left:0">0:00</span>${mk}<span style="right:0">${c.end}</span></div>`}</div>`;
}
const linesHTML=c=>c.lines.map(l=>`<div class="line${l[1]==='나'?' me':''}${l[3]?' miss':''}"><time>${l[0]}</time><div><b>${l[1]}</b><span>${l[2]}</span></div></div>`).join('');

/* ───────── 주차 후 화면 ───────── */
function viewParked(){const c=calls[state.call];
 const list=calls.map((k,i)=>`<button class="P-call${i===state.call?' on':''}" data-call="${i}" aria-pressed="${i===state.call}">${avatar(k.name)}<span><b>${k.name}</b><small>${k.at} · ${k.short}</small></span>${k.fresh?'<span class="dot" title="새 메모"></span>':''}</button>`).join('');
 let miss;
 if(!c.missed.length) miss=`<div class="P-miss glass wide none">${ic('check',40)}못 들은 부분이 없습니다</div>`;
 else miss=c.missed.map(m=>`<div class="P-miss glass${c.missed.length===1?' wide':''}"><div class="why">${ic(WHY[m.why].icon,30)}못 들은 부분 · ${WHY[m.why].label}<small>${m.at}</small></div><q>${m.q}</q></div>`).join('');
 return `<div class="P"><div class="glow"></div>
  <div class="P-top"><div class="P-unseen glass"><span class="stack">${UNSEEN.callWho.map(avatar).join('')}</span><span><b>받지 않은 전화 ${UNSEEN.calls}건 · 메시지 ${UNSEEN.msgs}건</b><small>${UNSEEN.callWho.join(', ')} · ${UNSEEN.msgWho}</small></span></div><button class="btn pri P-office" data-go="office-on">${ic('laptop',36)}오피스 모드</button></div>
  <section class="P-main"><p class="P-who">${avatar(c.name)}<b>${c.name}</b>${c.at} · ${c.len}${c.fresh?'<span class="new">새 메모</span>':''}</p><ol class="P-sum">${c.sum.map((s,i)=>`<li style="--i:${i}">${mine(s[1])}${s[0]}</li>`).join('')}</ol></section>
  <nav class="P-calls glass" aria-label="받은 통화">${list}</nav>
  <div class="P-ribbon">${ribbon(c,170)}</div>
  <div class="P-row">${miss}<button class="P-full" data-act="toggle"><span><b>전체 내용</b><small>${c.lines.length}문장 · ${c.len}</small></span><span class="go">${ic('up',40)}</span></button></div>
  <div class="scrim" data-act="toggle"></div>
  <div class="sheet" role="dialog" aria-label="전체 내용"><header><b>전체 내용</b><small>${c.name} · ${c.at}</small><button data-act="toggle" aria-label="닫기">${ic('down',40)}</button></header><div class="scroll">${linesHTML(c)}</div></div>
 </div>`;
}
function viewEmpty(){
 return `<div class="P"><div class="glow"></div>
  <div class="P-top"><button class="btn pri P-office" data-go="office-on">${ic('laptop',36)}오피스 모드</button></div>
  <section class="P-empty"><div class="orb glass">${ic('phone',64,2)}</div><h1>오늘은 받은 통화가 없습니다</h1><p>운전 중에 전화를 받으면 차가 실내를 맞추고, 주차한 뒤 여기에 통화 메모를 보여 드립니다.</p></section>
  <div class="P-quiet">${ribbon({seed:3,missed:[],end:''},170,true)}</div>
 </div>`;
}

/* ───────── 오피스 모드 ───────── */
function arrow(dir,s=92){const r={up:0,down:180}[dir];return `<svg class="arrow" width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" style="rotate:${r}deg"><polyline points="6 11 12 5 18 11"/><polyline points="6 18 12 12 18 18" opacity=".5"/></svg>`;}
function workerArt(){
 // 위에서 본 모습: 운전석에 앉아 허벅지 위에 노트북을 올리고 작업하는 사람. 색 면 없이 윤곽선만 그림.
 // 좌표는 차량 그림(720 × 1200) 기준, 사람의 가운데는 x = 261. 오른쪽 팔다리는 왼쪽을 좌우로 뒤집어 씀.
 // 아래에 있는 것부터 그리고, 위에 있는 것이 아래 선을 가림(마스크에서 검정 = 지움, 흰색 = 선)
 const body=line=>{
  const F=line?'#000':'#fff';
  const both=x=>x+`<g transform="translate(522,0) scale(-1,1)">${x}</g>`;
  const thin=x=>line?`<g fill="none" stroke-width="1.400">${x}</g>`:'';
  const tube=(d,w,t)=>`<path d="${d}" fill="none" stroke-width="${w+t}"/>`+(line?`<path d="${d}" fill="none" stroke="#000" stroke-width="${w-t}"/>`:'');
  let keys='';
  for(let r=0;r<3;r++)for(let k=0;k<10;k++)keys+=`<rect x="${(220+k*8.4).toFixed(1)}" y="${(497+r*6.5).toFixed(1)}" width="6.200" height="4.600" rx="1"/>`;
  [[220,6.2],[228.4,6.2],[236.8,6.2],[245.2,31.4],[278.8,6.2],[287.2,6.2],[295.6,6.2]].forEach(k=>keys+=`<rect x="${k[0]}" y="516.500" width="${k[1]}" height="4.600" rx="1"/>`);
  const thigh='<path d="M256 604C250 590 242 560 236 520C234 505 232 494 224 487C216 481 204 483 199 493C194 503 196 530 201 560C205 582 211 598 218 610Z"/>'+thin('<path d="M203 500C208 493 220 492 227 499"/><path d="M214 506C218 540 226 574 236 600"/>');
  const arm='<path d="M203 622C196 610 196 592 203 580C208 572 214 566 221 558L236 540L246 548L232 566C226 574 223 584 223 596L223 606Z"/>'+thin('<path d="M231 546L242 554"/><path d="M206 584C211 587 216 586 219 581"/><path d="M209 606C211 600 211 594 209 588"/>');
  const fingers=tube('M237.500 526L236.200 517',4.800,1.4)+tube('M241 524.500L240.500 513.500',4.800,1.4)+tube('M245 524.500L245.500 512.500',4.800,1.4)+tube('M248.800 525.500L250.500 514.500',4.800,1.4)+tube('M250.500 533L255.500 526.500',5.200,1.4);
  const palm='<path d="M235 537C232 529 237 522.500 244 522.500C250.500 522.500 254 529 251.500 536.500C249 542 238 542 235 537Z"/>';
  return `<g fill="${F}" stroke="#fff" stroke-width="2.600" stroke-linejoin="round" stroke-linecap="round">
   ${both(thigh)}
   <rect x="211" y="488" width="100" height="58" rx="7"/>
   ${thin('<g stroke-width="1">'+keys+'</g><rect x="245" y="525.500" width="32" height="15" rx="3"/><path d="M245 536.500h32M261 536.500v4"/><rect x="238" y="489.800" width="46" height="3.600" rx="1.800"/>'+[0,1,2,3,4,5].map(i=>`<circle cx="215.300" cy="${499+i*4.2}" r=".550"/><circle cx="306.700" cy="${499+i*4.2}" r=".550"/>`).join('')+'<rect x="311" y="516.500" width="4.600" height="7" rx="1.200"/>')}
   <path d="M216 458h90l5 30H211z"/>
   ${thin('<path d="M221 462.500h80l3.500 21.500h-87z"/><circle cx="261" cy="460.300" r=".700"/><g stroke-width="1"><path d="M224.500 466h34M224 469.500h40M223.500 473h28M223 476.500h37M222.500 480h22"/><path d="M270 466.500h28.500l2 14h-32z"/><path d="M275 478v-4.500M280 478v-8M285 478v-6M290 478v-9.500M295 478v-3.500"/></g>')}
   ${both(arm)}${both(fingers)}${both(palm)}
   <path d="M200 628C199 614 208 605 222 602C236 599 248 598 261 598C274 598 286 599 300 602C314 605 323 614 322 628C321 640 310 646 296 649C285 651 273 652 261 652C249 652 237 651 226 649C212 646 201 640 200 628Z"/>
   ${thin('<path d="M240 630C246 641 276 641 282 630"/><path d="M240 624C228 619 214 617 204 621"/><path d="M282 624C294 619 308 617 318 621"/><path d="M216 641C238 647 284 647 306 641"/>')}
   <path d="M257 582C258 576.500 264 576.500 265 582" fill="none"/>
   <path d="M237.500 602C231 602 231 615 237.500 615M284.500 602C291 602 291 615 284.500 615" fill="none"/>
   <path d="M261 580C276 580 285 592 285 607C285 622 275 633 261 633C247 633 237 622 237 607C237 592 246 580 261 580Z"/>
   ${thin('<path d="M243 593C250 585 272 585 279 593"/><path d="M252 589C256 599 257 611 254 623"/><path d="M252 589C262 591 274 597 279 607"/><path d="M256 600C266 602 274 608 277 617"/><path d="M250 593C245 599 242 606 242 615"/>')}
  </g>`;};
 return `<svg class="ov envP worker" style="left:150px;top:430px" width="240" height="250" viewBox="150 430 240 250" aria-label="허벅지 위에 노트북을 올리고 작업하는 모습" role="img">
  <defs><mask id="wk-line" maskUnits="userSpaceOnUse" x="150" y="430" width="240" height="250">${body(true)}</mask>
  <mask id="wk-fill" maskUnits="userSpaceOnUse" x="150" y="430" width="240" height="250">${body(false)}</mask></defs>
  <path class="cable" d="M360 452C358 494 342 514 312 520" fill="none" stroke="#6FE7D2" stroke-width="3" stroke-linecap="round"/>
  <rect x="150" y="430" width="240" height="250" fill="rgba(6,10,30,.52)" mask="url(#wk-fill)"/>
  <rect class="ink" x="150" y="430" width="240" height="250" fill="#F6F8FF" mask="url(#wk-line)"/>
 </svg>`;
}
function stageHTML(mode){
 return `<div class="stage"><div class="car"><img src="img/car_top_hd.webp" alt="위에서 본 차량 실내">${workerArt()}
   <div class="ov hl1" style="left:200px;top:366px"><div class="ring" style="width:124px;height:124px"></div><div style="position:absolute;left:16px;top:-104px">${arrow('up')}</div></div>
   <div class="ov hl2" style="left:186px;top:490px"><div class="box" style="width:148px;height:176px"></div><div style="position:absolute;left:28px;top:186px">${arrow('down')}</div></div>
   <div class="ov hl3" style="left:192px;top:494px"><div class="box" style="width:136px;height:78px"></div></div>
   <div class="ov hl4" style="left:192px;top:584px"><div class="box" style="width:136px;height:78px"></div></div>
   <div class="ov envA light-work"></div>
   <div class="ov envA shade" style="left:176px;top:262px;width:368px;height:58px"></div>
   <div class="ov envB light-face"></div>
   <div class="ov envB mood" style="left:170px;top:476px;height:420px"></div><div class="ov envB mood" style="left:544px;top:476px;height:420px"></div>
   <div class="ov envB shade v" style="left:138px;top:470px;width:26px;height:430px"></div><div class="ov envB shade v" style="left:556px;top:470px;width:26px;height:430px"></div>
   <div class="ov envC lockb" style="left:82px;top:610px">${ic('lock',30)}</div><div class="ov envC lockb r" style="left:578px;top:610px">${ic('lock',30)}</div>
   <div class="ov envC usb"><i></i><i></i><span>${ic('zap',28)}USB-C${mode==='low'?' · 60W':' · 140W'}</span></div>
  </div></div>`;
}
function showFor(mode,step){
 if(mode==='on'||mode==='pause') return step>=5?'A C':'hl'+step;
 if(mode==='off') return step>=5?'A C':'hl'+step;
 if(mode==='meet') return 'B C P'; if(mode==='lock') return ''; return 'A C P';
}
function stepsHTML(list,cur,reverse){
 // 켤 때는 1→5, 되돌릴 때는 5→1 순서로 진행
 return `<ol class="O-steps glass">${list.map((t,i)=>{const n=reverse?5-i:i+1;const done=reverse?n>cur:n<cur;const now=n===cur;
  return `<li data-n="${n}" class="${now?'now':''}${done?' done':''}"><span class="n"><span>${i+1}</span>${ic('check',30,3)}</span>${t}</li>`}).join('')}</ol>`;
}
function powerHTML(low){
 return low?`<div class="O-power glass low"><div class="top">${ic('zap',34)}노트북 충전 중<span class="watt">60W</span></div><div class="batt">차 배터리 18%<span class="meter"><i style="width:18%"></i></span></div><p class="msg">배터리가 적어 출력을 60W로 낮췄습니다. 15% 아래로 내려가면 알려 드린 뒤 충전을 끕니다.</p></div>`
  :`<div class="O-power glass"><div class="top">${ic('zap',34)}노트북 충전 중<span class="watt">140W</span></div><div class="batt">차 배터리 72%<span class="meter"><i style="width:72%"></i></span></div></div>`;
}
function rowsHTML(meet){
 const r=meet?[['sun','조명','얼굴 조명 · 무드등 흰색'],['shade','가리개','옆 창을 가려 바깥을 막음'],['wind','송풍','더 조용하게 · 창문 닫음'],['lock','문','잠금']]
  :[['sun','조명','키보드 쪽 독서등'],['shade','가리개','앞유리 눈부심을 가림'],['wind','송풍','조용하게 1단 · 시트 통풍'],['lock','문','잠금']];
 return `<ul class="O-rows glass">${r.map(x=>`<li>${ic(x[0],34)}<b>${x[1]}</b><span>${x[2]}</span></li>`).join('')}</ul>`;
}
function viewOffice(mode){
 const step=state.step;let side='';
 if(mode==='on') side=`<h1>오피스 모드를 켜고 있습니다</h1><p class="sub">화면을 누르거나 “멈춰”라고 말하면 바로 멈춥니다</p>${stepsHTML(STEPS,step,false)}<div class="O-actions"><button class="btn solid" data-go="office-pause">${ic('pause',38,3)}멈춤</button></div>`;
 else if(mode==='off') side=`<h1>원래대로 되돌리고 있습니다</h1><p class="sub">시트를 먼저 옮기고 운전대를 마지막에 내립니다</p>${stepsHTML(BACK,step,true)}<div class="O-actions"><button class="btn solid" data-go="office-pause">${ic('pause',38,3)}멈춤</button></div>`;
 else if(mode==='pause') side=`<h1>멈췄습니다</h1><p class="sub">시트와 운전대가 지금 위치에 멈춰 있습니다</p>${stepsHTML(STEPS,step,false)}<div class="O-actions"><button class="btn soft" data-go="office-off">${ic('back',34)}원래대로</button><button class="btn pri" data-go="office-on" data-keep="1">이어서 켜기</button></div>`;
 else if(mode==='lock') side=`<h1>오피스 모드</h1><p class="sub">시트와 조명을 노트북 작업에 맞춥니다</p><div class="O-lock glass">${ic('lock',40)}<p>차가 움직이는 동안에는 켤 수 없습니다. 주차(P)하면 켤 수 있습니다.</p></div><div class="O-actions"><button class="btn pri" disabled>${ic('lock',34)}주차하면 켤 수 있습니다</button></div>`;
 else{const meet=mode==='meet';
  side=`<h1>오피스 모드</h1><p class="sub">출발하기 전에 원래대로 되돌려 주세요</p>
   <div class="O-seg glass" role="group" aria-label="작업과 회의"><button data-go="office-work" data-kind="swap" aria-pressed="${!meet}">작업</button><button data-go="office-meet" data-kind="swap" aria-pressed="${meet}">회의</button></div>
   ${rowsHTML(meet)}${powerHTML(mode==='low')}
   <div class="O-actions"><button class="btn soft" data-act="toggle">설정 바꾸기</button><button class="btn solid" data-go="office-off">${ic('back',34)}원래대로</button></div>`;}
 const sheet=(mode==='work'||mode==='meet'||mode==='low'||mode==='set')?`<div class="scrim" data-act="toggle"></div><div class="sheet" style="height:940px" role="dialog" aria-label="설정 바꾸기"><header><b>설정 바꾸기</b><small>내 오피스 모드</small><button data-act="toggle" aria-label="닫기">${ic('down',40)}</button></header><div class="scroll">${SETTINGS.map(s=>`<div class="S-row"><b>${s[0]}</b><button aria-label="${s[0]} 줄이기">${ic('minus',36,3)}</button><span class="val">${s[1]}</span><button aria-label="${s[0]} 늘리기">${ic('plus',36,3)}</button></div>`).join('')}<p class="S-note">바꾼 값은 다음에 오피스 모드를 켤 때도 그대로 씁니다.</p></div></div>`:'';
 return `<div class="O${(mode==='work'||mode==='meet'||mode==='low')?' zoom':''}" data-m="${mode}" data-show="${showFor(mode,step)}"><div class="glow"></div>${stageHTML(mode)}<div class="O-side">${side}</div>${sheet}</div>`;
}

/* ───────── 주행 중 작은 창 ───────── */
const DRIVE={
 idle:{orb:ic('mark',84,2),line:'통화를 기다리는 중'},
 adjust:{orb:ic('windx',96,2),line:'통화가 잘 들리도록 조절 중'},
 miss:{orb:ic('note',80,2),line:'못 들은 부분을 표시하는 중',cls:'miss'},
 ready:{raw:gleo(176),line:'Gleo가 요약을 준비했습니다'},
 speak:{orb:`<span class="voice">${[.55,.9,.7,1,.6].map((h,i)=>`<i style="--i:${i};--h:${h}"></i>`).join('')}</span>`,line:'Gleo가 말하는 중'},
 listen:{orb:`<span class="halo"></span>${ic('chat',80,2)}`,line:'듣고 있습니다',cls:'listen'},
 refuse:{orb:ic('alert',80,2),line:'차량이 창문 조작을 받아들이지 않았습니다',cls:'warn'},
 off:{orb:ic('off',80,2),line:'자동 조절이 꺼져 있습니다',btn:'자동 조절 켜기'}
};
function navArt(){return `<svg viewBox="0 0 1121 1210" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
 <g fill="none" stroke="var(--road)" stroke-linecap="round"><path d="M-40 980 C300 860 420 700 520 520 S760 160 1180 60" stroke-width="64"/><path d="M120 -40 L420 1260" stroke-width="40"/><path d="M-40 420 L1180 700" stroke-width="34"/><path d="M700 -40 L940 1260" stroke-width="26"/><path d="M-40 160 L1180 300" stroke-width="20"/></g>
 <path d="M-40 980 C300 860 420 700 520 520 S760 160 1180 60" fill="none" stroke="var(--route)" stroke-width="18" stroke-linecap="round"/>
 <circle cx="352" cy="826" r="30" fill="var(--route)" stroke="#fff" stroke-width="8"/></svg><span class="tag">내비게이션 (다른 앱)</span>`;}
function viewTalk(){
 // Gleo와 운전자가 주고받는 말을 대화처럼 쌓아 보여 줌. 지금 말은 크게, 지난 말은 작고 흐리게 위로 밀려남
 const cur=Math.min(state.g,GLEO.length-1);
 const first=Math.max(0,cur-2); // 지난 말은 두 개까지만 남김
 const turns=GLEO.slice(first,cur+1).map((m,k)=>{const i=first+k,old=i<cur,waiting=!old&&!state.h;
  if(m.who==='me')return `<div class="turn me${old?' old':''}"><span class="pill glass${waiting?' waiting':''}">${old?'':`<span class="G-mic"><span class="halo"></span>${ic('mic',30,2.2)}</span>`}${waiting?'듣고 있습니다':m.t}</span></div>`;
  const words=old?m.t:m.t.split(' ').map((w,k)=>`<span style="--i:${k}">${w}</span>`).join(' ');
  return `<div class="turn g${old?' old':''}"><div class="who">${gleo(old?36:56)}<b>Gleo</b>${m.tag?`<em>${m.tag}</em>`:''}${old?'':DRIVE.speak.orb}</div><p>${words}</p></div>`;}).join('');
 return `<div class="D" data-w="${state.w}"><div class="D-nav">${navArt()}</div>
  <div class="D-win"><div class="glow"></div><div class="D-body talk"><div class="T" aria-live="polite"><div class="T-in">${turns}</div></div>
   <button class="btn soft D-btn" data-go="drive-off">자동 조절 끄기</button></div></div></div>`;
}
function viewReady(){
 // 통화 종료 후: Gleo가 화면과 음성으로 묻고, 운전자의 "네"를 기다림
 const words=GLEO[0].t.split(' ').map((w,i)=>`<span style="--i:${i}">${w}</span>`).join(' ');
 return `<div class="D" data-w="${state.w}"><div class="D-nav">${navArt()}</div>
  <div class="D-win"><div class="glow"></div><div class="D-body ready"><div class="D-raw">${gleo(168)}</div><p class="D-line">Gleo가 요약을 준비했습니다</p>
   <div class="D-ask" aria-live="polite">${DRIVE.speak.orb}<p>${words}</p></div><small class="D-hint">${ic('mic',26,2.2)}“네”라고 말하면 들려 드립니다</small>
   <button class="btn soft D-btn" data-go="drive-off">자동 조절 끄기</button></div></div></div>`;
}
function viewDrive(k){if(k==='ready')return viewReady();if(k==='speak'||k==='listen')return viewTalk();const d=DRIVE[k];
 return `<div class="D" data-w="${state.w}"><div class="D-nav">${navArt()}</div>
  <div class="D-win"><div class="glow"></div><div class="D-body">${d.raw?`<div class="D-raw">${d.raw}</div>`:`<div class="D-orb glass ${d.cls||''}">${d.orb}</div>`}<p class="D-line">${d.line}</p><button class="btn soft D-btn" data-go="${k==='off'?'drive-idle':'drive-off'}">${d.btn||'자동 조절 끄기'}</button></div></div></div>`;
}

/* ───────── 첫 실행 동의, 상태 카드 ───────── */
function viewConsent(){
 // 송풍 6단 → 2단으로 낮추는 모습: 3~6번째 칸이 꺼짐
 const fan=`<div class="fan"><b>송풍<span>2단</span></b><div>${[0,1,2,3,4,5,6,7].map(i=>`<i class="${i<2?'on':i<6?'drop':''}" style="--i:${5-i}"></i>`).join('')}</div></div><span class="win">${ic('check',26,3)}창문 닫힘</span>`;
 const steps=[
  ['통화 중','vis col',fan,'실내를 조용하게','송풍을 낮추고 창문을 닫아 통화가 잘 들리게 합니다.'],
  ['통화 직후','vis',gleo(88)+DRIVE.speak.orb,'Gleo가 짚어 줍니다','운전하느라 놓친 부분을 Gleo가 말로 짚어 드립니다.'],
  ['주차 후','vis',ribbon(calls[0],56,true),'통화 메모를 한눈에','핵심 요약과 못 들은 부분을 통화별로 보여 드립니다.'],
  ['일해야 할 때','vis',`<span class="lap">${ic('laptop',72,1.8)}</span><span class="win mid">${ic('zap',24)}노트북 충전</span>`,'오피스 모드','버튼 하나로 차를 노트북 작업 공간으로 바꿉니다.']
 ];
 return `<div class="C"><div class="glow"></div>
  <div class="stage"><img src="img/car_hero.jpg" alt=""><div class="beam"></div>
   <div class="C-title"><small>당신을 위한 업무 비서</small><h1>차분을 시작합니다</h1></div></div>
  <div class="C-steps">${steps.map((s,i)=>`<div class="C-step"><div class="ph" style="--i:${i}"><i></i><span>${s[0]}</span></div><section class="C-card glass" style="--i:${i}"><div class="${s[1]}">${s[2]}</div><h2>${s[3]}</h2><p>${s[4]}</p></section></div>`).join('')}</div>
  <div class="C-acts"><button class="btn soft" data-go="parked-empty">나중에</button><button class="btn pri" data-go="parked">사용하기</button></div></div>`;
}
function viewCard(){
 const c=(o,cls,a,b)=>`<div class="card">${cls==='raw'?o:`<span class="orb glass ${cls}">${o}</span>`}<span><b>${a}</b><small>${b}</small></span></div>`;
 return `<div class="W"><div class="glow"></div><p>상태 카드 760 × 170 · 상황별 예시</p>
  ${c(ic('windx',52,2.2),'','통화가 잘 들리도록 조절 중','송풍 2단 · 창문 닫힘')}
  ${c(ic('note',50,2),'miss','못 들은 부분을 표시하는 중','통화가 끝나면 Gleo가 짚어 드립니다')}
  ${c(gleo(100),'raw','Gleo가 요약을 준비했습니다','통화 요약을 듣고 싶으신가요?')}
  ${c(ic('laptop',52,2),'','오피스 모드를 켰습니다','노트북 충전 중 · 140W')}</div>`;
}

/* ───────── 그리기와 동작 ───────── */
const app=document.getElementById('app'),frame=document.getElementById('frame'),scaler=document.getElementById('scaler'),viewport=document.getElementById('viewport');
let timer=null;
function view(){const s=state.s;
 if(s==='parked')return viewParked(); if(s==='parked-empty')return viewEmpty();
 if(s.startsWith('office-'))return viewOffice(s.slice(7)); if(s.startsWith('drive-'))return viewDrive(s.slice(6));
 if(s==='consent')return viewConsent(); return viewCard();
}
function render(kind){
 app.dataset.t=state.t;
 app.className='app'+(state.open?' open':'')+(state.zone?' showzone':'')+(state.bare?' still':'');
 app.innerHTML=view()+'<div class="zone">비워 둔 구역 480 × 200</div>';
 if(kind&&!state.bare){void app.offsetWidth;app.classList.add(kind);if(kind!=='say')setTimeout(()=>app.classList.remove(kind),1700);}
 document.querySelectorAll('.side button').forEach(b=>b.setAttribute('aria-current',b.dataset.s===state.s));
 document.querySelectorAll('#segT button').forEach(b=>b.setAttribute('aria-pressed',b.dataset.v===state.t));
 const drive=state.s.startsWith('drive-');
 const talk=state.s==='drive-ready'||state.s==='drive-speak'||state.s==='drive-listen';
 document.getElementById('note').innerHTML=(drive?DRIVE_NOTE+(talk?GLEO_NOTE:''):NOTES[state.s]||'')+(drive?` <span class="seg"><button data-w="3" aria-pressed="${state.w==='3'}">1/3 폭</button><button data-w="2" aria-pressed="${state.w==='2'}">1/2 폭</button></span><button class="tool" data-tool="park">주차하면 넓어지는 모습 보기</button>`:'')+((state.s==='office-on'||state.s==='office-off'||talk)?' <button class="tool" data-tool="replay">처음부터 다시 보기</button>':'');
 document.getElementById('ctx').checked=state.ctx;document.getElementById('zone').checked=state.zone;
 frame.classList.toggle('ctx',state.ctx);
 frame.style.backgroundImage=state.ctx?`url(img/frame_${state.t}.png)`:'';
 fit();
}
function fit(){
 const fw=state.ctx?2560:W,fh=state.ctx?1440:H;
 const k=state.bare?1:Math.min((viewport.clientWidth-40)/fw,(viewport.clientHeight-34)/fh);
 frame.style.transform=`scale(${k})`;scaler.style.width=fw*k+'px';scaler.style.height=fh*k+'px';
}
function markSteps(){
 const o=app.querySelector('.O');if(!o)return;const m=o.dataset.m,rev=m==='off';
 o.dataset.show=showFor(m,state.step);
 o.querySelectorAll('.O-steps li').forEach(li=>{const n=+li.dataset.n;li.classList.toggle('now',n===state.step);li.classList.toggle('done',rev?n>state.step:n<state.step);});
}
function runSeq(){
 clearInterval(timer);clearTimeout(timer);if(state.bare)return;
 if(state.s==='office-on')timer=setInterval(()=>{if(state.step<5){state.step++;markSteps();}else{clearInterval(timer);go('office-work','enter');}},2300);
 if(state.s==='drive-ready'||state.s==='drive-speak'||state.s==='drive-listen'){const next=()=>{const m=GLEO[state.g];
   // 운전자 차례: 먼저 "듣고 있습니다"를 보여 주고, 알아들은 말로 바꿈
   if(m.who==='me'&&!state.h){timer=setTimeout(()=>{state.h=1;render('heard');next();},1500);return;}
   timer=setTimeout(()=>{if(state.g<GLEO.length-1){state.g++;state.h=0;state.s=GLEO[state.g].who==='me'?'drive-listen':'drive-speak';render('say');next();}},m.who==='me'?1200:m.t.split(' ').length*300+(state.g===0?2600:1300));};next();}
 if(state.s==='office-off')timer=setInterval(()=>{if(state.step>1){state.step--;markSteps();}else{clearInterval(timer);go('parked','enter');}},2100);
}
function go(s,kind,keep){
 if(s==='drive-ready'||s==='drive-speak'||s==='drive-listen')kind='say';
 if(s==='office-set'){state.s='office-work';state.open=true;render(kind);clearInterval(timer);return;}
 if(!keep){if(s==='office-on')state.step=1;if(s==='office-off')state.step=5;if(s==='drive-ready')state.g=0;if(s==='drive-speak')state.g=2;if(s==='drive-listen')state.g=6;state.h=0;}
 if(s==='office-pause'&&state.s==='office-off'){/* 되돌리다 멈춤: 단계 유지 */}
 state.s=s;state.open=false;render(kind);runSeq();
}
app.addEventListener('click',e=>{
 const c=e.target.closest('[data-call]');if(c){const i=+c.dataset.call;if(i!==state.call){state.call=i;state.open=false;render('swap');}return;}
 const g=e.target.closest('[data-go]');if(g&&!g.disabled){go(g.dataset.go,g.dataset.kind||'enter',g.dataset.keep);return;}
 const a=e.target.closest('[data-act]');if(a&&a.dataset.act==='toggle'){state.open=!state.open;app.classList.toggle('open',state.open);}
});
document.getElementById('nav').innerHTML='<h1>차분 · 밤공기</h1>'+SCREENS.map(g=>`<h2>${g[0]}</h2>`+g[1].map(x=>`<button data-s="${x[0]}">${x[1]}</button>`).join('')).join('');
document.getElementById('nav').addEventListener('click',e=>{const b=e.target.closest('button');if(b)go(b.dataset.s,'enter');});
document.getElementById('segT').addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;state.t=b.dataset.v;render();});
document.getElementById('ctx').addEventListener('change',e=>{state.ctx=e.target.checked;render();});
document.getElementById('zone').addEventListener('change',e=>{state.zone=e.target.checked;render();});
document.getElementById('note').addEventListener('click',e=>{
 const w=e.target.closest('[data-w]');if(w){state.w=w.dataset.w;render();return;}
 const t=e.target.closest('[data-tool]');if(!t)return;
 if(t.dataset.tool==='replay')go(state.s,'enter');
 if(t.dataset.tool==='park'){const d=app.querySelector('.D');if(d){d.classList.add('parking');setTimeout(()=>go('parked','enter'),950);}}
});
addEventListener('resize',fit);
addEventListener('keydown',e=>{if(e.target.tagName==='INPUT')return;const k=e.key.toLowerCase();if(k==='n'){state.t='night';render();}if(k==='d'){state.t='day';render();}});
if(state.bare)document.body.classList.add('bare');
if(state.s==='office-set'){state.s='office-work';state.open=true;}
render(state.bare?null:(state.s==='drive-ready'||state.s==='drive-speak'||state.s==='drive-listen')?'say':'enter');runSeq();
