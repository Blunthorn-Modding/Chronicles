package io.github.chronicle.lastcouncil;

import io.github.chronicle.core.server.ScenarioContentRuntime;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.server.CouncilRuntime;
import net.fabricmc.api.ModInitializer;

public final class LastCouncil implements ModInitializer {
    public static final String SCENARIO="last_council:scenario", MECHANICS="last_council:mechanics";
    @Override public void onInitialize(){
        ScenarioContentRuntime.registerScenario(SCENARIO,"The Last Council",MECHANICS);
        CouncilPayloads.register();
        CouncilRuntime.register();
    }
}
