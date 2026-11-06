package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.core.client.OwnCardScreens;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class LastCouncilClient implements ClientModInitializer {
    private static boolean active,chancellor;
    @Override public void onInitializeClient(){
        CouncilSmoke.register();
        CouncilBallotsSmoke.register();
        CouncilSuccessionSmoke.register();
        BallotAccess.register(()->active);
        ProceedingsAccess.register(()->active&&chancellor);
        OwnCardScreens.register("last_council:personal",()->active,(client,card)->{
            if(client.currentScreen instanceof PersonalScreen screen)screen.acceptCard(card);
            else client.setScreen(new PersonalScreen(card));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{active=false;chancellor=false;});
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.Capability.ID,(view,context)->context.client().execute(()->{active=view.active();chancellor=view.chancellor();}));
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.PersonalView.ID,(view,context)->context.client().execute(()->{CouncilSmoke.personal(view);CouncilBallotsSmoke.personal(view);if(context.client().currentScreen instanceof PersonalScreen screen)screen.accept(view);}));
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.AdminView.ID,(view,context)->context.client().execute(()->{
            CouncilSmoke.admin(view);CouncilBallotsSmoke.admin(view);CouncilSuccessionSmoke.admin(view);var client=context.client();if(view.opening())client.setScreen(new HouseSetupScreen(view));else if(client.currentScreen instanceof HouseSetupScreen screen)screen.accept(view);
        }));
        ClientPlayNetworking.registerGlobalReceiver(ProceedingsPayloads.View.ID,(view,context)->context.client().execute(()->{
            CouncilBallotsSmoke.view(view);CouncilSuccessionSmoke.view(view);var client=context.client();
            if(view.opening()){if(view.mode()==ProceedingsPayloads.Mode.PROCEEDINGS)ProceedingsAccess.openResponse(view,client);else client.setScreen(new CouncilBusinessScreen(view,null));}
            else if(client.currentScreen instanceof CouncilBusinessScreen screen)screen.accept(view);
        }));
        ClientPlayNetworking.registerGlobalReceiver(ProceedingsPayloads.Vote.ID,(view,context)->context.client().execute(()->{
            CouncilBallotsSmoke.vote(view);CouncilSuccessionSmoke.vote(view);var client=context.client();
            if(view.opening())BallotAccess.openResponse(view,client);
            else if(client.currentScreen instanceof BallotScreen screen)screen.accept(view);
        }));
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.Notice.ID,(notice,context)->context.client().execute(()->{
            CouncilSmoke.notice(notice);CouncilBallotsSmoke.notice(notice);CouncilSuccessionSmoke.notice(notice);if(context.client().currentScreen instanceof PersonalScreen screen)screen.notice(notice);
            else if(context.client().currentScreen instanceof HouseSetupScreen screen)screen.notice(notice);
            else if(context.client().currentScreen instanceof CouncilBusinessScreen screen)screen.notice(notice);
            else if(context.client().currentScreen instanceof BallotScreen screen)screen.notice(notice);
        }));
    }
}
