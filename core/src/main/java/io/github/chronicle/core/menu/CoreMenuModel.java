package io.github.chronicle.core.menu;

import io.github.chronicle.core.loot.LootType;
import java.util.List;
import java.util.Objects;

/** Private operator presentation. Every mutation is still authorized and validated on the server. */
public final class CoreMenuModel {
    private CoreMenuModel() { }
    public enum Page { SESSION, PLAYERS, EVENTS, CHESTS, LOOT_TABLES, REGIONS, LOCATIONS, SETUP, BOOKS_LORE, PLAYER_ROLES, AREA_CHANGES, DEPARTURES, FLAGS }
    public enum Action {
        OPEN, REOPEN, REFRESH, OPTIONS, CLOSE, EDIT_ON, EDIT_OFF, SESSION_START, SESSION_PAUSE, SESSION_RESUME, SESSION_END,
        PARTICIPANT_ADD, PARTICIPANT_REMOVE, LEAVE, CARD_OPEN,
        PLAYER_MOVE_REVIEW, PLAYER_MOVE, STARTING_SET, STARTING_OFF, STARTING_RESET, RESPAWN_SET, RESPAWN_OFF, STARTING_DEFAULT, RESPAWN_DEFAULT,
        EVENT_PREVIEW, EVENT_AUDIO, EVENT_CHECK, EVENT_REVIEW, EVENT_RUN, EVENT_SAVE, EVENT_DISABLE_AUTO, EVENT_REMOVE, EVENT_RESET,
        CONDITION_SET, CHEST_BIND, CHEST_RETYPE, CHEST_UNBIND, TABLE_OPEN, SHOW, LOOT_SAVE, LOOT_RELOAD,
        REGION_NEW, REGION_EDIT, REGION_POSITION, REGION_CONTINUE, REGION_SAVE, REGION_CREATE,
        POINT_CREATE, POINT_UPDATE, BLOCK_CREATE, BLOCK_UPDATE, SITE_DELETE, SETUP_ASSIGN, SETUP_CLEAR, TOOL_ACTION,
        REGION_ASSIGN, REGION_UNASSIGN, POINT_ASSIGN, POINT_UNASSIGN, BLOCK_LIMITS, BLOCK_LIMIT_SET, MANAGED_SHOW,
        MANUAL_REFERENCES, SESSION_BUILD_ON, SESSION_BUILD_OFF,
        ROLE_CHOICE_OPEN, ROLE_NEW, ROLE_EDIT, ROLE_CREATE, ROLE_SAVE, ROLE_DELETE, ROLE_RELOAD, ROLE_CHOOSE, ROLE_ASSIGN_REVIEW, ROLE_ASSIGN,
        AREA_NEW, AREA_BEGIN, AREA_CAPTURE_OPEN, AREA_CAPTURE, AREA_PREVIEW, AREA_APPLY_REVIEW, AREA_APPLY, AREA_DELETE_REVIEW, AREA_DELETE, AREA_RECOVER,
        DEPARTURE_NEW, DEPARTURE_EDIT, DEPARTURE_CREATE, DEPARTURE_SAVE, DEPARTURE_DELETE, DEPARTURE_BIND_LOOK,
        SITE_SUBTYPES_OPEN, SITE_SUBTYPE_SET, REGION_BEGIN, AREA_CAPTURE_REVIEW, REGION_SAVE_REVIEW,
        BOOK_NEW, BOOK_EDIT_DETAILS, BOOK_EDIT_PAGES, BOOK_RULE, BOOK_CREATE, BOOK_SAVE, BOOK_RELOAD, BOOK_REF_ADD, BOOK_REF_REMOVE, BOOK_OPEN, CHEST_BOOK_SET, BOOK_KEEP, BOOK_KEEP_START, BOOK_KEEP_STOP, BOOK_KEEP_REVIEW, BOOK_GIVE_OPEN, BOOK_GIVE_REVIEW, BOOK_GIVE, LOOT_NEW,
        EVENT_NEW, EVENT_EDIT, EVENT_CREATE, EVENT_DEFINITION_SAVE, EVENT_DELETE, EVENT_DRAFT_PREVIEW, EVENT_SHOW_BOUNDS, EVENT_RECOVER, EVENT_ACK_MEDIA,
        FLAG_CREATE
    }
    public record Entry(String id, String label, String description, String kind, String reference, long revision) {
        public Entry { text(id,160);text(label,160);text(description,2048);text(kind,32);text(reference,160);if(revision<0)throw new IllegalArgumentException("Invalid revision"); }
    }
    public record Fact(String label,String value) { public Fact {text(label,80);text(value,4096);} }
    /** input is an allowlisted picker/form kind, never a command string. */
    public record Control(Action action,String label,boolean enabled,String reason,boolean confirm,String input) {
        public Control {Objects.requireNonNull(action);text(label,96);text(reason,512);text(input,32);}
    }
    public record EventForm(String mode,String region,String flag,long seconds,boolean configured,boolean cue,boolean hasSound) {
        public EventForm {text(mode,32);text(region,160);text(flag,160);if(seconds<0||seconds>604800)throw new IllegalArgumentException("Invalid timer");}
    }
    public record RegionForm(String id,String dimension,long revision,String first,String second) {
        public RegionForm {text(id,160);text(dimension,160);text(first,96);text(second,96);if(revision<0)throw new IllegalArgumentException("Invalid region revision");}
    }
    public record Detail(String id,String title,String description,List<Fact> facts,List<Control> controls,
                         LootType loot,EventForm event,RegionForm region,io.github.chronicle.core.books.BookDefinition book,LoreForm lore,RoleForm role,RoleAssignmentForm roleAssignment,AreaForm area,DepartureForm departure) {
        public Detail(String id,String title,String description,List<Fact> facts,List<Control> controls,LootType loot,EventForm event,RegionForm region,io.github.chronicle.core.books.BookDefinition book,LoreForm lore,RoleForm role,RoleAssignmentForm roleAssignment){this(id,title,description,facts,controls,loot,event,region,book,lore,role,roleAssignment,null,null);}
        public Detail(String id,String title,String description,List<Fact> facts,List<Control> controls,LootType loot,EventForm event,RegionForm region,io.github.chronicle.core.books.BookDefinition book,LoreForm lore){this(id,title,description,facts,controls,loot,event,region,book,lore,null,null,null,null);}
        public Detail(String id,String title,String description,List<Fact> facts,List<Control> controls,LootType loot,EventForm event,RegionForm region,io.github.chronicle.core.books.BookDefinition book){this(id,title,description,facts,controls,loot,event,region,book,null,null,null,null,null);}
        public Detail(String id,String title,String description,List<Fact> facts,List<Control> controls,LootType loot,EventForm event,RegionForm region){this(id,title,description,facts,controls,loot,event,region,null,null,null,null,null,null);}
        public Detail {text(id,160);text(title,160);text(description,4096);facts=List.copyOf(facts);controls=List.copyOf(controls);if(facts.size()>96||controls.size()>32)throw new IllegalArgumentException("Detail exceeds bounds");}
    }
    public record AreaForm(String id,String name,long revision,String dimension,String first,String second,boolean startSaved,boolean endSaved,boolean stale,boolean busy,List<String> consumers,String problem,String operationId,String operationStage) { public AreaForm {text(id,160);text(name,160);text(dimension,160);text(first,96);text(second,96);text(problem,512);text(operationId,160);text(operationStage,64);consumers=List.copyOf(consumers);if(revision<0||consumers.size()>132)throw new IllegalArgumentException("Invalid area form");} }
    public record DepartureForm(String id,String name,long revision,String anchorId,long anchorRevision,String regionId,long regionRevision,String areaId,String areaState,String firstText,String finalText,boolean creating,boolean used,List<Entry> blockSites,List<Entry> regions,List<Entry> areas) { public DepartureForm {text(id,160);text(name,160);text(anchorId,160);text(regionId,160);text(areaId,160);text(areaState,32);text(firstText,4096);text(finalText,4096);blockSites=List.copyOf(blockSites);regions=List.copyOf(regions);areas=List.copyOf(areas);if(revision<0||anchorRevision<0||regionRevision<0)throw new IllegalArgumentException("Invalid departure form");} }
    public record RoleForm(io.github.chronicle.core.roles.PlayerRole definition,List<Entry> points,List<Entry> books,boolean creating) {
        public RoleForm {Objects.requireNonNull(definition);points=List.copyOf(points);books=List.copyOf(books);if(points.size()>512||books.size()>256)throw new IllegalArgumentException("Role choices exceed bounds");}
    }
    /** Public choice data contains no starting supplies, private books or destination metadata. */
    public record RoleChoice(String id,String name,String description,int assigned,int capacity,boolean available) {
        public RoleChoice{text(id,160);text(name,160);text(description,2048);if(assigned<0||capacity<1||capacity>512)throw new IllegalArgumentException("Invalid role capacity");}
    }
    public record RoleAssignmentForm(String playerId,String roleId,String roleName,boolean canChoose,boolean canAssign,boolean started,List<RoleChoice> choices,String pendingRoleId) {
        public RoleAssignmentForm(String playerId,String roleId,String roleName,boolean canChoose,boolean canAssign,boolean started,List<RoleChoice> choices){this(playerId,roleId,roleName,canChoose,canAssign,started,choices,"");}
        public RoleAssignmentForm{text(playerId,36);text(roleId,160);text(roleName,160);text(pendingRoleId,160);choices=List.copyOf(choices);if(choices.size()>129)throw new IllegalArgumentException("Too many role choices");}
    }
    public record BookSummary(String id,String title,String author,String rule,boolean issued,boolean found) {
        public BookSummary{text(id,160);text(title,64);text(author,160);text(rule,32);}
    }
    public record KeepForm(String bookId,String bookTitle,String tableId,String tableLabel,String status,boolean active,
                           boolean requiresReview,boolean overrideNeeded,boolean found,boolean canAct,String reason,String location) {
        public KeepForm{text(bookId,160);text(bookTitle,64);text(tableId,160);text(tableLabel,160);text(status,512);text(reason,512);text(location,256);}
    }
    public record BookStatus(boolean found,String finderName,String finderUuid,String foundAt,boolean finderOnline) {
        public BookStatus{text(finderName,160);text(finderUuid,36);text(foundAt,64);if(!found&&(!finderName.isEmpty()||!finderUuid.isEmpty()||!foundAt.isEmpty()||finderOnline))throw new IllegalArgumentException("Unfound book cannot have discovery details");}
    }
    public record BookGiveForm(String bookId,String bookTitle,List<Entry> players,String targetId,String targetName,boolean canGive,String reason) {
        public BookGiveForm{text(bookId,160);text(bookTitle,64);text(targetId,36);text(targetName,160);text(reason,512);players=List.copyOf(players);if(players.size()>512)throw new IllegalArgumentException("Too many book recipients");}
    }
    public record LoreForm(List<BookSummary> refs,List<Entry> tableUses,List<Entry> chestUses,io.github.chronicle.core.books.BookChance direct,KeepForm keep,BookStatus status,BookGiveForm give) {
        public LoreForm(List<BookSummary> refs,List<Entry> tableUses,List<Entry> chestUses,io.github.chronicle.core.books.BookChance direct,KeepForm keep){this(refs,tableUses,chestUses,direct,keep,null,null);}
        public LoreForm{refs=List.copyOf(refs);tableUses=List.copyOf(tableUses);chestUses=List.copyOf(chestUses);if(refs.size()>27||tableUses.size()>12||chestUses.size()>12)throw new IllegalArgumentException("Lore form exceeds bounds");}
    }
    public record Picker(String kind,String query,int page,int pages,List<Entry> entries) {
        public Picker {text(kind,32);text(query,80);entries=List.copyOf(entries);if(page<0||pages<1||page>=pages||entries.size()>12)throw new IllegalArgumentException("Invalid picker page");}
    }
    /** Bounded typed policy data; the dialog never parses diagnostic prose for authority. */
    public record BlockLimitType(String typeId,String blockId,String label,String description,int count,int limit,boolean fixed) {
        public BlockLimitType {
            text(typeId,160);text(blockId,160);text(label,80);text(description,256);
            if(typeId.isBlank()||blockId.isBlank()||label.isBlank()||count<0||limit<0||limit>1024)
                throw new IllegalArgumentException("Invalid block-limit type");
        }
    }
    public record BlockLimitsForm(long policyRevision,boolean ready,String problem,boolean editable,String editReason,
                                  String selectedType,List<BlockLimitType> types) {
        public BlockLimitsForm {
            text(problem,512);text(editReason,512);text(selectedType,160);types=List.copyOf(types);
            if(policyRevision<0||types.size()>12||types.stream().map(BlockLimitType::typeId).distinct().count()!=types.size()
                    ||(!selectedType.isBlank()&&types.stream().noneMatch(type->type.typeId().equals(selectedType))))
                throw new IllegalArgumentException("Invalid block-limit form");
            if(editable&&!ready)throw new IllegalArgumentException("Unavailable block-limit form cannot be edited");
        }
        public BlockLimitType selected() { return types.stream().filter(type->type.typeId().equals(selectedType)).findFirst().orElse(null); }
    }
    public record View(long token,Page page,int index,int pages,String filter,String selected,String phase,int session,
                       boolean editing,boolean editable,boolean operator,String hold,String clock,String message,boolean success,
                       List<Entry> entries,Detail detail,Picker picker,boolean close,BlockLimitsForm blockLimits,EventEditorForm eventEditor) {
        public View(long token,Page page,int index,int pages,String filter,String selected,String phase,int session,
                    boolean editing,boolean editable,boolean operator,String hold,String clock,String message,boolean success,
                    List<Entry> entries,Detail detail,Picker picker,boolean close,BlockLimitsForm blockLimits) {
            this(token,page,index,pages,filter,selected,phase,session,editing,editable,operator,hold,clock,message,success,
                    entries,detail,picker,close,blockLimits,null);
        }
        /** Existing callers remain valid when no block-policy dialog data is needed. */
        public View(long token,Page page,int index,int pages,String filter,String selected,String phase,int session,
                    boolean editing,boolean editable,boolean operator,String hold,String clock,String message,boolean success,
                    List<Entry> entries,Detail detail,Picker picker,boolean close) {
            this(token,page,index,pages,filter,selected,phase,session,editing,editable,operator,hold,clock,message,success,
                    entries,detail,picker,close,null,null);
        }
        public View {
            Objects.requireNonNull(page);text(filter,32);text(selected,160);text(phase,32);text(hold,512);text(clock,256);text(message,4096);entries=List.copyOf(entries);
            if(token<1||index<0||pages<1||index>=pages||session<0||entries.size()>12)throw new IllegalArgumentException("Invalid menu view");
            if(blockLimits!=null&&page!=Page.SETUP)throw new IllegalArgumentException("Block-limit forms belong to Game setup");
            if(eventEditor!=null&&page!=Page.EVENTS)throw new IllegalArgumentException("Event editors belong to Events");
            if(!operator) {
                if(eventEditor!=null||blockLimits!=null||page!=Page.PLAYERS||editing||editable||!hold.isBlank()||!clock.isBlank()||picker!=null||entries.size()>1||entries.stream().anyMatch(e->!e.id().equals(selected)))throw new IllegalArgumentException("Public player view contains management state");
                if(detail!=null&&(detail.role()!=null||detail.area()!=null||detail.departure()!=null||detail.loot()!=null||detail.event()!=null||detail.region()!=null||detail.book()!=null||detail.lore()!=null||detail.controls().stream().anyMatch(c->switch(c.action()){case CARD_OPEN,PARTICIPANT_ADD,PARTICIPANT_REMOVE,LEAVE,ROLE_CHOICE_OPEN,ROLE_CHOOSE->false;default->true;})))throw new IllegalArgumentException("Public player view contains management controls");
                if(detail!=null&&detail.roleAssignment()!=null&&(detail.roleAssignment().canAssign()||!detail.roleAssignment().playerId().equals(selected)||!detail.roleAssignment().pendingRoleId().isEmpty()))throw new IllegalArgumentException("Public role form contains management data");
            }
        }
        public boolean sameControls(View other) {return other!=null&&token==other.token&&page==other.page&&index==other.index&&pages==other.pages&&filter.equals(other.filter)&&selected.equals(other.selected)&&phase.equals(other.phase)&&session==other.session&&editing==other.editing&&editable==other.editable&&operator==other.operator&&hold.equals(other.hold)&&entries.equals(other.entries)&&Objects.equals(detail,other.detail)&&Objects.equals(picker,other.picker)&&Objects.equals(blockLimits,other.blockLimits)&&Objects.equals(eventEditor,other.eventEditor);}
    }
    private static void text(String value,int max) {if(value==null||value.length()>max||value.indexOf('\0')>=0)throw new IllegalArgumentException("Menu text exceeds bounds");}
}
