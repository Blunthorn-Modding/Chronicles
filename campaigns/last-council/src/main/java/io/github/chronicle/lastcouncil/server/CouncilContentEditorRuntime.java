package io.github.chronicle.lastcouncil.server;

import com.google.gson.*;
import io.github.chronicle.lastcouncil.content.*;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.Category;
import io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.*;
import io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/** Source-only editor. All transfers and mutations are authenticated on the server thread. */
public final class CouncilContentEditorRuntime {
    private static final int MAX_SESSIONS=32, MAX_BYTES=CouncilContentJson.MAX_BYTES, MAX_CHUNKS=(MAX_BYTES+CatalogueEditorPayloads.CHUNK_BYTES-1)/CatalogueEditorPayloads.CHUNK_BYTES;
    private static final long TTL=300_000_000_000L;
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private CouncilContentEditorRuntime() {}
    private static final class Session { final ServerPlayerEntity player;long touched;CouncilContent snapshot;String digest;Category loaded;String id;boolean creating;UUID uploadId;EditorTransfer upload;Session(ServerPlayerEntity p){player=p;touched=System.nanoTime();} }
    public static void register(){ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->SESSIONS.computeIfPresent(handler.player.getUuid(),(id,session)->session.player==handler.player?null:session));ServerLifecycleEvents.SERVER_STOPPED.register(server->SESSIONS.entrySet().removeIf(e->e.getValue().player.getEntityWorld().getServer()==server));ServerPlayNetworking.registerGlobalReceiver(Request.ID,(r,c)->c.server().execute(()->handle(c.player(),r)));ServerPlayNetworking.registerGlobalReceiver(Upload.ID,(r,c)->c.server().execute(()->upload(c.player(),r)));}
    public static void open(ServerPlayerEntity p){handle(p,new Request(new UUID(0,0),Action.OPEN,Category.HOUSES,"",0,0,0,""));}
    private static boolean authorized(ServerPlayerEntity p){var s=p.getEntityWorld().getServer();return s.isOnThread()&&s.getPlayerManager().getPlayer(p.getUuid())==p&&p.getCommandSource().hasPermissionLevel(2)&&ServerPlayNetworking.canSend(p,View.ID)&&ServerPlayNetworking.canSend(p,Chunk.ID);}
    private static String digest(CouncilContent c)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CouncilContentJson.encode(c)));}
    private static Session session(ServerPlayerEntity p){Session s=SESSIONS.get(p.getUuid());if(s==null||s.player!=p||System.nanoTime()-s.touched>TTL){SESSIONS.remove(p.getUuid());return null;}s.touched=System.nanoTime();return s;}
    private static void check(Session s,String expected)throws Exception {if(s.snapshot==null||!s.digest.equals(expected)||!s.snapshot.equals(CouncilRuntime.content(s.player.getEntityWorld().getServer())))throw new IllegalStateException("Source definitions changed. Reopen the editor.");}
    private static void edit(Session s,String expected)throws Exception {String reason=CouncilRuntime.contentEditReason(s.player);if(!reason.isBlank())throw new IllegalStateException(reason);check(s,expected);}
    private static void handle(ServerPlayerEntity p,Request r){if(!authorized(p))return;Session s=session(p);String message="";boolean opening=r.action()==Action.OPEN;
        try {if(r.query()==null||r.query().length()>128||r.page()<0||r.selected()<0||r.part()<0)throw new IllegalArgumentException("Invalid page.");if(opening){SESSIONS.entrySet().removeIf(e->System.nanoTime()-e.getValue().touched>TTL);if(s==null&&SESSIONS.size()>=MAX_SESSIONS)throw new IllegalStateException("Too many editor sessions.");s=new Session(p);s.snapshot=CouncilRuntime.content(p.getEntityWorld().getServer());s.digest=digest(s.snapshot);SESSIONS.put(p.getUuid(),s);}else if(s==null)throw new IllegalStateException("Editor session expired. Reopen it.");
            if(!opening)check(s,r.digest());
            switch(r.action()){
                case LOAD,CREATE->{if(r.action()==Action.CREATE)edit(s,r.digest());s.loaded=r.category();s.creating=r.action()==Action.CREATE;s.id=s.creating?null:selected(s.snapshot,r);JsonObject doc=s.creating?CouncilContentEditor.blank(s.loaded):CouncilContentEditor.document(s.snapshot,s.loaded,s.id);s.upload=null;s.uploadId=null;sendDocument(p,r.request(),s.digest,new GsonBuilder().disableHtmlEscaping().create().toJson(doc).getBytes(StandardCharsets.UTF_8));}
                case SAVE->{edit(s,r.digest());if(s.loaded!=r.category()||s.upload==null||!r.request().equals(s.uploadId))throw new IllegalStateException("Incomplete or stale upload.");byte[] data=s.upload.finish(r.request());s.upload=null;s.uploadId=null;CouncilContent replacement=CouncilContentEditor.save(s.snapshot,s.loaded,s.creating?null:s.id,data);CouncilRuntime.replaceContent(p.getEntityWorld().getServer(),s.snapshot,replacement);s.snapshot=replacement;s.digest=digest(replacement);s.loaded=null;message="Saved.";}
                case DELETE->{edit(s,r.digest());String id=selected(s.snapshot,r);if(r.category()==Category.HOUSES&&CouncilRuntime.roster(p.getEntityWorld().getServer()).assignments().values().stream().anyMatch(a->a.houseId().equals(id)))throw new IllegalStateException("This House is assigned to a character.");CouncilContent replacement=CouncilContentEditor.delete(s.snapshot,r.category(),id);CouncilRuntime.replaceContent(p.getEntityWorld().getServer(),s.snapshot,replacement);s.snapshot=replacement;s.digest=digest(replacement);s.loaded=null;message="Deleted.";}
                default->{}
            }
        }catch(Exception e){Throwable detail=e;if(e instanceof IOException&&e.getCause() instanceof IllegalArgumentException)detail=e.getCause();message=detail.getMessage()==null?"The source request could not be completed.":detail.getMessage();if(s!=null){s.upload=null;s.uploadId=null;}}
        if(s==null)try{s=new Session(p);s.snapshot=CouncilRuntime.content(p.getEntityWorld().getServer());s.digest=digest(s.snapshot);}catch(Exception ignored){return;}
        try{ServerPlayNetworking.send(p,view(s,r,opening,message));}catch(Exception ignored){}
    }
    private static void upload(ServerPlayerEntity p,Upload u){if(!authorized(p))return;Session s=session(p);if(s==null)return;try{edit(s,u.digest());if(s.loaded==null)throw new IllegalStateException("No loaded document.");if(s.upload==null){if(u.index()!=0)throw new IllegalArgumentException("Invalid transfer order.");s.upload=new EditorTransfer(u.request(),u.count(),CatalogueEditorPayloads.CHUNK_BYTES,MAX_BYTES);s.uploadId=u.request();}s.upload.append(u.request(),u.index(),u.count(),u.data());}catch(Exception e){s.upload=null;s.uploadId=null;s.loaded=null;}}
    private static void sendDocument(ServerPlayerEntity p,UUID request,String digest,byte[] bytes){if(bytes.length==0||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Document exceeds size limit.");int count=(bytes.length+CatalogueEditorPayloads.CHUNK_BYTES-1)/CatalogueEditorPayloads.CHUNK_BYTES;for(int i=0;i<count;i++){Session s=session(p);try{if(!authorized(p)||s==null)throw new IllegalStateException();check(s,digest);}catch(Exception failure){return;}ServerPlayNetworking.send(p,new Chunk(request,digest,i,count,Arrays.copyOfRange(bytes,i*CatalogueEditorPayloads.CHUNK_BYTES,Math.min(bytes.length,(i+1)*CatalogueEditorPayloads.CHUNK_BYTES))));}}
    private static List<String> filtered(CouncilContent c,Request r)throws IOException {String q=r.query().toLowerCase(Locale.ROOT);List<String> ids=CouncilContentEditor.ids(c,r.category());if(q.isEmpty())return ids;Map<String,JsonObject> docs=CouncilContentEditor.documents(c,r.category());List<String> result=new ArrayList<>();for(String id:ids)if(id.toLowerCase(Locale.ROOT).contains(q)||docs.get(id).toString().toLowerCase(Locale.ROOT).contains(q))result.add(id);return result;}
    private static String selected(CouncilContent c,Request r)throws IOException {var list=filtered(c,r);if(r.selected()<0||r.selected()>=list.size())throw new IllegalArgumentException("Choose a source record.");return list.get(r.selected());}
    private static String label(CouncilContent c,Category cat,String id){return cat==Category.HOUSES?c.houses().get(id).title():cat==Category.KNOWLEDGE?"Knowledge rules":id;}
    private static View view(Session s,Request r,boolean opening,String message)throws Exception {var c=s.snapshot;var ids=filtered(c,r);int pages=Math.max(1,(ids.size()+5)/6),page=Math.min(r.page(),pages-1),start=page*6,end=Math.min(ids.size(),start+6),sel=ids.isEmpty()?0:Math.clamp(r.selected(),start,end-1);List<Row> rows=new ArrayList<>();for(int i=start;i<end;i++)rows.add(new Row(i,truncate(label(c,r.category(),ids.get(i)),256)));List<Card> all=new ArrayList<>();if(!ids.isEmpty())cards(c,r.category(),ids.get(sel),all);int parts=Math.max(1,(all.size()+3)/4),part=Math.min(r.part(),parts-1);List<Card> cards=all.subList(Math.min(part*4,all.size()),Math.min(part*4+4,all.size()));List<HouseRef> houses=c.houses().values().stream().sorted(Comparator.comparing(h->h.id())).map(h->new HouseRef(h.id(),h.title())).toList();String reason=CouncilRuntime.contentEditReason(s.player);return new View(r.request(),opening,r.category(),r.query(),page,pages,sel,rows,part,parts,cards,houses,reason.isBlank(),reason,truncate(message,512),s.digest);}
    private static void card(List<Card> out,String title,String value){
        if(value==null||value.isEmpty())value="Not set";
        for(int i=0;i<value.length();){int end=Math.min(value.length(),i+16000);if(end<value.length()&&Character.isHighSurrogate(value.charAt(end-1))&&Character.isLowSurrogate(value.charAt(end)))end--;out.add(new Card(truncate(title,256),value.substring(i,end)));i=end;}
    }
    private static String names(CouncilContent c,Set<String> scope){return scope.stream().sorted().map(id->c.houses().get(id).title()+" ("+id+")").reduce((a,b)->a+", "+b).orElse("None");}
    private static void cards(CouncilContent c,Category cat,String id,List<Card> out){
        switch(cat){
            case HOUSES->{var h=c.houses().get(id);card(out,"Identifier",h.id());card(out,"Title",h.title());card(out,"Reputation",h.reputation());card(out,"Description",h.description());card(out,"Interests",h.interests());card(out,"Default candidate",h.defaultCandidate()?"Yes":"No");}
            case SECRETS->{var v=c.secrets().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();card(out,"Identifier",v.id());card(out,"Template",v.text());card(out,"Weight",Integer.toString(v.weight()));card(out,"Applicable Houses",names(c,v.applicableHouseIds()));new TreeMap<>(v.facts()).forEach((k,value)->{card(out,"Fact default",k+"\n"+value);});}
            case GOALS->{var v=c.goals().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();card(out,"Identifier",v.id());card(out,"Template",v.text());card(out,"Weight",Integer.toString(v.weight()));card(out,"Applicable Houses",names(c,v.applicableHouseIds()));}
            case KNOWLEDGE->{card(out,"Secrets per House",Integer.toString(c.secretsPerHouse()));card(out,"Goals per House",Integer.toString(c.goalsPerHouse()));card(out,"Knowledge chance",c.knowledgeChancePercent()+"%");}
        }
        String warning=diagnostics(c);if(!warning.isEmpty())card(out,"Source checks",warning);
    }
    private static String diagnostics(CouncilContent c){
        List<String> issues=new ArrayList<>();
        for(var h:new TreeMap<>(c.houses()).values()){
            long secrets=c.secrets().stream().filter(x->x.applicableHouseIds().contains(h.id())).count(),goals=c.goals().stream().filter(x->x.applicableHouseIds().contains(h.id())).count();
            if(secrets<c.secretsPerHouse())issues.add(h.title()+": add "+(c.secretsPerHouse()-secrets)+" eligible secret(s).");
            if(goals<c.goalsPerHouse())issues.add(h.title()+": add "+(c.goalsPerHouse()-goals)+" eligible goal(s).");
            Set<String> defaults=new HashSet<>();c.secrets().stream().filter(x->x.applicableHouseIds().contains(h.id())).forEach(x->defaults.addAll(x.facts().keySet()));
            List<String> texts=new ArrayList<>();c.secrets().stream().filter(x->x.applicableHouseIds().contains(h.id())).forEach(x->texts.add(x.text()));c.goals().stream().filter(x->x.applicableHouseIds().contains(h.id())).forEach(x->texts.add(x.text()));
            Set<String> missing=new TreeSet<>();for(String text:texts){var m=java.util.regex.Pattern.compile("\\$\\{(fact\\.[A-Za-z_][A-Za-z0-9_.]*)}").matcher(text);while(m.find())if(!defaults.contains(m.group(1)))missing.add(m.group(1));}if(!missing.isEmpty())issues.add(h.title()+": add fact defaults for "+String.join(", ",missing)+".");
        }
        return String.join("\n",issues);
    }
    private static String truncate(String s,int n){if(s.length()<=n)return s;int end=n;if(Character.isHighSurrogate(s.charAt(end-1))&&Character.isLowSurrogate(s.charAt(end)))end--;return s.substring(0,end);}
}
