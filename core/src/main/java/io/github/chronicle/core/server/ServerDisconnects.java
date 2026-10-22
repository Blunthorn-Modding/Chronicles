package io.github.chronicle.core.server;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Fabric can dispatch channelInactive on Netty; Chronicle state belongs to the server thread. */
public final class ServerDisconnects {
    private ServerDisconnects() { }
    public static void register(ServerPlayConnectionEvents.Disconnect callback){
        Objects.requireNonNull(callback);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->
                dispatch(server::isOnThread,server::execute,()->callback.onPlayDisconnect(handler,server)));
    }
    static void dispatch(BooleanSupplier onThread,Consumer<Runnable> enqueue,Runnable cleanup){
        if(onThread.getAsBoolean())cleanup.run();else enqueue.accept(cleanup);
    }
}
