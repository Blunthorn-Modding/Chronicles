package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.core.client.OwnCardScreens;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class LastCouncilClient implements ClientModInitializer {
    private static boolean active;
    @Override public void onInitializeClient(){
        CouncilSmoke.register();
        OwnCardScreens.register("last_council:personal",()->active,(client,card)->{
            if(client.currentScreen instanceof PersonalScreen screen)screen.acceptCard(card);
            else client.setScreen(new PersonalScreen(card));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->active=false);
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.Capability.ID,(view,context)->context.client().execute(()->active=view.active()));
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.PersonalView.ID,(view,context)->context.client().execute(()->{CouncilSmoke.personal(view);if(context.client().currentScreen instanceof PersonalScreen screen)screen.accept(view);}));
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.AdminView.ID,(view,context)->context.client().execute(()->{
            CouncilSmoke.admin(view);var client=context.client();if(view.opening())client.setScreen(new HouseSetupScreen(view));else if(client.currentScreen instanceof HouseSetupScreen screen)screen.accept(view);
        }));
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.Notice.ID,(notice,context)->context.client().execute(()->{
            CouncilSmoke.notice(notice);if(context.client().currentScreen instanceof PersonalScreen screen)screen.notice(notice);
            else if(context.client().currentScreen instanceof HouseSetupScreen screen)screen.notice(notice);
        }));
    }
}
