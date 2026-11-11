"""Regenerate reviewed Quiet Below content from the organizer's unmodified Word source.

This is an explicit source/build operation. It never edits a server, map, installed
mod or original document. Normal mod builds consume the committed generated JSON.
"""
from __future__ import annotations
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import textwrap
import xml.etree.ElementTree as ET
import zipfile

REPO=Path(__file__).resolve().parents[1]
RESOURCE=REPO/'campaigns/quiet-below/src/main/resources/data/quiet_below/chronicle'
SOURCE_SHA='7e1536cb9e8a8b3506d6cc8514a9cc605002d390dd0be05b3c7eb8eee7076612'
W='{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
BOOKS=[
    ('weird_building_plans','Weird Building Plans','Unknown',40,48,'Private Carpenter book','OFF'),
    ('a_tale_of_the_past','A Tale of the Past','A village elder',49,56,'Private Hermit book','OFF'),
    ('research_journal_i','Research Journal I','Unknown researcher',58,62,'Collapsed City spot','UNTIL_FOUND'),
    ('research_journal_ii','Research Journal II','Unknown researcher',64,68,'Sculk Pathway','UNTIL_FOUND'),
    ('research_journal_iii','Research Journal III','Unknown researcher',70,75,'Ancient Forge','UNTIL_FOUND'),
    ('research_journal_iv','Research Journal IV','Unknown researcher',77,81,'Spruce Tower','UNTIL_FOUND'),
    ('research_journal_v','Research Journal V','Unknown researcher',83,86,'Passageway','UNTIL_FOUND'),
    ('research_journal_vi','Research Journal VI','Unknown researcher',88,94,'Ancient Altar Room','UNTIL_FOUND'),
    ('warning_for_those_who_come_after','Warning for Those Who Come After','Unknown worker',96,104,'Just before the Ancient Altar room; old dried blood stains','UNTIL_FOUND'),
]
ROLES=[
    ('hermit','Hermit','You live just outside the village by yourself; why is up to you.',1,[('stone_sword',1),('fishing_rod',1),('bread',32)],['a_tale_of_the_past']),
    ('farmer','Farmer','You take care of supplying vegetables and bread to the villagers.',2,[('hay_block',64),('iron_hoe',1),('bread',128),('potato',64),('carrot',64)],[]),
    ('woodcutter','Woodcutter','You keep the houses warm and supply the wood for the mineshaft.',2,[('iron_axe',3),('bread',32),('oak_sapling',16),('spruce_sapling',16),('oak_log',32),('spruce_log',32)],[]),
    ('carpenter','Carpenter','You keep the houses standing and help with the mineshaft extension.',1,[('iron_axe',1),('iron_pickaxe',1),('iron_shovel',1),('bread',32),('torch',32),('cobblestone',128),('oak_planks',64),('spruce_planks',64),('crafting_table',1)],['weird_building_plans']),
    ('miner','Miner','You gather resources from the mountain and supply them to the village and beyond.',2,[('iron_pickaxe',3),('bread',32),('cobblestone',128),('spruce_fence',64),('torch',128)],[]),
    ('fisher','Fisher','You fish for food and take the small sailboat to the bigger cities beyond for trade. The iron from this island seems to be of particular purity.',2,[('fishing_rod',2),('iron_sword',1),('bread',32),('cod',64),('compass',1)],[]),
]


def paragraphs(document:Path):
    raw=document.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=SOURCE_SHA:raise ValueError('The Word source changed. Review its changes before regenerating final defaults.')
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        if archive.getinfo('word/document.xml').file_size>2*1024*1024:raise ValueError('Word source XML is too large')
        tree=ET.fromstring(archive.read('word/document.xml'))
    values=[]
    for p in tree.iter(W+'p'):
        parts=[]
        for node in p.iter():
            if node.tag==W+'t':parts.append(node.text or '')
            elif node.tag==W+'br':parts.append('\n')
            elif node.tag==W+'tab':parts.append(' ')
        values.append(''.join(parts).strip())
    if len(values)!=105 or values[56]!='Research Journal I' or values[86]!='Research Journal VI':raise ValueError('Unexpected Word structure; review source ranges')
    return values


def book_pages(blocks):
    """Conservative plain pages: <=18 characters per line and <=12 lines.

    Word/paragraph text is retained; only layout whitespace and the approved timber
    noun change are normalized. Native font wrapping is checked separately.
    """
    pages=[];page=[]
    for block in blocks:
        if not block.strip():continue
        for paragraph in block.splitlines():
            if not paragraph.strip():continue
            lines=textwrap.wrap(' '.join(paragraph.split()),width=18,break_long_words=False,break_on_hyphens=False)
            if page and len(page)<11:page.append('')
            for line in lines:
                if len(page)>=12:pages.append('\n'.join(page).rstrip());page=[]
                page.append(line)
    if page:pages.append('\n'.join(page).rstrip())
    if not pages or len(pages)>64 or sum(map(len,pages))>16384:raise ValueError('Book exceeds Minecraft/Chronicle limits')
    return pages


def books(values):
    result=[]
    for ident,title,author,start,end,location,rule in BOOKS:
        blocks=[p.replace('timber seems to halt its advance','planks seem to halt its advance') if ident=='research_journal_ii' else p for p in values[start:end]]
        if ident=='warning_for_those_who_come_after':blocks.insert(0,'A warning for those who come after')
        result.append({'id':'quiet_below:'+ident,'title':title,'author':author,'description':location,'pages':book_pages(blocks),'rule':rule})
    return result


def role_yaml():
    result=['# Final starting roles from A Quite Below.docx. Map Points remain world-owned.','schemaVersion: 1','roles:']
    for ident,name,description,capacity,supplies,private in ROLES:
        result += [f'  - id: quiet_below:{ident}','    name: '+json.dumps(name),'    description: '+json.dumps(description),f'    selectable: true',f'    capacity: {capacity}','    starting: null','    respawn: null','    items:']
        for item,amount in supplies:
            size=1 if item.endswith(('_sword','_axe','_pickaxe','_shovel','_hoe')) or item=='fishing_rod' else 64
            while amount:
                count=min(amount,size);amount-=count;stack=json.dumps({'id':'minecraft:'+item,'count':count},separators=(',',':'))
                result.append("      - '"+stack+"'")
        result.append('    books: '+json.dumps(['quiet_below:'+book for book in private]))
    return '\n'.join(result)+'\n'


def entry(item,chance,minimum,maximum,components=None):
    return {'item':item if ':' in item else 'minecraft:'+item,'chancePercent':chance,'minCount':minimum,'maxCount':maximum,'components':components or {}}


def table(ident,label,description,entries,recommended):
    return {'id':'quiet_below:'+ident,'label':label,'description':description,'recommendedChests':recommended,'refillMinutes':0,'entries':entries,'books':[]}


def vanilla_city(server):
    path='data/minecraft/loot_table/chests/ancient_city.json'
    with zipfile.ZipFile(server) as archive:
        nested=[p for p in archive.namelist() if p.startswith('META-INF/versions/') and p.endswith('.jar')]
        if path in archive.namelist():data=archive.read(path)
        elif len(nested)==1:
            with zipfile.ZipFile(io.BytesIO(archive.read(nested[0]))) as game:data=game.read(path)
        else:raise ValueError('Cannot locate pinned Minecraft Ancient City table')
    native=json.loads(data);pool=native['pools'][0]
    candidates=[e for e in pool['entries'] if not(e['name']=='minecraft:book' and any(f['function'] in ('minecraft:enchant_randomly','minecraft:enchant_with_levels') for f in e.get('functions',[])))]
    total=sum(e.get('weight',1) for e in candidates);entries=[]
    for e in candidates:
        weight=e.get('weight',1);chance=round(100*sum(1-(1-weight/total)**rolls for rolls in range(5,11))/6,2)
        low=high=1;components={}
        for f in e.get('functions',[]):
            if f['function']=='minecraft:set_count':
                count=f['count'];low,high=(int(count['min']),int(count['max'])) if isinstance(count,dict) else (int(count),int(count))
            elif f['function']=='minecraft:set_potion':components['minecraft:potion_contents']={'potion':f['id']}
            elif f['function']=='minecraft:set_damage':components['minecraft:damage']=156
        # Chronicle entries are legal inventory stacks. Vanilla's potion result may
        # have an overstacked count; this editable approximation supplies one.
        if e['name']=='minecraft:potion':low=high=1
        # Independent editable entries intentionally replace vanilla's weighted roll
        # coupling and random enchantment functions. No enchanted books are supplied.
        entries.append(entry(e['name'],chance,low,high,components))
    for e in native['pools'][1]['entries']:
        if e['type']=='minecraft:item':entries.append(entry(e['name'],round(100*e.get('weight',1)/80,2),1,1))
    if len(entries)!=28:raise ValueError('Pinned Ancient City loot changed; review independent approximation')
    return entries,hashlib.sha256(data).hexdigest()


def loot_tables(server):
    city,digest=vanilla_city(server)
    return [table('ancient_city','Ancient City','Independent Minecraft 1.21.10 Ancient City approximation without enchanted books.',city,4),
            table('crashed_ship','Crashed ship','Waterlogged cargo and ocean-monument materials.',[entry('prismarine_shard',65,2,8),entry('prismarine_crystals',35,1,4),entry('gold_nugget',55,3,12),entry('gold_ingot',25,1,3),entry('wet_sponge',10,1,1),entry('gold_block',3,1,1)],2),
            table('mineshaft','Mineshaft','Mine supplies, timber planks and unprocessed resources.',[entry('cobblestone',90,16,48),entry('spruce_planks',65,16,32),entry('raw_iron',40,1,6),entry('coal',60,4,16),entry('torch',60,8,24)],4),
            table('previous_explorer_stash','Previous explorer stash','Worn tools and remains of earlier experiments.',[entry('iron_hoe',45,1,1,{'minecraft:damage':175}),entry('amethyst_shard',50,1,4),entry('rotten_flesh',80,3,12),entry('iron_nugget',60,2,8)],2)],digest


def event(ident,label,message,sound,mode='SYSTEM',source=None,delay=0,audience='PARTICIPANTS',reference='',frequency='ONCE',description=''):
    return {'id':'quiet_below:'+ident,'label':label,'description':description,'text':message,'sound':sound,'audience':audience,'audienceReference':reference,'mode':mode,'triggerReference':reference if mode=='ENTRY' else '', 'delayTicks':delay,'requiredFlags':[],'flagOutcomes':{},'regionOutcomes':[],'source':source or {'type':'','target':'','activity':''},'encounterOutcomes':[],'occurrencePolicy':frequency}


def source(kind,target,activity=''):return {'type':kind,'target':target,'activity':activity}


def events():
    sound=lambda name:'quiet_below:event.'+name
    result = [
        event('sinkhole_opening','Sinkhole Opening','The Earth quakes beneath your feet and you feel the entire island shift',sound('sinkhole_opening'),'TIMER',delay=36000,description='Enable before first play. Add the map sinkhole Region End-state actions; GM manual start remains available.'),
        event('sculk_awakening','Sculk Awakening','Something has awakened…..',sound('sculk_awakening'),source=source('chronicle_core:event_completed','quiet_below:sinkhole_opening'),delay=18000,description='Fifteen occupied active minutes after Sinkhole Opening completes.'),
        event('whisper_from_below','Whisper from Below','Something is whispering in the dark below….',sound('whisper_from_below'),'MANUAL',description='Optional GM nudge when needed.'),
        event('city_discovery','City Discovery','Something ancient lies before you…',sound('city_discovery'),'ENTRY',audience='REGION',reference='quiet_below:ancient_city',description='Bind the city trigger/audience Region on the target map.'),
        event('station_awakened','Altar or Forge Connected to Sculk','Something is ready for you…..',sound('station_awakened'),source=source('quiet_below:station_ready','*'),description='The first time either station becomes powered by the living network.'),
        event('altar_offering','First Altar Offering','You gain something….at a price…',sound('altar_offering'),source=source('chronicle_core:prayer_completed','*'),audience='SOURCE_ACTOR',frequency='ONCE_PER_ACTOR',description='Each participant hears this on their first successful Altar offering.'),
        event('sculk_growth','Sculk Growth stages','Something grows….',sound('sculk_growth'),source=source('quiet_below:growth_step','network','5000'),frequency='PER_SOURCE_OCCURRENCE',description='Each additional 5,000 living connected blocks above the initial/high-water baseline; regrowth below it does not repeat old cues.'),
        event('heart_destroyed','Heart Destroyed','Something vast ends…',sound('heart_destroyed'),source=source('quiet_below:heart_destroyed','network'),description='Durable Heart destruction; a GM may also deliberately run this before ending play.'),
        event('first_warden','First Warden spawned','An ancient creature rises from the ground…','minecraft:entity.warden.emerge',source=source('chronicle_core:entity_spawned','minecraft:warden'),description='First actual Warden spawn during active occupied play; loading a saved Warden is not a new spawn.'),
    ]
    by_id = {item['id']: item for item in result}
    for event_id, flag_id in [('sinkhole_opening', 'sinkholes_opened'), ('sculk_awakening', 'sculk_awakened'), ('city_discovery', 'city_discovered'), ('heart_destroyed', 'heart_destroyed')]:
        by_id['quiet_below:' + event_id]['flagOutcomes']['quiet_below:' + flag_id] = True
    by_id['quiet_below:sculk_awakening']['requiredFlags'] = ['quiet_below:sinkholes_opened']
    by_id['quiet_below:sinkhole_opening']['regionOutcomes'] = [{'regionId':'quiet_below:sinkhole','areaId':'quiet_below:sinkhole','state':'END'}]
    return result


def generate(document,server):
    values=paragraphs(document);loot,digest=loot_tables(server)
    content={'schemaVersion':4,'scenarioId':'quiet_below:scenario','rolesYaml':role_yaml(),'books':books(values),'lootTables':loot,'events':events(),'encounterGroups':[],'flags':{f'quiet_below:{name}':False for name in ['sinkholes_opened','sculk_awakened','city_discovered','heart_destroyed']}}
    return content,{'sourceSha256':SOURCE_SHA,'vanillaAncientCitySha256':digest,'books':len(content['books']),'roles':len(ROLES),'events':len(content['events']),'lootTables':len(loot)}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=('generate','check'));parser.add_argument('--document',type=Path,default=REPO/'docs/A Quite Below.docx');parser.add_argument('--vanilla-server',type=Path,default=REPO.parent/'server/server.jar');args=parser.parse_args()
    content,summary=generate(args.document,args.vanilla_server);encoded=json.dumps(content,indent=2,ensure_ascii=False)+'\n';target=RESOURCE/'scenario-content.json';roles=RESOURCE/'player-roles.yaml'
    if args.action=='generate':target.write_text(encoded,encoding='utf-8');roles.write_text(content['rolesYaml'],encoding='utf-8')
    elif not target.is_file() or target.read_text(encoding='utf-8')!=encoded or roles.read_text(encoding='utf-8')!=content['rolesYaml']:raise SystemExit('Generated defaults differ; review source changes and explicitly regenerate.')
    print(json.dumps(summary,indent=2))


if __name__=='__main__':main()
