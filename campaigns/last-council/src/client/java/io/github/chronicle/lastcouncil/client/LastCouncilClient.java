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
        CouncilCatalogueClient.register();
        PaperCopiesClient.register();
        TreatyClient.register(()->active);
        ConflictClient.register();
        RebellionClient.register();
        ForeignClient.register();
        ConflictNativeProbe.register();
        ComplicationSettingsClient.register();
        BanquetClient.register();
        BanquetSmoke.register();
        CouncilSmoke.register();
        CouncilBallotsSmoke.register();
        CouncilSuccessionSmoke.register();
        TreatySmoke.register();
        CouncilEditorSmoke.register();
        PaperCopiesSmoke.register();
        BallotAccess.register(()->active);
        ProceedingsAccess.register(()->active&&chancellor);
        OwnCardScreens.register("last_council:personal",()->active,(client,card)->{
            if(client.currentScreen instanceof PersonalScreen screen)screen.acceptCard(card);
            else client.setScreen(new PersonalScreen(card));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{active=false;chancellor=false;});
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.Capability.ID,(view,context)->context.client().execute(()->{active=view.active();chancellor=view.chancellor();}));
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.PersonalView.ID,(view,context)->context.client().execute(()->{CouncilSmoke.personal(view);CouncilBallotsSmoke.personal(view);PaperCopiesSmoke.personal(view);if(context.client().currentScreen instanceof PersonalScreen screen)screen.accept(view);}));
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.AdminView.ID,(view,context)->context.client().execute(()->{
            CouncilSmoke.admin(view);CouncilBallotsSmoke.admin(view);CouncilSuccessionSmoke.admin(view);CouncilEditorSmoke.admin(view);PaperCopiesSmoke.admin(view);var client=context.client();if(view.opening())client.setScreen(new HouseSetupScreen(view));else if(client.currentScreen instanceof HouseSetupScreen screen)screen.accept(view);
        }));
        ClientPlayNetworking.registerGlobalReceiver(ProceedingsPayloads.View.ID,(view,context)->context.client().execute(()->{
            ConflictNativeProbe.proceedings(view);CouncilBallotsSmoke.view(view);CouncilSuccessionSmoke.view(view);var client=context.client();
            if(view.opening()){if(view.mode()==ProceedingsPayloads.Mode.PROCEEDINGS)ProceedingsAccess.openResponse(view,client);else client.setScreen(new CouncilBusinessScreen(view,null));}
            else if(client.currentScreen instanceof CouncilBusinessScreen screen)screen.accept(view);
            else if(client.currentScreen instanceof MotionDraftScreen draft&&draft.ownerBusiness()!=null)draft.ownerBusiness().accept(view);
        }));
        ClientPlayNetworking.registerGlobalReceiver(ProceedingsPayloads.Vote.ID,(view,context)->context.client().execute(()->{
            ConflictNativeProbe.vote(view);CouncilBallotsSmoke.vote(view);CouncilSuccessionSmoke.vote(view);var client=context.client();
            if(view.opening())BallotAccess.openResponse(view,client);
            else if(client.currentScreen instanceof BallotScreen screen)screen.accept(view);
        }));
        ClientPlayNetworking.registerGlobalReceiver(CouncilPayloads.Notice.ID,(notice,context)->context.client().execute(()->{
            ConflictNativeProbe.notice(notice);PaperCopiesSmoke.notice(notice);
            if(PaperCopiesClient.notice(notice,context.client()))return;
            CouncilSmoke.notice(notice);CouncilBallotsSmoke.notice(notice);CouncilSuccessionSmoke.notice(notice);if(context.client().currentScreen instanceof PersonalScreen screen)screen.notice(notice);
            else if(context.client().currentScreen instanceof HouseSetupScreen screen)screen.notice(notice);
            else if(context.client().currentScreen instanceof CouncilBusinessScreen screen)screen.notice(notice);
            else if(context.client().currentScreen instanceof MotionDraftScreen draft&&draft.ownerBusiness()!=null)draft.ownerBusiness().notice(notice);
            else if(context.client().currentScreen instanceof BallotScreen screen)screen.notice(notice);
        }));
    }
}
