package io.github.chronicle.core.menu;

import io.github.chronicle.core.loot.LootType;
import java.util.List;
import java.util.Objects;

/** Private operator presentation. Every mutation is still authorized and validated on the server. */
public final class CoreMenuModel {
    private CoreMenuModel() { }
    public enum Page { SESSION, PLAYERS, EVENTS, CHESTS, LOOT_TABLES, REGIONS, LOCATIONS, SETUP }
    public enum Action {
        OPEN, REFRESH, OPTIONS, CLOSE, EDIT_ON, EDIT_OFF, SESSION_START, SESSION_PAUSE, SESSION_RESUME, SESSION_END,
        PARTICIPANT_ADD, PARTICIPANT_REMOVE, LEAVE, CARD_OPEN,
        PLAYER_MOVE_REVIEW, PLAYER_MOVE, STARTING_SET, STARTING_OFF, STARTING_RESET, RESPAWN_SET, RESPAWN_OFF,
        EVENT_PREVIEW, EVENT_AUDIO, EVENT_CHECK, EVENT_REVIEW, EVENT_RUN, EVENT_SAVE, EVENT_DISABLE_AUTO, EVENT_REMOVE, EVENT_RESET,
        CONDITION_SET, CHEST_BIND, CHEST_RETYPE, CHEST_UNBIND, TABLE_OPEN, SHOW, LOOT_SAVE, LOOT_RELOAD,
        REGION_NEW, REGION_EDIT, REGION_POSITION, REGION_CONTINUE, REGION_SAVE, REGION_CREATE,
        POINT_CREATE, POINT_UPDATE, BLOCK_CREATE, BLOCK_UPDATE, SITE_DELETE, SETUP_ASSIGN, SETUP_CLEAR, TOOL_ACTION,
        REGION_ASSIGN, REGION_UNASSIGN, POINT_ASSIGN, POINT_UNASSIGN, BLOCK_LIMITS, BLOCK_LIMIT_SET, MANAGED_SHOW,
        MANUAL_REFERENCES, SESSION_BUILD_ON, SESSION_BUILD_OFF
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
                         LootType loot,EventForm event,RegionForm region) {
        public Detail {text(id,160);text(title,160);text(description,4096);facts=List.copyOf(facts);controls=List.copyOf(controls);if(facts.size()>96||controls.size()>32)throw new IllegalArgumentException("Detail exceeds bounds");}
    }
    public record Picker(String kind,String query,int page,int pages,List<Entry> entries) {
        public Picker {text(kind,32);text(query,80);entries=List.copyOf(entries);if(page<0||pages<1||page>=pages||entries.size()>12)throw new IllegalArgumentException("Invalid picker page");}
    }
    public record View(long token,Page page,int index,int pages,String filter,String selected,String phase,int session,
                       boolean editing,boolean editable,boolean operator,String hold,String clock,String message,boolean success,
                       List<Entry> entries,Detail detail,Picker picker,boolean close) {
        public View {
            Objects.requireNonNull(page);text(filter,32);text(selected,160);text(phase,32);text(hold,512);text(clock,256);text(message,4096);entries=List.copyOf(entries);
            if(token<1||index<0||pages<1||index>=pages||session<0||entries.size()>12)throw new IllegalArgumentException("Invalid menu view");
            if(!operator) {
                if(page!=Page.PLAYERS||editing||editable||!hold.isBlank()||!clock.isBlank()||picker!=null||entries.size()>1||entries.stream().anyMatch(e->!e.id().equals(selected)))throw new IllegalArgumentException("Public player view contains management state");
                if(detail!=null&&(detail.loot()!=null||detail.event()!=null||detail.region()!=null||detail.controls().stream().anyMatch(c->switch(c.action()){case CARD_OPEN,PARTICIPANT_ADD,PARTICIPANT_REMOVE,LEAVE->false;default->true;})))throw new IllegalArgumentException("Public player view contains management controls");
            }
        }
        public boolean sameControls(View other) {return other!=null&&token==other.token&&page==other.page&&index==other.index&&pages==other.pages&&filter.equals(other.filter)&&selected.equals(other.selected)&&phase.equals(other.phase)&&session==other.session&&editing==other.editing&&editable==other.editable&&operator==other.operator&&hold.equals(other.hold)&&entries.equals(other.entries)&&Objects.equals(detail,other.detail)&&Objects.equals(picker,other.picker);}
    }
    private static void text(String value,int max) {if(value==null||value.length()>max||value.indexOf('\0')>=0)throw new IllegalArgumentException("Menu text exceeds bounds");}
}
