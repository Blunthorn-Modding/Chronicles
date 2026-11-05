'use strict';
// UI proposal only: sample state, no storage/network/world integration.
const $=q=>document.querySelector(q);
const houses=['House Ash','House Rowan','House Vale','House Reed'];
const votingHouses=houses.concat('House Elm','House Flint','House Mere','House Thorn');
const actorHouse=()=>({ash:'House Ash',rowan:'House Rowan',vale:'House Vale',reed:'House Reed'}[$('#actor').value]||null);
const gm=()=>$('#actor').value==='gm';
const isRebel=h=>state.rebels.includes(h);
const presentElectorate=()=>votingHouses.filter(h=>state.regionHouses.includes(h)&&!isRebel(h));
const insideRegion=()=>state.regionHouses.includes(actorHouse());
function ballotSnapshot(definition){const electorate=Object.freeze(presentElectorate());if(!electorate.length)return null;const twoThirds=['Foreign war','Reinstatement'].includes(definition.kind);return {...definition,electorate,seats:electorate.length,required:twoThirds?Math.ceil(2*electorate.length/3):Math.floor(electorate.length/2)+1};}
// One assigned fact supplies both the owner's private information and any leaked knowledge.
const grainFact={owner:'House Vale',capacity:3};
const grainReport=()=>`House Vale can reliably supply wheat to ${grainFact.capacity} other houses. Further promises exceed its reliable reserves.`;
const houseContent={
  'House Ash':{reputation:'Wardens of the northern roads',public:'House Ash controls the old stone roads through the northern hills. Merchants rely on its bridges, toll posts and timber yards to reach the capital.',interests:'Public interests: safe trade routes, bridge repairs and recognition of the northern towns.',goal:'Secure Council support for rebuilding the northern bridge.',private:'The family toll debt was never paid. The original account survives in the archive.'},
  'House Rowan':{reputation:'The river merchants',public:'House Rowan commands a network of river barges and market warehouses. Its influence comes from moving other houses’ goods and arranging access to the ports.',interests:'Public interests: open river markets, favorable duties and dependable trading partners.',goal:'Secure a trade agreement with another house without surrendering river toll rights.',private:'Several guild captains have promised their support only if the toll charter remains intact.'},
  'House Vale':{reputation:'A major exporter of wheat',public:'House Vale’s broad valley farms and old granaries make it one of the kingdom’s best-known wheat exporters. Other lords often seek its support before a harsh winter.',interests:'Public interests: agricultural influence, dependable roads and political allies for the valley towns.',goal:'Use wheat agreements to secure public backing from other houses.',private:null},
  'House Reed':{reputation:'Keepers of the coastal archives',public:'House Reed maintains coastal chart rooms, old legal records and a respected school of scribes. Its seals and witnesses give weight to disputed titles and agreements.',interests:'Public interests: recognition of historic rights, reliable treaties and protection of the archives.',goal:'Persuade the Council to recognize the authority of the coastal archives.',private:'An unregistered copy of an old charter is still in your family’s keeping.'}
};
const fresh=()=>({personalTab:'Character',houseSection:'House information',house:'House Ash',name:'Elian Ash',pronouns:'they / them',description:'A representative of the northern road towns.',
  scene:'personal',rebels:[],profiles:{'House Ash':{name:'Elian Ash',pronouns:'they / them',description:'A representative of the northern road towns.'},'House Rowan':{name:'Mara Rowan',pronouns:'she / her',description:'An envoy of the river merchants.'},'House Vale':{name:'Oren Vale',pronouns:'he / him',description:'A representative of the valley farms.'},'House Reed':{name:'Sera Reed',pronouns:'she / her',description:'A representative of the coastal archives.'}},debug:false,meeting:'Discussion',meetingNumber:1,regencyUsed:false,complicationDelivered:false,
  ballot:null,choices:{},closedBallot:null,selectedMotion:0,motionFilter:'Upcoming',regionHouses:houses.concat('House Elm'),
  motions:[{title:'Open the winter granaries',text:'Release the royal grain reserve to the market towns before winter.',house:'House Vale',sponsored:true,visibility:'Public',kind:'Policy / decree'},
    {title:'Repair the northern bridge',text:'Merchants will fund half of the repairs.',house:'House Rowan',sponsored:false,visibility:'Public',kind:'Policy / decree'}],
  treaties:[{id:1,name:'Northern road pact',terms:'Neither house will declare war on the other while the northern road is being repaired.',parties:['House Ash','House Rowan'],visibility:'Private',signed:[],status:'Draft'}],selectedTreaty:1,nextTreaty:2,
  lecternUsers:['House Ash','House Rowan'],
  challengeTarget:'House Rowan',wars:[],records:[],personalDraft:null});
let state=fresh(),modalOrigin;
function node(tag,text,cls=''){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;e.className=cls;return e;}
function p(t,c=''){return node('p',t,c+(t.includes('\n')?' record':''));}
function button(t,fn,disabled=false,cls=''){const b=node('button',t,cls);b.type='button';b.disabled=disabled;b.onclick=()=>fn();return b;}
function actions(...buttons){const e=node('div',undefined,'actions');e.append(...buttons);return e;}
function panel(title,...content){const e=node('section',undefined,'panel');e.append(node('h2',title),...content);return e;}
function field(title,value,multi=false){const e=node('label',title),i=node(multi?'textarea':'input');i.value=value;e.append(i);return e;}
function select(title,options,value,fn){const e=node('label',title),s=node('select');options.forEach(x=>{const o=node('option',x);o.value=x;o.selected=x===value;s.append(o);});if(fn)s.onchange=()=>fn(s.value);e.append(s);return e;}
function say(t){$('#notice').textContent=t;}
function scene(v){const own=actorHouse();if(state.scene==='treaty'&&v!=='treaty'&&own)state.lecternUsers=state.lecternUsers.filter(h=>h!==own);if(v==='treaty'&&own&&!state.lecternUsers.includes(own))state.lecternUsers.push(own);state.scene=v;$('#scene').value=v==='ballot'?'personal':v;render();}
function screenHeader(title,close=()=>scene('world')){const bar=node('header',undefined,'bar');bar.append(node('h1',title),button('Close',close));$('#screen').append(bar);}
function popup(title,build){
  if(!$('#overlay').firstChild)modalOrigin=document.activeElement;
  $('#overlay').replaceChildren();$('#screen').inert=true;$('.review').inert=true;$('.examples').inert=true;
  const shade=node('div',undefined,'shade'),box=node('section',undefined,'popup');box.role='dialog';box.setAttribute('aria-modal','true');const h=node('h1',title);h.id='popup-heading';box.setAttribute('aria-labelledby','popup-heading');box.append(h);shade.append(box);$('#overlay').append(shade);build(box);box.querySelector('input,textarea,select,button:not(:disabled)')?.focus();
}
function closePopup(){const origin=modalOrigin;$('#overlay').replaceChildren();$('#screen').inert=false;$('.review').inert=false;$('.examples').inert=false;render();if(origin?.isConnected)origin.focus();else $('#scene').focus();}
function confirm(title,text,verb,fn,danger=false){popup(title,m=>m.append(p(text),actions(button('Cancel',closePopup),button(verb,()=>{fn();closePopup();},false,danger?'danger':'primary'))));}
function choose(title,options,fn,back=closePopup){popup(title,m=>{const search=field('Search',''),list=node('div',undefined,'choices');function draw(){list.replaceChildren();const found=options.filter(x=>x.toLowerCase().includes(search.lastChild.value.toLowerCase()));if(!found.length)list.append(p('No matches.','muted'));found.forEach(x=>list.append(button(x,()=>fn(x))));}search.lastChild.oninput=draw;m.append(search,list,actions(button('Back',back)));draw();});}
function render(){
  const s=$('#screen');s.replaceChildren();s.className='screen'+(['motions','authoring'].includes(state.scene)?' wide':'')+(state.scene==='ballot'?' ballot':'');
  const entries={personal:'Open the personal menu',motions:'Interact with the bound motion lectern in the Council hall',treaty:'Interact with the separate treaty lectern',challenge:'Use a challenge interaction on the character standing in front of you',chancellor:'Open the Chancellor’s proceedings controls',authoring:'Existing Chronicle editor · proposed bindings',ballot:'The Chancellor has opened a vote',world:'Back in the world'};
  $('#entry').textContent=entries[state.scene];
  $('#treaty-examples').hidden=state.scene!=='treaty';$('#challenge-example').hidden=state.scene!=='challenge';$('#voting-example').hidden=state.scene!=='ballot'||!actorHouse();
  $('#region-example').hidden=state.scene!=='chancellor';$('#voter-inside').checked=insideRegion();
  document.querySelectorAll('.region-user').forEach(e=>e.checked=state.regionHouses.includes(e.value));
  document.querySelectorAll('.lectern-user').forEach(e=>e.checked=state.lecternUsers.includes(e.value));
  if(state.scene==='personal')personal();else if(state.scene==='motions')motions();else if(state.scene==='treaty')treaty();else if(state.scene==='challenge')challenge();else if(state.scene==='chancellor')chancellor();else if(state.scene==='ballot')ballot();else if(state.scene==='authoring')authoring();else world();
}
function personal(){
  screenHeader('Character & houses');const tabs=node('div',undefined,'tabs');['Character','My house','Other houses'].forEach(t=>tabs.append(button(t,()=>{state.personalTab=t;render();},false,state.personalTab===t?'selected':'')));$('#screen').append(tabs);
  const own=actorHouse();
  if(state.personalTab==='Character'){
    if(!own){$('#screen').append(p('The Chancellor uses the same existing character card. Political powers are separate from its public fields.','muted'));return;}
    const profile=state.profiles[own];$('#screen').append(panel(profile.name,p(profile.pronouns),p(profile.description),p(own,'tag')),actions(button('Edit character card…',()=>editCard(own))));return;
  }
  if(state.personalTab==='My house'){
    if(!own){$('#screen').append(p('The Chancellor has no house assignment in this example.','muted'));return;}
    const content=houseContent[own],layout=node('div',undefined,'split'),list=node('div',undefined,'list'),detail=node('div');
    $('#screen').append(node('h2',own));
    ['House information','Secrets','Goals',"Other houses' secrets"].forEach(section=>list.append(button(section,()=>{state.houseSection=section;render();},false,state.houseSection===section?'selected':'')));
    detail.append(node('h2',state.houseSection==="Other houses' secrets"?'Secrets about other houses':state.houseSection));
    if(state.houseSection==='House information')detail.append(p(content.reputation,'tag'),p(content.public),p(content.interests));
    else if(state.houseSection==='Secrets')detail.append(p(own===grainFact.owner?grainReport():content.private));
    else if(state.houseSection==='Goals')detail.append(p(content.goal));
    // This sample draw assigns Rowan one report. Other draws may expose a secret to nobody.
    else if(own==='House Rowan')detail.append(node('h3','House Vale'),p(grainReport()));
    else detail.append(p('Your papers contain no secrets about other houses.','muted'));
    layout.append(list,detail);$('#screen').append(layout);
    $('#screen').append(actions(button('Agreements & declarations',()=>records(true))));
    if(!isRebel(own))$('#screen').append(actions(button('Declare rebellion…',rebellion,!!state.ballot,'danger')));else $('#screen').append(p('In rebellion. Reinstatement requires a Council settlement.','muted'));
    if(state.ballot&&!isRebel(own))$('#screen').append(p('Membership changes must wait until the open ballot closes.','warning'));return;
  }
  const others=houses.filter(h=>h!==own);if(!others.includes(state.house))state.house=others[0];
  const layout=node('div',undefined,'split'),list=node('div',undefined,'list'),detail=node('div');
  others.forEach(h=>list.append(button(h,()=>{state.house=h;render();},false,state.house===h?'selected':'')));
  const h=state.house,content=houseContent[h];detail.append(node('h2',h),p(content.reputation,'tag'),p(content.public),p(content.interests),p(isRebel(h)?'In rebellion · no Council seat':'Council seat · crown candidate','tag'),p('Representative: '+state.profiles[h].name,'muted'),actions(button('Public record',()=>records(false))));layout.append(list,detail);$('#screen').append(layout);
}
function editCard(house){popup('Edit character card',m=>{
  const profile=state.profiles[house],name=field('Name',profile.name),pronouns=field('Pronouns',profile.pronouns),description=field('Public description',profile.description,true),error=p('','warning');
  m.append(name,pronouns,description,error,actions(button('Cancel',closePopup),button('Save',()=>{if(!name.lastChild.value.trim()){error.textContent='Enter a character name.';return;}profile.name=name.lastChild.value;profile.pronouns=pronouns.lastChild.value;profile.description=description.lastChild.value;say('Sample character card saved.');closePopup();},false,'primary')));
});}
function readBook(title,text){popup(title,m=>m.append(node('article',text,'paper'),actions(button('Close',closePopup))));}
function records(own){popup(own?'House agreements & declarations':'Public record',m=>{
  state.treaties.filter(t=>t.status==='Active'&&(t.visibility==='Public'||own&&t.parties.includes(actorHouse()))).forEach(t=>m.append(panel(t.name,p(t.terms),p(t.visibility,'tag'))));
  const visible=state.records.filter(r=>!r.private||own&&r.parties?.includes(actorHouse()));m.append(p(visible.map(r=>r.text).join('\n')||'No recorded decisions yet.','muted'),p('Treaties are formed at the treaty lectern. War is declared to the other character in person.','muted'),actions(button('Close',closePopup)));
});}
function rebellion(){popup('Declare rebellion',m=>{
  const reason=field('Public grievance','',true),error=p('','warning');m.append(reason,p('Your house loses its vote, Council seat, candidacy, motion and sponsorship rights, and palace access. You remain an active character. An authored safe outside Point is required.','warning'),error,actions(button('Cancel',closePopup),button('Review declaration…',()=>{if(!reason.lastChild.value.trim()){error.textContent='State the grievance.';return;}confirm('Leave the Council in rebellion?',reason.lastChild.value+' · Reinstatement requires a settlement.','Declare rebellion',()=>{state.rebels.push(actorHouse());state.records.push({text:actorHouse()+' declared rebellion: '+reason.lastChild.value});say('Sample rebellion declared. Roleplay continues.');},true);},false,'danger')));
});}
function motions(){
  screenHeader('Council motions');$('#screen').append(p('Read the upcoming business or submit a proposal to the Chancellor.','muted'),actions(button(gm()?'+ Prepare motion':'+ Propose motion',()=>motionForm(gm()),isRebel(actorHouse()))));
  if(isRebel(actorHouse()))$('#screen').append(p('A rebel house cannot submit or sponsor Council motions.','warning'));
  const layout=node('div',undefined,'split'),list=node('div',undefined,'list'),detail=node('div');
  state.motions.forEach((m,i)=>{const b=button(m.title,()=>{state.selectedMotion=i;render();},false,state.selectedMotion===i?'selected':'');b.append(node('small',m.sponsored?'On the agenda':'Needs a sponsor'));list.append(b);});
  const m=state.motions[state.selectedMotion];if(m){detail.append(node('h2',m.title),p(m.text),p(m.house+' · '+m.kind,'tag'),p(m.sponsored?'Ready for the Chancellor to bring to the floor.':'Needs another house to sponsor it.','muted'));
    if(!m.sponsored&&m.house!==actorHouse()&&!gm())detail.append(actions(button('Sponsor motion',()=>{m.sponsored=true;say('Sample sponsorship recorded.');render();},isRebel(actorHouse()))));
    if(m.house===actorHouse()&&!state.ballot)detail.append(actions(button('Withdraw…',()=>confirm('Withdraw this proposal?','It has not been opened for a vote.','Withdraw',()=>{state.motions.splice(state.selectedMotion,1);state.selectedMotion=0;}))));
  }else detail.append(p('No motions submitted yet.','empty'));
  layout.append(list,detail);$('#screen').append(layout);
}
function motionForm(host){popup(host?'Prepare motion':'Propose motion',m=>{
  const title=field('Title',''),text=field('Proposal','',true),kind=select('Decision',['Policy / decree','Foreign war','Foreign peace','Reinstatement','Advisory resolution'],'Policy / decree'),visibility=select('Ballot',['Public','Secret'],'Public'),target=field('Relevant house or realm (for a typed decision)',''),error=p('','warning');
  m.append(title,text,kind,target,visibility,error,actions(button('Cancel',closePopup),button(host?'Add to agenda':'Submit proposal',()=>{if(!title.lastChild.value.trim()||!text.lastChild.value.trim()){error.textContent='Add a title and proposal.';return;}state.motions.push({title:title.lastChild.value,text:text.lastChild.value,kind:kind.lastChild.value,visibility:visibility.lastChild.value,house:host?'Chancellor':actorHouse(),sponsored:host});state.selectedMotion=state.motions.length-1;say('Sample proposal submitted.');closePopup();},false,'primary')));
});}
function ballot(){
  const b=state.ballot,house=actorHouse();if(!b){world();return;}if(!house||isRebel(house)){screenHeader('Council notice');$('#screen').append(p(!house?'The ballot is open. Close it from the Chancellor’s proceedings controls.':'Your house has no Council vote.'),actions(button('Return to world',()=>scene('world'))));return;}
  if(!b.electorate.includes(house)){screenHeader('Council vote');$('#screen').append(p('Your house was not in the voting Region when this ballot opened. You cannot join this vote.','warning'));return;}
  if(!insideRegion()){screenHeader('Council vote');$('#screen').append(p('You are outside the voting Region. You cannot vote from here.','warning'),p('This ballot still requires '+b.required+' of its original '+b.seats+' houses.','muted'));return;}
  screenHeader(b.title,()=>{say('Example: ballot dismissed, not an abstention. An active-vote reminder lets you return.');scene('world');});
  $('#screen').append(p(b.text),p(b.visibility+' ballot · '+b.required+' of '+b.seats+' houses required','tag'));
  const submitted=Object.hasOwn(state.choices,house),choices=node('div',undefined,'choices');b.options.forEach(x=>choices.append(button(x,()=>confirm('Vote '+x+'?', 'This records '+house+'’s final choice. Once confirmed, it cannot be changed for this ballot.','Confirm final vote',()=>{if(state.ballot!==b||Object.hasOwn(state.choices,house)||!insideRegion()||!b.electorate.includes(house))return;state.choices[house]=x;say('Your final vote was recorded.');}),submitted,state.choices[house]===x?'selected':'')));$('#screen').append(choices);
  $('#screen').append(p(submitted?'Your final vote: '+state.choices[house]:'Choose one option.','success'));
  if(b.visibility==='Secret')$('#screen').append(p('No live totals or other houses’ choices are shown.','muted'));else $('#screen').append(p('Public votes: '+Object.entries(state.choices).map(([h,x])=>h+' — '+x).join('; '),'muted'));
}
function openVote(regency=false){
  if(!gm()||state.ballot||state.meeting!=='Discussion'||regency&&state.regencyUsed)return;
  if(!presentElectorate().length){say('No eligible representatives are in the voting Region. No ballot was opened.');return;}
  if(regency&&$('#due').checked&&!state.complicationDelivered){state.complicationDelivered=true;state.meeting='Recess';say('A messenger arrives: the northern crossing has fallen. Proceedings enter recess.');render();return;}
  const motion=state.motions[state.selectedMotion];
  state.ballot=ballotSnapshot({title:regency?'The regency':motion.title,text:regency?'Choose a ruler for the kingdom.':motion.text,visibility:regency?'Secret':motion.visibility,options:regency?houses.filter(h=>!isRebel(h)):['Yes','No','Abstain'],kind:regency?'Regency':motion.kind,regency});
  if(regency){state.ballot.options.push('Abstain');state.regencyUsed=true;}state.choices={};say('The ballot has opened. Eligible players receive only the standalone motion screen. Switch View as to inspect their popup.');render();
}
function world(){
  screenHeader('In the world');$('#screen').append(p('Move, talk and interact with the people and lecterns around you.','empty'));
  if(state.ballot&&state.ballot.electorate.includes(actorHouse()))$('#screen').append(actions(button('Return to open vote',()=>scene('ballot'))),p('Example reminder while this ballot is open. The exact dismissal/reopen policy still needs review.','muted'));
}
function chancellor(){
  screenHeader('Council proceedings');if(!gm()){$('#screen').append(p('Only the Chancellor manages proceedings. Use the review toolbar to inspect that viewpoint.','warning'));return;}
  $('#screen').append(actions(button('Call meeting',()=>confirm('Call the houses to meeting?','Play the configured Council call Event. This does not open a vote.','Sound the call',()=>{if(state.meeting==='Between meetings')state.meeting='Gathering';say('Sample meeting call: bell and announcement.');}))));
  $('#screen').append(p('Council '+state.meetingNumber+' · '+state.meeting,'tag'));
  if(state.ballot){$('#screen').append(panel('Vote open',p(state.ballot.title),p(state.ballot.visibility+' · '+state.ballot.required+' of '+state.ballot.seats+' houses required'),p('Electorate fixed when voting opened.','muted'),actions(button('Close vote…',()=>confirm('Close the vote?','The real game computes the result. This sample has no other connected voters and only records that voting closed.','Close vote',()=>{state.records.push({text:state.ballot.title+' — ballot closed (sample; no simulated tally).'});state.ballot=null;say('Sample vote closed. No winner was invented.');})))));return;}
  if(state.meeting==='Recess'){$('#screen').append(panel('Proceedings in recess',p(state.complicationDelivered?'The northern crossing has fallen.':'The Chancellor has called a recess.')),actions(button('Resume proceedings',()=>{state.meeting='Discussion';say('Proceedings resumed. No vote opened.');render();},false,'primary')));return;}
  if(state.meeting!=='Discussion'){$('#screen').append(actions(button('Open proceedings',()=>{state.meeting='Discussion';state.regencyUsed=false;render();})));return;}
  if(!state.motions[state.selectedMotion]?.sponsored)state.selectedMotion=state.motions.findIndex(m=>m.sponsored);
  const picker=select('Motion on the floor',state.motions.filter(m=>m.sponsored).map(m=>m.title),state.motions[state.selectedMotion]?.title,x=>{state.selectedMotion=state.motions.findIndex(m=>m.title===x);});$('#screen').append(picker,actions(button('Open vote…',()=>confirm('Open this motion for voting?',state.motions[state.selectedMotion]?.title||'No motion selected.','Open vote',()=>openVote(false)),!state.motions[state.selectedMotion]?.sponsored,'primary'),button('Prepare motion…',()=>motionForm(true))));
  $('#screen').append(actions(button('Request regency vote…',()=>{if($('#due').checked&&!state.complicationDelivered){openVote(true);return;}confirm('Open the regency ballot?','A secret ballot, at most once in this meeting. Due complications are resolved first.','Open regency vote',()=>openVote(true));},state.regencyUsed)),actions(button('Recess',()=>{state.meeting='Recess';render();}),button('Close meeting…',()=>{if($('#due').checked&&!state.complicationDelivered){state.complicationDelivered=true;state.meeting='Recess';say('A due complication arrives before this meeting can close.');render();return;}confirm('Close this meeting?','The evening and ordinary roleplay continue.','Close meeting',()=>{state.meeting='Between meetings';state.meetingNumber++;});})));
}
function treaty(){
  screenHeader('Treaty lectern');const house=actorHouse(),visible=state.treaties.filter(t=>gm()||t.visibility==='Public'||t.parties.includes(house));
  const canCreate=!!house&&state.lecternUsers.includes(house)&&state.lecternUsers.length>=2;
  $('#screen').append(actions(button('+ Create treaty',()=>treatyDraft(),!canCreate,'primary')));
  if(house&&!canCreate)$('#screen').append(p('At least two representatives must have this same lectern open to submit a treaty.','warning'));
  const layout=node('div',undefined,'split'),list=node('div',undefined,'list'),detail=node('div');
  visible.forEach(t=>list.append(button(t.name,()=>{state.selectedTreaty=t.id;render();},false,t.id===state.selectedTreaty?'selected':'')));
  const t=visible.find(t=>t.id===state.selectedTreaty)||visible[0];
  if(!t){detail.append(p('No agreements are available to your house at this lectern.','muted'));layout.append(list,detail);$('#screen').append(layout);return;}
  state.selectedTreaty=t.id;detail.append(node('h2',t.name),p(t.terms),p(t.parties.join(' / ')+' - '+t.visibility,'tag'));
  layout.append(list,detail);$('#screen').append(layout);
  if(t.status==='Active'){
    detail.append(p('Sanctified by the Chancellor. This agreement is active.','success'));
    if(house&&t.parties.includes(house))detail.append(actions(button('Break agreement...',()=>confirm('Break this agreement?','The breach remains in the parties record. Private breach disclosure rules still need review.','Break agreement',()=>{t.status='Broken';state.records.push({text:t.name+' broken by '+house,private:t.visibility==='Private',parties:t.parties});},true))));return;
  }
  if(t.status==='Broken'){detail.append(p('Broken agreement - retained in history','warning'));return;}
  const ready=()=>t.parties.every(h=>state.lecternUsers.includes(h))&&$('#witness').checked;
  t.parties.forEach(h=>{const row=node('div',undefined,'row');row.append(node('span',h),node('span',state.lecternUsers.includes(h)?'Lectern open':'Not using this lectern','tag'));detail.append(row);});
  detail.append(p('Chancellor: '+($('#witness').checked?'present':'absent'),'muted'));
  if(!ready())detail.append(p('Every named representative must have this lectern open, with the Chancellor present, to finish signing and sanctification.','warning'));
  if(!t.signed.length&&house&&t.parties.includes(house))detail.append(actions(button('Edit draft...',()=>treatyDraft({...t,parties:[...t.parties]}))));
  if(house&&t.parties.includes(house))detail.append(actions(button(t.signed.includes(house)?'Signed by your house':'Read & sign...',()=>confirm('Sign these terms?',t.terms+' - Sign only as '+house+'. The Chancellor must still sanctify the agreement.','Sign terms',()=>{if(!ready()){say('The required people are no longer using this lectern.');return;}if(!t.signed.includes(house))t.signed.push(house);say('Sample signature recorded for '+house+'.');}),!ready()||t.signed.includes(house),'primary')));
  detail.append(p(t.parties.map(h=>h+': '+(t.signed.includes(h)?'signed':'not signed')).join('\n'),'muted'));
  if(gm())detail.append(actions(button('Sanctify agreement...',()=>confirm('Sanctify '+t.name+'?',t.terms+' - All named houses signed these same terms.','Sanctify agreement',()=>{if(!ready()||t.signed.length!==t.parties.length){say('Attendance or signatures changed.');return;}t.status='Active';state.records.push({text:t.name+' sanctified.',private:t.visibility==='Private',parties:t.parties});say('Sample agreement sanctified.');}),!ready()||t.signed.length!==t.parties.length,'primary')));
  if(gm()&&t.signed.length!==t.parties.length)detail.append(p('Each party signs for itself before sanctification.','muted'));
}
function treatyDraft(draft){
  if(!draft)draft={name:'',terms:'',parties:[...state.lecternUsers],visibility:'Private'};
  popup(draft.id?'Edit treaty draft':'Create treaty',m=>{
    const name=field('Name',draft.name),terms=field('Terms',draft.terms,true),visibility=select('Visibility',['Private','Public'],draft.visibility),parties=node('div'),error=p('','warning');
    function retain(){draft.name=name.lastChild.value;draft.terms=terms.lastChild.value;draft.visibility=visibility.lastChild.value;}
    draft.parties.forEach(h=>{const row=node('div',undefined,'row');row.append(node('span',state.profiles[h].name+' / '+h));if(h!==actorHouse())row.append(button('Remove',()=>{retain();draft.parties=draft.parties.filter(x=>x!==h);treatyDraft(draft);}));parties.append(row);});
    const available=houses.filter(h=>state.lecternUsers.includes(h)&&!draft.parties.includes(h));
    m.append(name,terms,visibility,node('h2','Representatives using this lectern'),parties,actions(button('Add representative...',()=>{retain();choose('Using this lectern',available,h=>{draft.parties.push(h);treatyDraft(draft);},()=>treatyDraft(draft));},available.length===0)),error,actions(button('Cancel',closePopup),button('Submit treaty',()=>{
      if(!name.lastChild.value.trim()||!terms.lastChild.value.trim()||draft.parties.length<2){error.textContent='Add a name, terms and at least two parties.';return;}
      if(!draft.parties.includes(actorHouse())||!draft.parties.every(h=>state.lecternUsers.includes(h))){error.textContent='All selected representatives must still have this lectern open.';return;}
      retain();const saved={...draft,id:draft.id||state.nextTreaty++,status:'Draft',signed:[]},index=state.treaties.findIndex(t=>t.id===saved.id);
      if(index<0)state.treaties.push(saved);else state.treaties[index]=saved;state.selectedTreaty=saved.id;say('Sample treaty submitted. Each party reviews and signs separately.');closePopup();
    },false,'primary')));
  });
}
function challenge(){
  screenHeader('Challenge another house');const house=actorHouse(),present=$('#target-nearby').checked;
  $('#screen').append(p('You have challenged '+state.profiles['House Rowan'].name+', representing House Rowan.','muted'));
  if(!house){$('#screen').append(p('Chancellor authority does not declare war for a house.','warning'));return;}
  if(house==='House Rowan'){$('#screen').append(p('You cannot challenge your own house.','warning'));return;}
  const reason=field('Public reason','',true);$('#screen').append(reason,p('This declares a house war, not a request for a duel. The target is present but does not approve or veto it.','warning'));
  if(!present)$('#screen').append(p('The other representative is no longer here. Approach them to declare the challenge.','warning'));
  $('#screen').append(actions(button('Cancel',()=>scene('world')),button('Declare war…',()=>{if(!reason.lastChild.value.trim()){say('State a public reason.');return;}confirm('Declare war on House Rowan?',reason.lastChild.value+' · Both loyal houses retain their votes and palace access. An existing non-aggression pact may be broken.','Declare war',()=>{if(!$('#target-nearby').checked){say('The target is no longer present.');return;}state.wars.push({from:house,to:'House Rowan'});state.records.push({text:house+' declared war on House Rowan: '+reason.lastChild.value});say('Public announcement: '+house+' declares war on House Rowan. No target acceptance required.');state.scene='world';},true);},!present,'danger')));
}
function authoring(){
  screenHeader('Map bindings');$('#screen').append(p('Use the existing Chronicle editor to bind the places on your map.','muted'),panel('Locations → Block sites',p('Motion lectern\nTreaty lectern'),actions(button('Preview binding popup…',()=>popup('Block site subtype',m=>m.append(select('Subtype',['Motion lectern','Treaty lectern'],'Motion lectern'),p('Bind the block you are looking at using the existing Locations workflow.','muted'),actions(button('Close',closePopup))))))),panel('Regions & Points',p('Council voting Region\nPalace access Region\nSafe outside Point')),p('Assigning a connected player to a character and house is a separate GM roster task, not a map binding.','muted'));
}
$('#scene').onchange=()=>{say('');scene($('#scene').value);};
$('#actor').onchange=()=>{const own=actorHouse();if(state.scene==='personal')state.house=own||'House Ash';if(state.scene==='treaty'&&own&&!state.lecternUsers.includes(own))state.lecternUsers.push(own);if(state.ballot&&own&&state.ballot.electorate.includes(own)&&insideRegion())state.scene='ballot';render();};
document.querySelectorAll('.lectern-user').forEach(e=>e.onchange=()=>{state.lecternUsers=e.checked?[...new Set(state.lecternUsers.concat(e.value))]:state.lecternUsers.filter(h=>h!==e.value);render();});
$('#witness').onchange=()=>render();$('#target-nearby').onchange=()=>render();
function setRegionPresence(house,present){state.regionHouses=present?[...new Set(state.regionHouses.concat(house))]:state.regionHouses.filter(h=>h!==house);render();}
$('#voter-inside').onchange=()=>{if(actorHouse())setRegionPresence(actorHouse(),$('#voter-inside').checked);};
votingHouses.forEach(h=>{const label=node('label'),input=node('input');input.type='checkbox';input.value=h;input.className='region-user';input.onchange=()=>setRegionPresence(h,input.checked);label.append(input,document.createTextNode(' '+h));$('#region-users').append(label);});
$('#due').onchange=()=>say('Review condition changed. The game never shows future complication timing.');
$('#simulate').onclick=()=>{if(state.ballot){scene('ballot');return;}state.ballot=ballotSnapshot({title:'Open the winter granaries',text:'Release the royal grain reserve to the market towns before winter.',visibility:'Public',options:['Yes','No','Abstain']});if(!state.ballot){say('No eligible representatives are in the voting Region. No ballot was opened.');return;}state.choices={};scene('ballot');};
$('#reset').onclick=()=>{state=fresh();$('#actor').value='ash';$('#scene').value='personal';$('#witness').checked=true;$('#target-nearby').checked=true;$('#voter-inside').checked=true;$('#due').checked=false;say('Sample data reset.');render();};
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&$('#overlay').firstChild){e.preventDefault();closePopup();}if(e.key==='Tab'&&$('#overlay').firstChild){const items=[...$('#overlay').querySelectorAll('input,textarea,select,button:not(:disabled)')],first=items[0],last=items.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}});
render();
