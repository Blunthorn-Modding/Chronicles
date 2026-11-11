package io.github.chronicle.quietbelow.testing;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.CampaignEvent;
import io.github.chronicle.core.campaign.EventOutcome;
import java.nio.file.*;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.WorldSavePath;

/** Opt-in fixture for the old bounded-stage regression; not scenario content. */
public final class StageEventFixture {
    private StageEventFixture(){}
    public static void register(){
        if(!"true".equals(System.getenv("CHRONICLE_STAGE_TRIAL")))return;
        String configured=System.getenv("CHRONICLE_STAGE_SERVER_DIR");
        if(configured==null)throw new IllegalStateException("Stage fixture root is required");
        Path root=Path.of(configured).toAbsolutePath().normalize();
        if(root.getParent()==null||!root.getParent().getFileName().toString().equals("test-runs")
                ||!root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")
                ||!Files.isRegularFile(root.resolve(".stage-events-fixture")))throw new IllegalStateException("Unsafe stage fixture root");
        ServerLifecycleEvents.SERVER_STARTING.register(server->{
            if(!server.isDedicated()||!server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent().equals(root))
                throw new IllegalStateException("Unsafe stage fixture server");
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            if(server.isOnlineMode()||server.getServerPort()!=25575||!"127.0.0.1".equals(server.getServerIp()))
                throw new IllegalStateException("Unsafe stage fixture binding");
        });
        ChronicleCore.events().register(new CampaignEvent("test:marker_clue","Disposable marker clue proof.",true,Map.of(),
                state->new EventOutcome(Map.of("quiet_below:fragment_revealed",true),"A new trace can be observed at Resonance Markers, with or without an Echo Lens.")));
    }
}
