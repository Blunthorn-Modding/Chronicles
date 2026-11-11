package io.github.chronicle.core.scenario;

import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.roles.PlayerRole;

/** Bounded operator-only summary; the portable definitions themselves never cross the menu wire. */
public record ScenarioExportPreview(String providerId,String title,int roles,int books,int lootTables,int events,
                                    int strippedRoleBindings,String digest,boolean available,String reason,int encounterGroups) {
    public ScenarioExportPreview(String providerId,String title,int roles,int books,int lootTables,int events,int strippedRoleBindings,String digest,boolean available,String reason) {
        this(providerId,title,roles,books,lootTables,events,strippedRoleBindings,digest,available,reason,0);
    }
    public ScenarioExportPreview {
        if(providerId==null||!ObservationCatalog.NAMESPACED_ID.matcher(providerId).matches())throw new IllegalArgumentException("Invalid scenario provider ID");
        text(title,160);text(digest,64);text(reason,512);
        if(title.isBlank()||title.codePoints().anyMatch(Character::isISOControl)||roles<0||roles>PlayerRole.MAX_ROLES||books<0||books>ScenarioContent.MAX_BOOKS||lootTables<0||lootTables>ScenarioContent.MAX_LOOT_TABLES||events<0||events>EventTemplate.MAX_EVENTS||strippedRoleBindings<0||strippedRoleBindings>PlayerRole.MAX_ROLES*2||encounterGroups<0||encounterGroups>ScenarioContent.MAX_ENCOUNTER_GROUPS)throw new IllegalArgumentException("Invalid scenario preview counts");
        if(!digest.isEmpty()&&!digest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid scenario preview digest");
        if(available&&(!reason.isEmpty()||(!digest.isEmpty()&&digest.length()!=64)))throw new IllegalArgumentException("Invalid available scenario preview");
    }
    private static void text(String value,int limit){if(value==null||value.length()>limit||value.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid scenario preview text");}
}
