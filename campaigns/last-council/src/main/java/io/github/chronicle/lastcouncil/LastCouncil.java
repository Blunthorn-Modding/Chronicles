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
        io.github.chronicle.lastcouncil.network.CataloguePayloads.register();
        io.github.chronicle.lastcouncil.network.ProceedingsPayloads.register();
        CouncilRuntime.register();
        io.github.chronicle.lastcouncil.server.CouncilCatalogueRuntime.register();
        io.github.chronicle.lastcouncil.server.ProceedingsRuntime.register();
        if(io.github.chronicle.core.server.PackagedScenarios.defaultsFor(SCENARIO))
            io.github.chronicle.core.scenario.ScenarioEventTemplates.register(io.github.chronicle.lastcouncil.content.CouncilDefaults.meetingCall());
    }
}
