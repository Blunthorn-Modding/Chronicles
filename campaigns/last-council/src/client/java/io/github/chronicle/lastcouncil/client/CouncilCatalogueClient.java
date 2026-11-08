package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.View;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

final class CouncilCatalogueClient {
    private CouncilCatalogueClient() { }
    static void register(){ClientPlayNetworking.registerGlobalReceiver(View.ID,(view,context)->context.client().execute(()->{
        CouncilBallotsSmoke.catalogue(view);
        var client=context.client();
        if(view.opening()&&client.currentScreen instanceof CoreMenuScreen)client.setScreen(new CouncilCatalogueScreen(view,client.currentScreen));
        else if(!view.opening()&&client.currentScreen instanceof CouncilCatalogueScreen screen)screen.accept(view);
    }));}
}
