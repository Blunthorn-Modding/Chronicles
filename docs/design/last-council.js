'use strict';
// Deliberately standalone. All state is fictitious and in memory; no backend or mod API.
const $ = q => document.querySelector(q);
const houses = ['House Ash', 'House Rowan', 'House Vale', 'House Reed'];
const otherSeats = ['House Elm', 'House Flint', 'House Mere', 'House Thorn'];
const sections = {
  Player: ['Council', 'Character', 'Treaties', 'Conflicts', 'Chronicle'],
  Chancellor: ['Council', 'Houses', 'Treaties', 'Conflicts', 'Chronicle'],
  Editor: ['Session', 'Players', 'Events', 'Chests', 'Loot Tables', 'Books & Lore', 'Content pools', 'Regions', 'Locations', 'Player Roles', 'Encounter groups', 'Flags', 'Game setup']
};
let view = 'Player', page = 'Council', selected = {}, debug = false, editing = true, modalOpener;
let state;
const sample = () => ({meeting:'Discussion', vote:null, succession:false, candidateCount:4, motion:'Open the winter granaries', choice:null,
  rebel:false, sponsorship:false, treaty:'Offered', conflict:false, committed:false, call:false, meetingOpen:true,
  pool:'House secrets', eventPool:'Council complications', poolItems:['An unpaid debt', 'A hidden claimant', 'A stolen charter'],
  weight:1, count:3, repeat:'No repeated entry', eveningCount:3, meetings:['First Council', 'Second Council', 'Final Council'],meetingNumber:1,
  votesClosed:false, history:[], returnedFromRecess:false, publicVote:false, incomplete:false, lastResult:null,
  castPreview:false, characterName:'Elian Ash', role:'Navigator', poolTab:'Entries',bookCount:2,recessReason:'',complicationDelivered:false,
  electorate:8,policyResult:null,regencyResult:null,electionResolved:false,
  treaties:[{name:'Northern road pact',terms:'Neither signatory will declare war on the other while the northern road is being repaired.',parties:['House Ash','House Rowan'],visibility:'Private',scope:'Named houses',status:'Offered',signed:['House Rowan']},
    {name:'Northern trade accord',terms:'Open the river markets to all royal merchants.',parties:['House Rowan','House Vale'],visibility:'Public',scope:'Realm-wide (Council ratification)',status:'Awaiting ratification',signed:['House Rowan','House Vale']}],
  pools:{'House secrets':{event:false,items:['An unpaid debt','A hidden claimant','A stolen charter'],count:2},'House information':{event:false,items:['The northern road','The river merchants'],count:1},'Character goals':{event:false,items:['Open the granaries','Protect the river charter'],count:2},'Cross-house knowledge':{event:false,items:['Knowledge of a debt','Knowledge of a claimant'],count:1},'Council complications':{event:true,items:['The northern crossing falls','A disputed royal debt','An unexpected envoy']}},poolWeights:{}});
state = sample();
function el(tag, value, cls=''){const e=document.createElement(tag);if(value!==undefined)e.textContent=value;e.className=cls;return e;}
function btn(label, fn, disabled=false, cls=''){const e=el('button',label,cls);e.type='button';e.disabled=disabled;e.onclick=()=>fn();return e;}
function actions(...buttons){const e=el('div',undefined,'actions');e.append(...buttons);return e;}
function para(t, cls=''){return el('p',t,cls+(t.includes('\n')?' record-text':''));}
function card(title, ...nodes){const e=el('section',undefined,'card');e.append(el('h3',title),...nodes);return e;}
function field(label,value,type='text'){const l=el('label',label),i=el(type==='textarea'?'textarea':'input');if(type!=='textarea')i.type=type;i.value=value;l.append(i);return l;}
function select(label, options, value, fn){const l=el('label',label),s=el('select');options.forEach(v=>{let o=el('option',v);o.value=v;o.selected=v===value;s.append(o);});if(fn)s.onchange=()=>fn(s.value);l.append(s);return l;}
function check(label,checked=false){const l=el('label',undefined,'check'),i=el('input');i.type='checkbox';i.checked=checked;l.append(i,document.createTextNode(label));return l;}
function note(t){$('#notice').textContent=t;}
function navigate(p){page=p;document.body.dataset.pane='list';render();}
function chooseItem(name){selected[view+page]=name;document.body.dataset.pane='detail';render();}
function getSelection(fallback){return selected[view+page]||fallback;}
function heading(t, description){$('#detail').append(el('h2',t));if(description)$('#detail').append(para(description));}
function listItems(names, fallback, create){const l=$('#list');l.append(el('h3',page));if(create)l.append(btn(create.label,create.fn,!!create.disabled,'primary'));for(const name of names)l.append(btn(name,()=>chooseItem(name),false,getSelection(fallback)===name?'selected':''));}
function showModal(title,build,wide=false){
  if(!$('#overlay').firstChild)modalOpener=document.activeElement;
  $('#app').inert=true;$('.review-tools').inert=true;$('#overlay').replaceChildren();
  const shade=el('div',undefined,'shade'),box=el('section',undefined,'modal'+(wide?' wide':''));box.role='dialog';box.setAttribute('aria-modal','true');
  const h=el('h2',title);h.id='modal-title';box.setAttribute('aria-labelledby',h.id);box.append(h);shade.append(box);$('#overlay').append(shade);build(box);
  const first=box.querySelector('input,textarea,select,button:not(:disabled)');if(first)first.focus();
}
function closeModal(){const opener=modalOpener;$('#overlay').replaceChildren();$('#app').inert=false;$('.review-tools').inert=false;render();if(opener?.isConnected)opener.focus();else $('#debug').focus();}
function confirm(title,body,label,fn,danger=false){showModal(title,m=>m.append(para(body),actions(btn('Cancel',closeModal),btn(label,()=>{fn();closeModal();},false,danger?'danger':'primary'))));}
function picker(title,options,fn,onCancel=closeModal){showModal(title,m=>{const search=field('Search',''),results=el('div',undefined,'option-list');function draw(){results.replaceChildren();let matches=options.filter(x=>x.toLowerCase().includes(search.lastChild.value.toLowerCase()));if(!matches.length)results.append(para('No matches.','empty'));matches.forEach(x=>results.append(btn(x,()=>fn(x))));}search.lastChild.oninput=draw;m.append(search,results,actions(btn('Cancel',onCancel)));draw();});}
function disabledReason(text){$('#detail').append(para(text,'warning'));}
function debugInfo(){if(debug)$('#detail').append(para('Debug · Sample revision 4 · Last request acknowledged. Diagnostics never reveal secret choices, future complications or who knows your secrets.','debug'));}
function render(){
  const navOffset=$('.advanced-nav')?.scrollTop||0;
  $('#title').textContent=view==='Editor'?'Chronicle':view==='Chancellor'?'The Last Council · Chancellor':'The Last Council';
  $('#subtitle').textContent=view==='Editor'?'Authoring · Last Council':state.meeting==='Recess'?'Council in recess · roleplay continues':state.meeting==='Recovery'?'Council held · operation needs attention':'Council · '+state.meeting;
  $('#debug').textContent='Debug: '+(debug?'On':'Off');$('#debug').hidden=view==='Player';$('#editing').hidden=view!=='Editor';$('#editing').textContent='Editing: '+(editing?'On':'Off');
  $('#nav').replaceChildren();$('#list').replaceChildren();$('#detail').replaceChildren();
  const backList=btn('‹ Sections',()=>document.body.dataset.pane='nav',false,'compact-back'),backDetail=btn('‹ '+page,()=>document.body.dataset.pane='list',false,'compact-back');$('#list').append(backList);$('#detail').append(backDetail);
  $('#nav').classList.toggle('editor-nav',view==='Editor');let navTarget=$('#nav');
  for(const name of sections[view]){
    if(view==='Editor'&&name==='Chests'){navTarget=el('div',undefined,'advanced-nav');$('#nav').append(navTarget);navTarget.append(el('div','ADVANCED','section-label'));}
    navTarget.append(btn(name,()=>navigate(name),false,page===name?'selected':''));
  }
  const advanced=$('.advanced-nav');if(advanced){advanced.scrollTop=navOffset;const current=advanced.querySelector('.selected');if(current&&current.offsetTop-advanced.offsetTop+current.offsetHeight>advanced.scrollTop+advanced.clientHeight)advanced.scrollTop=current.offsetTop-advanced.offsetTop-advanced.clientHeight+current.offsetHeight+8;}
  if(page==='Council')council();else if(page==='Character')character();else if(page==='Houses')roster();else if(page==='Treaties')treaties();else if(page==='Conflicts')conflicts();else if(page==='Chronicle')chronicle();else editor();if(view!=='Player')debugInfo();
}
function council(){
  const gm=view==='Chancellor';listItems(['This meeting','Open the winter granaries','Regency','Proposals'], 'This meeting', {label:gm?'+ Prepare motion':'+ Submit motion',fn:motionForm,disabled:!gm&&state.rebel});
  const item=getSelection('This meeting');
  if(state.incomplete){heading('Council needs setup',gm?'Assign representatives and a palace Region before opening a meeting.':'The Chancellor needs to finish Council setup. You can still edit your character card.');if(gm)$('#detail').append(actions(btn('Review setup',()=>{view='Editor';$('#context').value=view;navigate('Game setup');})));return;}
  if(item==='Regency'){regency();return;}if(item==='Proposals'){proposals();return;}if(item==='Open the winter granaries'){motion();return;}
  heading(state.meetings[state.meetingNumber-1]||'Council '+state.meetingNumber,'Discuss the kingdom’s future. The Chancellor opens each vote when the room is ready.');
  if(state.meeting==='Recovery'){recovery();return;}
  if(state.meeting==='Recess'){
    $('#detail').append(card('Proceedings in recess',para(state.recessReason||'The Chancellor has called a recess.'),para('Talk, move and negotiate as usual. Voting resumes when the Chancellor recalls the Council.','muted')));
    if(gm)$('#detail').append(actions(btn('Resume proceedings',()=>{state.meeting='Discussion';state.returnedFromRecess=true;note('Proceedings resumed. No ballot has opened.');render();},false,'primary')));return;
  }
  $('#detail').append(card('On the floor',para(state.vote?(state.vote==='secret'?'Regency · secret ballot open':'Open the winter granaries · public ballot open'):'Open the winter granaries · discussion'),actions(btn(state.vote?'View ballot':'View motion',()=>chooseItem(state.vote==='secret'?'Regency':'Open the winter granaries')))));
  if(!gm){$('#detail').append(card('Your house',para(state.rebel?'House Ash · in rebellion':'House Ash · Council seat'),para(state.rebel?'You can follow proceedings and negotiate, but cannot vote or sponsor motions.':'You have one vote. Your candidacy is separate from your Council seat.'),actions(btn('House standing…',houseStanding))));}
  else{
    $('#detail').append(actions(btn('Call Council…',callCouncil),btn(state.meetingOpen?'Recess…':'Open meeting',()=>{if(!state.meetingOpen){state.meetingOpen=true;state.meeting='Discussion';state.succession=false;state.returnedFromRecess=false;render();}else confirm('Call a recess?','Players continue roleplay. This suspends Council proceedings, not the world.','Recess',()=>{state.meeting='Recess';state.recessReason='';});},!!state.vote)));
    $('#detail').append(actions(btn('Open ordinary vote…',()=>openBallot('public'),!!state.vote||!state.meetingOpen),btn('Request regency vote…',requestRegency,!!state.vote||!state.meetingOpen||state.succession||state.electionResolved)));
    if(state.vote)$('#detail').append(actions(btn('Close current ballot…',closeBallot,false,'primary')));
    if(!state.meetingOpen)disabledReason('Open the meeting before opening a ballot.');
    $('#detail').append(actions(btn('Close meeting…',closeMeeting,!!state.vote||!state.meetingOpen)));
    if(state.succession)$('#detail').append(para('This meeting has used its regency ballot. The next one belongs to a later Council meeting.','muted'));
  }
}
function callCouncil(){confirm('Call the Council?','Play the configured Council call Event. This gathers people; it does not open a meeting or vote.','Sound the call',()=>{state.call=true;note('Sample: the Council call Event played once. No real audio is bundled in this prototype.');});}
function dueComplication(){if($('#example').value!=='recess'||state.complicationDelivered||state.eveningCount===0)return false;state.meeting='Recess';state.complicationDelivered=true;state.recessReason='A messenger has arrived. The northern crossing has fallen; grain wagons cannot reach the capital.';state.history.push('The northern crossing has fallen.');selected[view+'Council']='This meeting';note('Example: due Event delivered. Roleplay continues; resume proceedings before trying again.');render();return true;}
function closeMeeting(){if(dueComplication())return;confirm('Close this Council meeting?','No ballot is open. Future Council meetings and ordinary roleplay remain available.','Close meeting',()=>{state.meetingOpen=false;state.meeting='Between meetings';state.meetingNumber++;state.succession=false;note('Meeting closed. Core play remains active.');});}
function motionForm(){if(state.rebel&&view==='Player'){note('A rebel house cannot submit Council motions.');return;}showModal(view==='Chancellor'?'Prepare a motion':'Submit a motion',m=>{
  const title=field('Title',''),terms=field('Proposal','', 'textarea'),kind=select('Decision',['Policy / decree','Advisory resolution','Foreign war','Foreign peace','Reinstatement','Realm treaty ratification'],'Policy / decree'),visibility=select('Ballot',['Public','Secret'],'Public'),target=el('div'),message=para('','warning');
  function targets(){target.replaceChildren();const k=kind.lastChild.value;if(['Foreign war','Foreign peace'].includes(k))target.append(select('Foreign realm',['The Northern March','The River League'],'The Northern March'));if(k==='Reinstatement')target.append(select('House to reinstate',['House Ash','House Rowan'],'House Ash'));if(k==='Realm treaty ratification')target.append(select('Treaty',['Northern trade accord'],'Northern trade accord'));if(k==='Advisory resolution')target.append(field('Named alternatives (one per line)','Open the northern road\nRepair the eastern road','textarea'));}kind.lastChild.onchange=targets;
  m.append(title,terms,kind,target,visibility,para('Policy text records a political decision. Foreign war and reinstatement use their named actions; they need two-thirds support.','muted'),message,actions(btn('Cancel',closeModal),btn(view==='Chancellor'?'Save motion':'Submit',()=>{if(!title.lastChild.value.trim()||!terms.lastChild.value.trim()){message.textContent='Add a title and proposal.';return;}note('Sample motion saved. Player proposals need another house’s sponsorship before the Chancellor adds them to the agenda.');closeModal();},false,'primary')));targets();
});}
function proposals(){heading('Proposals','Player proposals need support from another house before the Chancellor puts them on the agenda.');$('#detail').append(card('Repair the northern bridge',para('Proposed by House Rowan. Merchants will fund half the work.'),para(state.sponsorship?'Sponsored by House Ash':'Needs one sponsor'),actions(btn(view==='Chancellor'?'Add to agenda':'Sponsor motion',()=>{state.sponsorship=true;note(view==='Chancellor'?'Sample motion added to agenda.':'House Ash sponsored this proposal.');render();},state.rebel||(view==='Chancellor'&&!state.sponsorship)))));if(state.rebel)disabledReason('A rebel house cannot sponsor Council motions.');if(view==='Chancellor'&&!state.sponsorship)$('#detail').append(actions(btn('Add as emergency motion…',()=>confirm('Bypass sponsorship?','Record this as a Chancellor-prepared emergency motion.','Add emergency motion',()=>note('Sample emergency motion added.')))));}
function motion(){heading('Open the winter granaries','Release the royal grain reserve to the market towns before winter.');const n=state.vote?state.electorate:state.rebel?7:8;$('#detail').append(para('Policy / decree · Public · '+(Math.floor(n/2)+1)+' of '+n+' houses required','tag'));
  if(state.vote==='public'){ballot(['Yes','No','Abstain']);if(view==='Chancellor')$('#detail').append(actions(btn('Close ballot…',closeBallot,false,'primary')));}
  else if(state.policyResult){$('#detail').append(card(state.policyResult.title,para(state.policyResult.totals),para('Required: '+state.policyResult.threshold+' of '+state.policyResult.seats+' eligible houses.'),actions(btn('View record',()=>navigate('Chronicle')))));}
  else{ $('#detail').append(para('On the agenda. No vote is open.'));if(view==='Chancellor')$('#detail').append(actions(btn('Open ballot…',()=>openBallot('public'),!!state.vote||!state.meetingOpen)));}
}
function openBallot(kind){if(state.vote||state.meeting!=='Discussion'||!state.meetingOpen){note('Resume Council proceedings before opening a ballot.');return;}const n=state.rebel?7:8,required=Math.floor(n/2)+1;confirm('Open '+(kind==='secret'?'regency':'this')+' ballot?',(kind==='secret'?'Secret ballot. This uses this meeting’s regency ballot. ':'Public named votes. ')+n+' eligible houses; '+required+' votes required. The electorate is fixed when you open.','Open ballot',()=>{state.vote=kind;state.electorate=n;state.choice=null;state.publicVote=kind==='public';if(kind==='secret')state.succession=true;selected[view+'Council']=kind==='secret'?'Regency':'Open the winter granaries';note('Sample ballot opened.');});}
function ballot(options){
  if(state.rebel){disabledReason('House Ash is in rebellion and has no Council vote.');return;}
  const box=el('div',undefined,'choices');for(const option of options)box.append(btn(option,()=>confirm('Cast '+option+'?','This replaces House Ash’s previous choice. You can change it while the ballot remains open.','Confirm vote',()=>{state.choice=option;note('Your sample vote has been recorded.');}),false,state.choice===option?'selected':''));
  if(view==='Player')$('#detail').append(box,para(state.choice?'Your recorded choice: '+state.choice:'You have not voted.','success'));
  else $('#detail').append(para('Chancellor authority does not grant a house vote. Cast from your participant view if you represent a house.','muted'));
  if(state.vote==='secret')$('#detail').append(para('Secret ballot. No live totals or other houses’ choices are shown.','muted'));
  else $('#detail').append(card('Public votes',para('House Rowan — Yes\nHouse Vale — No\nHouse Reed — Abstain'),para('House Ash — '+(state.choice||'Not voted'))));
}
function candidates(){return houses.slice(0,state.candidateCount).filter(x=>!state.rebel||x!=='House Ash');}
function regency(){heading('Regency','One secret succession ballot per Council meeting. Eliminated candidates keep their Council vote.');const n=state.vote?state.electorate:state.rebel?7:8,eligible=candidates();$('#detail').append(para(eligible.length+' candidates · '+(Math.floor(n/2)+1)+' of '+n+' houses required','tag'));
  if(state.vote==='secret'){ballot(eligible.concat('Abstain'));if(view==='Chancellor')$('#detail').append(actions(btn('Close regency ballot…',closeBallot,false,'primary')));return;}
  $('#detail').append(card('Candidate slate',...eligible.map(x=>para(x))));
  if(state.regencyResult)$('#detail').append(card(state.regencyResult.title,para(state.regencyResult.totals),para(state.regencyResult.description)));
  if(view==='Chancellor')$('#detail').append(actions(btn('Request regency vote…',requestRegency,!!state.vote||state.succession||!state.meetingOpen||state.electionResolved)));
  if(state.succession)disabledReason('This meeting’s regency ballot has already been used.');
}
function requestRegency(){if(state.vote||state.succession||state.electionResolved||!state.meetingOpen||state.meeting!=='Discussion')return;
  // Explicit review scenario, not a proposed random scheduler.
  if(dueComplication())return;
  openBallot('secret');
}
function closeBallot(){showModal('Close the ballot?',m=>{
  const secret=state.vote==='secret',n=state.electorate,required=Math.floor(n/2)+1;
  const demo=select('Review only: illustrative result',secret?['No majority','Ruler elected']:['Passed','Rejected'],secret?'No majority':'Passed');
  m.append(para('Uncast votes and abstentions remain in the '+n+'-house electorate.'),para('Prototype control: other houses are not connected voters. Choose a fixed result to inspect its presentation. The actual game will tally submitted votes and offer no result selector.','review-badge'),demo,actions(btn('Cancel',closeModal),btn('Close and record',()=>{
    if(secret){
      const eligible=candidates(),won=demo.lastChild.value==='Ruler elected',last=eligible.at(-1),winner=eligible[0],title=won?winner+' crowned':eligible.length===2?'Throne vacant':'No majority';
      state.regencyResult={title,totals:won?winner+' '+required+' · Other candidates '+(n-required):'Illustrative split · no candidate reached '+required+' of '+n,description:won?'The Council continues after the election. No extra vote or veto is granted.':eligible.length>2?last+' was eliminated. It retains its seat. The next regency ballot requires a later meeting.':'The final two candidates failed to reach a majority. The throne remains vacant.'};
      if(!won&&eligible.length>2)state.candidateCount--;
      state.electionResolved=won||eligible.length===2;state.history.push(title);
    }
    else{state.policyResult={title:demo.lastChild.value,totals:demo.lastChild.value==='Passed'?'Yes '+required+' · No '+(n-required-1)+' · Abstain 1':'Yes '+(required-1)+' · No '+(n-required)+' · Abstain 1',threshold:required,seats:n};state.history.push('Winter granaries decree: '+demo.lastChild.value);}
    state.vote=null;note('Illustrative result recorded.');closeModal();
  },false,'primary')));
});}
function recovery(){ $('#detail').append(card('Proceedings need attention',para('An Event outcome was interrupted. The Council cannot advance until its recorded operation is reconciled.'),para('The selected operation is retained. Recovery does not choose a new complication.','muted')));if(view==='Chancellor')$('#detail').append(actions(btn('Review interrupted operation…',()=>showModal('Interrupted Event operation',m=>m.append(para('Region change: northern crossing · awaiting acknowledgement.'),para('Use the existing Event recovery flow to reconcile the recorded operation. Ballot void/reopen policy is still a separate design decision.','warning'),actions(btn('Close',closeModal),btn('Retry same operation (demo)',()=>{state.meeting='Discussion';note('Sample retry acknowledged. No new complication was drawn.');closeModal();},false,'primary')))))));}
function character(){listItems(['Character card','Your handouts'],'Character card');if(getSelection('Character card')==='Your handouts'){heading('Your handouts','The books issued to your character. You may show or trade their physical copies in roleplay.');$('#detail').append(actions(btn('Read house information',()=>book('House Ash','Our lands supply the northern road. The Council has promised to repair it.')),btn('Read private journal',()=>book('A private journal','The old toll debt was never paid. Keep the original account out of the royal archive.'))));return;}
  heading('Character card','The existing Chronicle card, with public house affiliation.');$('#detail').append(card(state.characterName,para('They / them · House Ash'),para('A young representative sent to speak for the northern road towns.')),actions(btn('Edit character card…',()=>showModal('Edit character card',m=>{const n=field('Character name',state.characterName),d=field('Public description','A young representative sent to speak for the northern road towns.','textarea');m.append(n,d,actions(btn('Cancel',closeModal),btn('Save card',()=>{state.characterName=n.lastChild.value;closeModal();},false,'primary')));})),btn('House standing…',houseStanding)));
}
function book(title,text){showModal(title,m=>m.append(el('article',text,'paper'),actions(btn('Close',closeModal))));}
function houseStanding(){showModal('House Ash · standing',m=>{
  m.append(para(state.rebel?'In rebellion. Your character remains in the game.':'Council member. One vote; current candidate.'));
  if(state.rebel)m.append(para('Reinstatement needs a Council settlement. You can still negotiate treaties and follow public proceedings.'));
  else{
    m.append(para('Rebellion removes your vote, Council seat, candidacy, motion submission, sponsorship and palace access. You remain an active character.','warning'));
    if(state.vote)m.append(para('A ballot is open. Membership changes must wait until it closes.','warning'));
    m.append(actions(btn('Declare rebellion…',()=>rebellionForm(false),!!state.vote),btn('Join an existing rebellion…',()=>picker('Join a rebellion',['The Northern Compact'],()=>rebellionForm(true)),!!state.vote)));
  }
  m.append(actions(btn('Close',closeModal)));
});}
function rebellionForm(join){showModal(join?'Join the Northern Compact':'Declare rebellion',m=>{
  const reason=field('Public grievance','','textarea'),error=para('','warning');
  m.append(reason,para('House Ash loses its vote, Council seat, candidacy, motion submission, sponsorship and palace access. Inside the palace, you are moved to the safe outside Point. You remain in active roleplay. Reinstatement requires a settlement.','warning'),error,actions(btn('Back',houseStanding),btn('Review consequences…',()=>{
    if(!reason.lastChild.value.trim()){error.textContent='State the public grievance.';return;}
    confirm(join?'Join this rebellion?':'Declare rebellion?',reason.lastChild.value+' · You accept the listed loss of political rights and palace access.','Confirm rebellion',()=>{state.rebel=true;state.history.push('House Ash in rebellion: '+reason.lastChild.value);note('Sample rebellion recorded. You remain an active character.');},true);
  },false,'danger')));
});}
function roster(){listItems(houses.concat(otherSeats),'House Ash');const house=getSelection('House Ash');heading(house,'The Council roster. Public biography stays on the existing character card.');$('#detail').append(card('Representative',para(house==='House Ash'?state.characterName:'Existing character'),para((state.rebel&&house==='House Ash')?'In rebellion':houses.includes(house)?'Council seat · candidate':'Council seat')),actions(btn('Edit character assignment…',()=>{view='Editor';$('#context').value=view;page='Players';selected[view+page]='Character assignments';render();})));
  $('#detail').append(para('Membership changes wait while a ballot is open. Crowned-ruler removal and supervised visits need their own reviewed rules.','muted'));
}
function treaties(){
  const visible=state.treaties.filter(t=>view==='Chancellor'||t.visibility==='Public'||t.parties.includes('House Ash'));
  listItems(visible.map(t=>t.name),'Northern road pact',view==='Player'?{label:'+ Propose treaty',fn:treatyForm}:null);
  const t=visible.find(t=>t.name===getSelection('Northern road pact'))||visible[0];
  if(!t){heading('Treaties','No treaties are visible to your house.');return;}
  heading(t.name,'Read the terms before signing. A treaty never forces a vote or automatically joins a war.');
  $('#detail').append(para(t.status+' · '+t.visibility+' · '+t.parties.join(' / '),'tag'),card('Terms',para(t.terms)),para(t.parties.map(h=>h+': '+(t.signed.includes(h)?'signed':'awaiting signature')).join('\n')));
  const own=t.parties.includes('House Ash'),canSign=view==='Player'&&own&&t.status==='Offered'&&!t.signed.includes('House Ash');
  if(canSign)$('#detail').append(actions(btn('Sign these terms…',()=>confirm('Sign '+t.name+'?',t.terms+' · Sign as House Ash. Every signature binds the same displayed terms.','Sign treaty',()=>{t.signed.push('House Ash');if(t.signed.length===t.parties.length)t.status=t.scope==='Named houses'?'Active':'Awaiting ratification';})),btn('Decline…',()=>confirm('Decline this offer?','The parties will see that House Ash declined.','Decline',()=>{t.status='Declined';}))));
  if(t.status==='Active'&&view==='Player'&&own)$('#detail').append(actions(btn('Break treaty…',()=>confirm('Break '+t.name+'?','The agreement becomes broken and remains in the parties’ history. Public disclosure of private breaches is still a separate design decision.','Break treaty',()=>{t.status='Broken';},true))));
  if(t.status==='Awaiting ratification'&&view==='Chancellor')$('#detail').append(actions(btn('Prepare ratification motion…',motionForm)));
  if(view==='Chancellor')$('#detail').append(para('The GM can moderate private treaties but cannot sign for a house.','muted'));
  $('#detail').append(para('Amendment, deliberate disclosure and signature expiry need review before implementation.','review-badge'));
}
function treatyForm(draft={name:'',terms:'',kind:'Non-aggression',visibility:'Private',scope:'Named houses',parties:['House Ash']}){
  if(view!=='Player')return;
  showModal('Propose a treaty',m=>{
    const title=field('Title',draft.name),terms=field('Terms',draft.terms,'textarea'),kind=select('Type',['Non-aggression','Alliance','Mutual defense','Trade / rights','Peace / armistice','General treaty'],draft.kind),visibility=select('Visibility',['Private','Public'],draft.visibility),scope=select('Scope',['Named houses','Realm-wide (Council ratification)'],draft.scope),parties=el('div'),message=para('','warning');
    function retain(){draft.name=title.lastChild.value;draft.terms=terms.lastChild.value;draft.kind=kind.lastChild.value;draft.visibility=visibility.lastChild.value;draft.scope=scope.lastChild.value;}
    draft.parties.forEach(h=>{const row=el('div',undefined,'row');row.append(el('span',h));if(h!=='House Ash')row.append(btn('Remove',()=>{retain();draft.parties=draft.parties.filter(x=>x!==h);treatyForm(draft);}));parties.append(row);});
    m.append(title,kind,scope,visibility,el('h3','Parties'),parties,actions(btn('Add house…',()=>{retain();picker('Add signatory',houses.concat(otherSeats).filter(x=>!draft.parties.includes(x)),h=>{draft.parties.push(h);treatyForm(draft);},()=>treatyForm(draft));})),terms,message,actions(btn('Cancel',closeModal),btn('Review offer…',()=>{
      retain();if(!draft.name.trim()||!draft.terms.trim()||draft.parties.length<2){message.textContent='Add a title, terms and at least one other house.';return;}
      if(state.treaties.some(t=>t.name===draft.name)){message.textContent='Choose a distinct treaty name.';return;}
      showModal('Offer '+draft.name+'?',b=>b.append(para(draft.terms),para('Parties: '+draft.parties.join(', ')),para(draft.visibility+' · '+draft.scope),para('Sending the offer does not sign on anyone’s behalf. Each party reviews and signs.'),actions(btn('Back',()=>treatyForm(draft)),btn('Send offer',()=>{state.treaties.push({...draft,status:'Offered',signed:[]});selected[view+'Treaties']=draft.name;note('Sample offer sent. No signature was applied for another house.');closeModal();},false,'primary'))));
    },false,'primary')));
  });
}
function conflicts(){
  listItems(['House conflicts','Foreign affairs'],'House conflicts',view==='Player'?{label:'+ Declare house war',fn:declareWar}:null);
  if(getSelection('House conflicts')==='Foreign affairs'){
    heading('Foreign affairs','Foreign war is a Council decision.');
    const content=[para('At peace')];if(!state.rebel||view==='Chancellor')content.push(actions(btn('Propose foreign war…',motionForm)));
    $('#detail').append(card('The Northern March',...content),card('The River League',para('At peace')));return;
  }
  heading('House conflicts','Conflict records political sides. Loyal houses retain their seats and palace access.');
  if(state.conflict){$('#detail').append(card('War declared',para('House Ash ↔ '+state.conflict.target),para(state.conflict.reason)));if(view==='Player')$('#detail').append(actions(btn('Propose peace…',()=>treatyForm({name:'Northern peace',terms:'End the declared house war.',kind:'Peace / armistice',visibility:'Public',scope:'Named houses',parties:['House Ash',state.conflict.target]}))));}
  else{
    $('#detail').append(card('Northern road dispute',para('House Rowan ↔ House Vale'),para('War · two participating houses')));
    if(view==='Player')$('#detail').append(actions(btn('Join a side…',()=>picker('Join which side?',['House Rowan','House Vale'],h=>confirm('Join '+h+'?', 'House Ash joins voluntarily. A defense pact never enlists you automatically.','Join side',()=>{note('Sample: House Ash joined '+h+'’s side.');})))));
  }
  $('#detail').append(para('Side withdrawal, ceasefire, peace acceptance and civil-war reclassification are included in the design specification; their detailed forms await review.','review-badge'));
}
function declareWar(){if(view!=='Player')return;showModal('Declare house war',m=>{
  const target=select('Against',houses.concat(otherSeats).filter(x=>x!=='House Ash'),'House Vale'),reason=field('Reason','','textarea'),warn=para('A declaration may break a non-aggression treaty. Private terms are not published by this action.','warning');
  m.append(target,reason,warn,actions(btn('Cancel',closeModal),btn('Review declaration…',()=>confirm('Declare war on '+target.lastChild.value+'?','Your reason: '+(reason.lastChild.value||'No reason supplied')+'. Council seat and palace rights remain.','Declare war',()=>{state.conflict={target:target.lastChild.value,reason:reason.lastChild.value||'No reason supplied'};note('Sample declaration recorded.');},true))));
});}
function chronicle(){listItems(['Public record','Ending summary'],'Public record');heading(getSelection('Public record'),getSelection('Public record')==='Ending summary'?'An accurate political ending can leave the throne vacant or unresolved.':'Decrees, announced conflicts and revealed complications. Private terms and secret individual votes are excluded.');
  $('#detail').append(card('Crown',para(state.regencyResult?.title||'Unresolved · no ruler elected')),card('Recorded decisions',para(state.history.join('\n')||'No decisions recorded in this sample.')));
  if(view==='Chancellor'&&getSelection('Public record')==='Ending summary')$('#detail').append(actions(btn('Conclude evening…',()=>confirm('Conclude the evening?','Review outstanding proceedings and complication obligations first. An early conclusion records unresolved matters; it never invents a crown or marks absent Events delivered.','Review conclusion',()=>note('Proposal: conclusion review must list unresolved obligations without exposing future complication identities.')))));
}
function editor(){
  if(page==='Content pools'){pools();return;}if(page==='Game setup'){setup();return;}if(page==='Players'){cast();return;}if(page==='Regions'){regions();return;}if(page==='Events'){events();return;}
  const descriptions={'Session':'Existing play, technical pause and Building Mode controls stay here. Council recess is a different control in the Chancellor’s Council screen.','Books & Lore':'Existing named books and editing stay here. Pool entries link to templates; committed recipient-specific handouts are not reusable defaults.','Locations':'Existing Points and Block sites. Bind the safe outside Point here; palace bounds belong to Regions.','Player Roles':'Optional starting gear, starting/respawn Points and fixed books. Randomized houses do not require one role per house.','Chests':'Existing chest bindings, loot-table choice and per-chest lore book option.','Loot Tables':'Existing item percentages and lore-book entries. Banquet mechanics are independent of starting supplies.','Flags':'Existing catalogue and manual overrides. Events can observe committed political outcomes; flags cannot rewrite votes or signatures.','Encounter groups':'Existing encounter authoring is retained. It is not the political-conflict system.'};
  listItems(['Existing '+page.toLowerCase()+' screen'],'Existing '+page.toLowerCase()+' screen');heading(page,descriptions[page]);$('#detail').append(para('Existing screen retained','review-badge'));if(page==='Books & Lore')$('#detail').append(actions(btn('Example template…',()=>book('An unpaid debt','A reusable private handout template. House references resolve against the committed cast, not a shared global book edit.'))));
}
function pools(){
  listItems(Object.keys(state.pools),'House secrets',{label:'+ Create content pool',fn:()=>{if(!editing)return;showModal('Create content pool',m=>{
    const name=field('Name',''),type=select('Contains',['Book templates','Events'],'Book templates'),error=para('','warning');
    m.append(name,type,error,actions(btn('Cancel',closeModal),btn('Create draft',()=>{const n=name.lastChild.value.trim();if(!n||state.pools[n]){error.textContent='Choose a new, non-empty pool name.';return;}state.pools[n]={event:type.lastChild.value==='Events',items:[],count:1};selected[view+page]=n;note('Sample pool draft created: '+n);closeModal();},false,'primary')));
  });}});
  const chosen=getSelection('House secrets'),pool=state.pools[chosen],event=pool.event;
  heading(chosen,event?'Reusable possibilities. Live selection and arrival stay hidden.':'Reusable templates for private handouts. Character assignments are committed separately.');
  $('#detail').append(para('Proposed shared Core extension','review-badge'));
  const tabs=el('div',undefined,'tabs');for(const t of ['Entries','Draw rules'])tabs.append(btn(t,()=>{state.poolTab=t;render();},false,state.poolTab===t?'selected':''));$('#detail').append(tabs);
  if(state.poolTab==='Entries'){
    if(!pool.items.length)$('#detail').append(para('No entries yet. Add existing content below.','empty'));
    for(const x of pool.items){const r=el('div',undefined,'row');r.append(el('span',x),btn('Edit…',()=>poolEntry(chosen,x),!editing));$('#detail').append(r);}
    $('#detail').append(actions(btn(event?'Add Event…':'Add book template…',()=>picker(event?'Choose Event':'Choose book template',(event?['A missing seal','News from the border']:['A quiet alliance','A sealed confession']).filter(x=>!pool.items.includes(x)),x=>{pool.items.push(x);note('Sample entry linked: '+x);closeModal();}),!editing)));
  }else{
    const repeat=select('Repeat policy',['No repeated entry','Only entries with supported recurrence'],pool.repeat||'No repeated entry');$('#detail').append(repeat);
    if(event)$('#detail').append(para('This pool does not change an Event’s recurrence limits.'),actions(btn('Last Council complication settings',()=>{page='Game setup';selected[view+page]='Complications';render();})));
    else{const count=field('Draws per character (example)',String(pool.count),'number');count.lastChild.min='0';count.lastChild.oninput=()=>pool.pendingCount=Number(count.lastChild.value);$('#detail').append(count,para('Facts are assigned before knowledge. Templates reference the same cast.','muted'));}
    $('#detail').append(actions(btn('Save rules',()=>{if(pool.pendingCount<0||!Number.isInteger(pool.pendingCount??pool.count??0)){note('Use a non-negative whole number.');return;}pool.repeat=repeat.lastChild.value;pool.count=pool.pendingCount??pool.count;note('Sample pool rules saved.');},!editing,'primary')));
    if(chosen==='Cross-house knowledge')$('#detail').append(actions(btn('Knowledge rules…',()=>showModal('Cross-house knowledge',m=>{
      const chance=field('Independent chance per eligible other house (%)',String(pool.chance??30),'number');chance.lastChild.min='0';chance.lastChild.max='100';const error=para('','warning');
      m.append(chance,para('Example tuning only. Zero, one or several houses may know a fact. No coverage guarantee; no exposure indicator for the owner.','muted'),error,actions(btn('Cancel',closeModal),btn('Save draft',()=>{const n=Number(chance.lastChild.value);if(!Number.isFinite(n)||n<0||n>100){error.textContent='Choose a percentage from 0 to 100.';return;}pool.chance=n;closeModal();},!editing,'primary')));
    }),!editing)));
  }
}
function poolEntry(poolName,name){const pool=state.pools[poolName],key=poolName+'|'+name;showModal('Edit pool entry',m=>{
  const weight=field('Weight',String(state.poolWeights[key]||1),'number'),requirements=field('Requirements (form sketch)',pool.event?'Compatible with the current realm state':'Valid target in this cast'),error=para('','warning');weight.lastChild.min='1';
  m.append(para((pool.event?'Event: ':'Template: ')+name),weight,requirements,para(pool.event?'Edit message and sound in Events. This entry controls selection.':'Bindings must resolve to facts in the same committed cast.','muted'),para('Eligibility and template binding pickers need a second, detailed design pass. This free-text field is a placeholder, not an executable expression.','review-badge'),error,actions(btn('Remove entry',()=>{pool.items=pool.items.filter(x=>x!==name);closeModal();},!editing),btn('Cancel',closeModal),btn('Save entry',()=>{const n=Number(weight.lastChild.value);if(!Number.isInteger(n)||n<1){error.textContent='Weight must be a positive whole number.';return;}state.poolWeights[key]=n;closeModal();},!editing,'primary')));
});}
function setup(){listItems(['Council rules','Council meetings','Complications','Palace access','Banquet','Room voice'],'Council rules');const item=getSelection('Council rules');heading(item,{'Council rules':'Political rules for this scenario. Configure them before the evening begins.','Council meetings':'Meeting opportunities guide preparation. The Chancellor controls the actual gathering and votes.','Complications':'Random Events across the whole evening, delivered during a Council meeting before that meeting’s regency vote.','Palace access':'Use existing Regions and an outside Point; rebels stay active characters.','Banquet':'Optional drink and antidote gameplay, used in the world. No starting-gear requirement.','Room voice':'The acoustic policy awaits a real multi-client sound test.'}[item]);
  if(item==='Council rules'){
    const sponsors=field('Other-house sponsors required','1','number'),civil=field('Civil-war classification: total houses','4','number');$('#detail').append(sponsors,civil,card('Constitution',para('One vote per eligible house. Ordinary majority: more than half. External war and reinstatement: two thirds.'),para('Regency is secret, at most once per meeting. No majority narrows the slate across later meetings, never an immediate runoff.')),actions(btn('Save rules',()=>note('Sample rules saved.'),!editing,'primary')));
  }else if(item==='Council meetings'){
    state.meetings.forEach((x,i)=>{
      const edit=btn('Edit…',()=>showModal('Edit meeting opportunity',m=>{
        const name=field('Name',x);
        m.append(name,actions(btn('Cancel',closeModal),btn('Save',()=>{state.meetings[i]=name.lastChild.value;closeModal();},!editing,'primary')));
      }),!editing);
      $('#detail').append(card(x,para('Chancellor opens proceedings when the gathering is ready.'),actions(edit)));
    });
    $('#detail').append(actions(btn('Add meeting…',()=>showModal('Add meeting opportunity',m=>{
      const name=field('Name','');m.append(name,actions(btn('Cancel',closeModal),btn('Add',()=>{state.meetings.push(name.lastChild.value||'Additional Council');closeModal();},!editing,'primary')));
    }),!editing)),para('Four candidates can require three meetings. Insufficient opportunities produce a setup warning; rules do not silently change.','muted'));
  }else if(item==='Complications'){
    const count=field('Total across the evening',String(state.eveningCount),'number');count.lastChild.min='0';$('#detail').append(count,actions(btn('Choose Event pool…',()=>picker('Choose Event pool',['Council complications'],x=>{state.eventPool=x;closeModal();}),!editing)),para('Pool: '+state.eventPool),para('Unpredictable Council arrival. A late complication puts proceedings into recess before the regency vote. No future Event, target or countdown is shown.'),para('Spacing and reconciliation of changed meeting plans are proposals for review, not fixed gameplay rules.','muted'),actions(btn('Save',()=>{if(Number(count.lastChild.value)<0){note('Count cannot be negative.');return;}state.eveningCount=Number(count.lastChild.value);note('Sample complication count saved.');},!editing,'primary')));
  }else if(item==='Palace access'){
    $('#detail').append(para(state.incomplete?'! Palace Region and safe outside Point are missing.':'Palace Region: Great Hall\nSafe outside Point: Outer courtyard',state.incomplete?'warning':''),actions(btn('Choose Region…',()=>picker('Choose palace Region',['Great Hall','Palace grounds'],()=>{note('Sample Region linked.');closeModal();}),!editing),btn('Choose outside Point…',()=>picker('Choose safe Point',['Outer courtyard','Gatehouse'],()=>{note('Sample safe Point linked.');closeModal();}),!editing)),para('Author bounds in Regions. An unsafe or missing exit blocks a rebellion preflight.','muted'));
  }else if(item==='Banquet'){
    const enabled=check('Enable banquet intrigue',true);$('#detail').append(enabled,card('In the world',para('Hold a drink to consume it, even at full hunger. Combine a dose with a drink through its item interaction; antidote is consumed normally.'),para('Contaminated and ordinary drinks have the same appearance and normal tooltip. Poison never removes a vote.')),para('Interaction details and dose/onset/duration/death policy need review. No numbers are treated as approved by this sketch.','warning'),actions(btn('Preview drink interaction…',banquetPreview),btn('Save feature setting',()=>note('Sample banquet setting saved.'),!editing)));
  }else $('#detail').append(para('Decision pending','warning'),para('First compare physical wall/door acoustics with the desired privacy. No automatic voice-group controls are promised by this mockup.'),actions(btn('View Region integration proposal',()=>navigate('Regions'))));
}
function banquetPreview(){showModal('Banquet · interaction sketch',m=>{
  m.append(para('An ordinary-looking cup. Drink through the normal held-item action. A private contamination action operates on the exact held drink; the interaction and item recipe still need review.'),el('div','In-world item use · no permanent banquet dashboard','in-world'),para('Delayed symptoms are experienced in the world. A normal player menu never labels someone “poisoned” or names the perpetrator.','muted'),actions(btn('Close',closeModal)));
});}
function cast(){listItems(['Character assignments','Randomized handouts'],'Character assignments');const item=getSelection('Character assignments');heading(item,item==='Character assignments'?'Link existing character cards to houses. Optional Player Roles retain their existing gear and spawn functions.':'Draw from reusable content pools, review the proposed character handouts, then commit them once.');if(item==='Character assignments'){
    $('#detail').append(card('Elian Ash → House Ash',para('One representative · Council seat · candidate'),actions(btn('Choose character…',()=>picker('Choose character card',['Elian Ash','Mara Rowan'],x=>{note('Sample character selected: '+x);closeModal();}),!editing),btn('Optional role…',()=>picker('Choose existing Player Role',['None','Navigator','Attendant'],x=>{state.role=x;closeModal();}),!editing))),actions(btn('Randomized handouts',()=>chooseItem('Randomized handouts'))));
  }else{
    $('#detail').append(para(state.committed?'Cast committed · grants survive reconnect and restart':'No cast committed',state.committed?'success':''),actions(btn(state.castPreview?'Review draw…':'Generate character draw…',drawPreview,state.committed||!editing),btn('Choose content pools…',()=>picker('Choose handout pool',['House secrets','House information','Character goals','Cross-house knowledge'],x=>{note('Sample linked pool: '+x);closeModal();}),state.committed||!editing)));
    $('#detail').append(para('Character draw review is private GM preparation. Future complication selection is never part of this preview. An owner is not told who knows a secret.','muted'));if(state.committed)$('#detail').append(actions(btn('Review delivery…',()=>showModal('Committed handout delivery',m=>m.append(para('Elian Ash · delivered\nMara Rowan · awaiting inventory space'),para('Retry the same grant. Do not reroll the character or spend chest-book discovery limits.','muted'),actions(btn('Close',closeModal),btn('Retry pending grants',()=>{note('Sample: original pending grants retried.');closeModal();},false,'primary')))))));
  }
}
function drawPreview(){state.castPreview=true;showModal('Review character draw',m=>{m.append(para('Sample private GM preview. Check coherence before committing. There is no complete “who knows whose secret” matrix.'),card('Elian Ash · House Ash',para('House information: northern road tolls\nPrivate fact: an unpaid debt\nGoal: pass the road decree'),actions(btn('Read issued journal',()=>showModal('Handout · Elian Ash',b=>b.append(el('article','The old toll debt was never paid. Keep the account away from the archive.','paper'),actions(btn('Back to draw',drawPreview))))))),card('Mara Rowan · House Rowan',para('House information: river merchants\nGoal: keep the grain reserve sealed')),
    actions(btn('Cancel',closeModal),btn('Reroll character draw',()=>{note('Sample character-only reroll. No future complications are selected.');drawPreview();}),btn('Commit & deliver…',()=>confirm('Commit this cast?','Assignments and private handout grants become durable. Reconnects and later play windows do not reroll them.','Commit & deliver',()=>{state.committed=true;note('Sample cast committed.');}),false,'primary')));},true);}
function regions(){listItems(['Great Hall','Outer courtyard'],'Great Hall');heading(getSelection('Great Hall'),'Existing Region geometry and the shovel selection workflow. Region subtypes add only their relevant options.');$('#detail').append(para('Existing screen + proposed guarded-access subtype','review-badge'),actions(btn('Edit bounds…',()=>confirm('Continue selection in world?','Use the existing debug shovel or /chronicle regions pos1 and pos2 workflow; the bounding box follows selection.','Continue in world',()=>note('Prototype: return to in-world Region editing.'))),btn('Choose subtypes…',()=>showModal('Region subtypes',m=>m.append(check('Saved states'),check('Guarded access (proposed)',true),para('Room voice is deliberately omitted until the acoustic policy is selected.','muted'),actions(btn('Cancel',closeModal),btn('Save selection',closeModal,!editing,'primary')))))));
  $('#detail').append(card('Guarded access',para('Rule: Last Council palace eligibility'),para('Outside Point: Outer courtyard'),actions(btn('Choose outside Point…',()=>picker('Choose safe exit Point',['Outer courtyard','Gatehouse'],()=>closeModal()),!editing))));
}
function events(){listItems(['Call the Council','The northern crossing falls'],'Call the Council',{label:'+ Create Event',fn:()=>showModal('Create Event · existing editor',m=>m.append(para('Existing workflow: Basics · Message & sound · Trigger · Changes.'),para('Proposed political sources observe committed outcomes such as Council called, ballot closed or treaty activated. They cannot force a signature, set vote choices or bypass the regency gate.','muted'),actions(btn('Close',closeModal))))});const name=getSelection('Call the Council');heading(name,name==='Call the Council'?'Existing Event supplies the gathering message and alarm. Calling does not open a ballot.':'A reusable possible complication. Membership in a pool is not a fixed agenda or a preview of the live draw.');$('#detail').append(para('Existing Events editor + proposed source integration','review-badge'),card('Message & sound',para(name==='Call the Council'?'“The Chancellor calls the houses to Council.”':'“The northern crossing has fallen.”')),actions(btn('Edit Event…',()=>showModal('Edit Event · retained workflow',m=>m.append(field('Message',name==='Call the Council'?'The Chancellor calls the houses to Council.':'The northern crossing has fallen.','textarea'),select('Trigger',['Manual','Council call source (proposed)','Pool dispatch (proposed)'],'Manual'),para('Use the existing sound picker, prerequisite editor, Region changes and flag updates. No second Council cue editor.','muted'),actions(btn('Cancel',closeModal),btn('Save draft',()=>{note('Sample Event draft saved.');closeModal();},!editing,'primary'))))),btn('Preview authoring content',()=>confirm('Preview this possible Event?','Authoring previews show pool possibilities, not which complication will be selected during live play. No political state is changed.','Preview',()=>note('Sample private preview. No real sound played.')))));
}
$('#context').onchange=()=>{view=$('#context').value;page=view==='Editor'?'Game setup':'Council';document.body.dataset.pane='list';render();};
$('#example').onchange=()=>{const e=$('#example').value;state=sample();if(e==='gathering'){state.meeting='Between meetings';state.meetingOpen=false;}if(e==='public'){state.vote='public';state.publicVote=true;}if(e==='secret'){state.vote='secret';state.succession=true;}if(e==='recess'){view='Chancellor';$('#context').value=view;state.meeting='Discussion';note('Review path: Request regency vote → due Event → recess → resume → request again.');}if(e==='rebel')state.rebel=true;if(e==='recovery')state.meeting='Recovery';if(e==='empty')state.incomplete=true;selected={};page=view==='Editor'?'Game setup':'Council';render();};
$('#reset').onclick=()=>{state=sample();selected={};$('#example').value='normal';debug=false;note('Demo reset. All changes were in memory.');render();};
$('#debug').onclick=()=>{debug=!debug;render();};$('#editing').onclick=()=>{editing=!editing;render();};
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&$('#overlay').firstChild){e.preventDefault();closeModal();}if(e.key==='Tab'&&$('#overlay').firstChild){const focusable=[...$('#overlay').querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled)')];const first=focusable[0],last=focusable.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}}});
document.body.dataset.pane='nav';render();
