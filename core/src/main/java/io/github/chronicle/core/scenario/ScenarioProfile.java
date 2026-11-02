package io.github.chronicle.core.scenario;

import io.github.chronicle.core.play.ObservationCatalog;
import java.util.*;

/** Portable configuration for a separate server using installed mechanics. Never contains map/progress data. */
public record ScenarioProfile(UUID identity,String title,Set<String> mechanics,ScenarioContent content) {
    public ScenarioProfile {
        Objects.requireNonNull(identity,"Scenario identity is required");Objects.requireNonNull(content,"Scenario content is required");
        if(title==null||title.isBlank()||title.length()>160||title.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Choose a scenario name of 1-160 plain characters.");
        if(mechanics==null||mechanics.size()>16)throw new IllegalArgumentException("At most 16 mechanics providers are supported.");
        TreeSet<String> checked=new TreeSet<>();for(String id:mechanics){if(id==null||id.length()>160||!ObservationCatalog.NAMESPACED_ID.matcher(id).matches())throw new IllegalArgumentException("Invalid mechanics provider ID.");checked.add(id);}
        mechanics=Collections.unmodifiableSet(checked);
    }
    public static ScenarioProfile empty(String id,String title,Set<String> mechanics){return new ScenarioProfile(UUID.randomUUID(),title,mechanics,new ScenarioContent(id,Map.of(),Map.of(),Map.of(),Map.of(),Map.of()));}
    public ScenarioProfile withContent(ScenarioContent value){if(!content.scenarioId().equals(value.scenarioId()))throw new IllegalArgumentException("Scenario identity changed.");return new ScenarioProfile(identity,title,mechanics,value);}
}
