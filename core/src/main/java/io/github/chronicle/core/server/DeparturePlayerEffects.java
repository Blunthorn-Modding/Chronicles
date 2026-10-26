package io.github.chronicle.core.server;

import io.github.chronicle.core.departures.DepartureOperation;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.UUID;

/** Per-passenger save receipts allow committed passengers to reconnect at different times. */
final class DeparturePlayerEffects {
    private DeparturePlayerEffects(){}
    static void acknowledge(MinecraftServer server,DepartureOperation operation,UUID player)throws IOException{
        if(!operation.onlineParticipants().contains(player))throw new IllegalArgumentException("Passenger is outside the committed roster");
        Path data=server.getSavePath(WorldSavePath.PLAYERDATA).resolve(player+".dat");
        if(!Files.isRegularFile(data,LinkOption.NOFOLLOW_LINKS)||Files.size(data)>8L*1024*1024)throw new IOException("Saved departure passenger data is unavailable: "+player);
        var nbt=NbtIo.readCompressed(data,NbtSizeTracker.of(32L*1024*1024));
        if(nbt.getInt("playerGameType").orElse(-1)!=GameMode.SPECTATOR.getIndex())throw new IOException("Saved passenger is not Spectator: "+player);
        AreaEffectFiles.put(path(server,operation,player),evidence(operation,player));
    }
    static boolean acknowledged(MinecraftServer server,DepartureOperation operation,UUID player)throws IOException{
        Path receipt=path(server,operation,player);if(!Files.exists(receipt,LinkOption.NOFOLLOW_LINKS))return false;
        if(!Files.isRegularFile(receipt,LinkOption.NOFOLLOW_LINKS)||Files.size(receipt)>512||!Arrays.equals(Files.readAllBytes(receipt),evidence(operation,player)))throw new IOException("Departure passenger receipt is invalid and was preserved: "+player);
        return true;
    }
    private static Path path(MinecraftServer server,DepartureOperation op,UUID player){return server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/departure-player-effects").resolve(op.operationId()+"."+player+".receipt");}
    private static byte[] evidence(DepartureOperation op,UUID player){return ("chronicle-departure-spectator-v1\n"+op.operationId()+"\n"+player+"\n"+op.definition().id()+"\n").getBytes(StandardCharsets.UTF_8);}
}
