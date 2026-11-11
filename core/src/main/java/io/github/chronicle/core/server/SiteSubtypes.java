package io.github.chronicle.core.server;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import java.util.*;

/** Independent authoring capabilities. Each trusted handler retains its own authoritative data. */
public final class SiteSubtypes {
    public static final String SAVED_STATES="chronicle_core:saved_states", DEPARTURE="chronicle_core:departure";
    public enum Kind { REGION, POINT, BLOCK }
    public enum Change { APPLIED, CONFIGURE_DEPARTURE, REVIEW_STATE_REMOVAL }
    public interface Handler {
        boolean assigned(MinecraftServer server,AuthoringSite site);
        default String refusal(MinecraftServer server,AuthoringSite site,boolean enabling){return "";}
        Change change(ServerCommandSource source,AuthoringSite site,boolean enabled) throws Exception;
    }
    public record Definition(String id,String label,String description,Set<Kind> kinds,Handler handler){
        public Definition {
            if(id==null||!AuthoringSite.NAMESPACED_ID.matcher(id).matches()||id.length()>160||label==null||label.isBlank()||label.length()>96||description==null||description.length()>2048)
                throw new IllegalArgumentException("Invalid site subtype definition");
            kinds=Set.copyOf(kinds);Objects.requireNonNull(handler);if(kinds.isEmpty())throw new IllegalArgumentException("A subtype needs a geometry kind");
        }
    }
    /** Also usable as an isolated registry in campaigns/tests; kinds are never mutually exclusive. */
    public static final class Registry {
        private final Map<String,Definition> definitions=new LinkedHashMap<>();
        private boolean frozen;
        public void register(Definition definition){if(frozen)throw new IllegalStateException("Site subtype registration is frozen");if(definitions.size()>=12)throw new IllegalStateException("At most twelve site subtype definitions are supported");if(definitions.putIfAbsent(definition.id(),definition)!=null)throw new IllegalArgumentException("Duplicate site subtype: "+definition.id());}
        public void freeze(){frozen=true;}
        public List<Definition> definitions(){return List.copyOf(definitions.values());}
        public Optional<Definition> get(String id){return Optional.ofNullable(definitions.get(id));}
        public List<Definition> applicable(AuthoringSite site){return definitions.values().stream().filter(d->d.kinds().contains(kind(site))).toList();}
    }
    private static final Registry TYPES=new Registry();
    private SiteSubtypes(){}
    public static void register(Definition definition){TYPES.register(definition);}
    public static void registerBuiltins(){
        register(new Definition(SAVED_STATES,"Saved states","Capture and apply Start and End states of this Region.",Set.of(Kind.REGION),new Handler(){
            public boolean assigned(MinecraftServer server,AuthoringSite site){return AreaChanges.forRegion(server,site.id()).isPresent();}
            public Change change(ServerCommandSource source,AuthoringSite site,boolean enabled)throws Exception{if(enabled){AreaChanges.enableForRegion(source,site.id());return Change.APPLIED;}return Change.REVIEW_STATE_REMOVAL;}
        }));
        register(new Definition(DEPARTURE,"Departure","A bound control lets the online participants aboard a Region leave together.",Set.of(Kind.BLOCK),new Handler(){
            public boolean assigned(MinecraftServer server,AuthoringSite site){return departure(server,site.id()).isPresent();}
            public String refusal(MinecraftServer server,AuthoringSite site,boolean enabled){return !enabled&&departure(server,site.id()).map(d->Departures.state(server).usedByDefinition().containsKey(d.id())).orElse(false)?"A used departure remains immutable history.":"";}
            public Change change(ServerCommandSource source,AuthoringSite site,boolean enabled)throws Exception{if(enabled)return Change.CONFIGURE_DEPARTURE;var d=departure(source.getServer(),site.id()).orElseThrow();Departures.delete(source,d.id(),d.revision(),Departures.state(source.getServer()).revision());return Change.APPLIED;}
        }));
        ServerLifecycleEvents.SERVER_STARTING.register(server->TYPES.freeze());
    }
    public static Kind kind(AuthoringSite site){return site instanceof AuthoringSite.Region?Kind.REGION:site instanceof AuthoringSite.Location?Kind.POINT:Kind.BLOCK;}
    public static Optional<io.github.chronicle.core.departures.DepartureDefinition> departure(MinecraftServer server,String siteId){
        var matches=Departures.state(server).definitions().values().stream().filter(d->d.exactBlockAnchor().map(a->a.id().equals(siteId)).orElse(false)).toList();
        if(matches.size()>1)throw new IllegalStateException("More than one departure references this Block site; repair its definitions while stopped.");return matches.stream().findFirst();
    }
    public static List<Entry> choices(MinecraftServer server,AuthoringSite site,boolean editable){
        List<Entry> choices=new ArrayList<>();
        for(var type:TYPES.applicable(site)){
            boolean applicable=type.kinds().contains(kind(site)),assigned=applicable&&type.handler().assigned(server,site);
            String reason=!applicable?"This subtype requires "+type.kinds()+" geometry.":!editable?"Enable non-active Editing to change subtypes.":type.handler().refusal(server,site,!assigned);
            String state=assigned?(reason.isBlank()?"ASSIGNED":"ASSIGNED_LOCKED"):(reason.isBlank()?"AVAILABLE":"UNAVAILABLE");
            choices.add(new Entry(type.id(),type.label(),reason.isBlank()?type.description():reason,state,kind(site).name(),0));
        }return List.copyOf(choices);
    }
    public static Set<String> assigned(MinecraftServer server,AuthoringSite site){Set<String> result=new LinkedHashSet<>();for(var d:TYPES.applicable(site))if(d.handler().assigned(server,site))result.add(d.id());return Set.copyOf(result);}
    /** Preflight all changes first. Extension handlers retain their individual durable boundaries. */
    public static List<Change> set(ServerCommandSource source,AuthoringSite site,Set<String> desired)throws Exception{
        if(!source.hasPermissionLevel(2)||source.getPlayer()==null||!AuthoringRuntime.isAuthor(source.getPlayer())||!AuthoringRuntime.editable(source.getServer()))throw new IllegalStateException("Subtype edits require non-active operator Editing.");
        if(RuntimeHolds.reason(source.getServer())!=null)throw new IllegalStateException("Finish pending operations before changing subtypes.");
        Set<String> before=assigned(source.getServer(),site);var applicable=TYPES.applicable(site);Set<String> allowed=new HashSet<>();for(var d:applicable)allowed.add(d.id());if(!allowed.containsAll(desired))throw new IllegalArgumentException("Choose registered subtypes for this geometry kind.");
        var changes=applicable.stream().filter(d->before.contains(d.id())!=desired.contains(d.id())).toList();
        if(before.contains(SAVED_STATES)&&!desired.contains(SAVED_STATES))throw new IllegalStateException("Choose which saved state to leave before removing Saved states.");
        for(var d:changes){String refusal=d.handler().refusal(source.getServer(),site,desired.contains(d.id()));if(!refusal.isBlank())throw new IllegalStateException(refusal);}
        List<Change> result=new ArrayList<>();for(var d:changes)result.add(d.handler().change(source,site,desired.contains(d.id())));return List.copyOf(result);
    }
}
