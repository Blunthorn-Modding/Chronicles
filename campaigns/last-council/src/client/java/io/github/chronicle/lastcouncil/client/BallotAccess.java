package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.core.client.ChronicleCoreClient;
import io.github.chronicle.core.client.TextInputGuard;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads.*;
import java.util.*;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import io.github.chronicle.core.ballots.BallotStatus;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

/** A new vote is a notice, never an unsolicited replacement for another workstation. */
final class BallotAccess {
    private static final KeyBinding OPEN=KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.last_council.ballot.open",InputUtil.Type.KEYSYM,InputUtil.GLFW_KEY_R,ChronicleCoreClient.KEY_CATEGORY));
    private static UUID lastNotice, currentHint;
    private static int cooldown, tick;
    private record Opening(Screen expected, UUID ballot, int expires) { }
    private static final Map<UUID,Opening> openings=new HashMap<>();
    private static final Set<UUID> terminal=new HashSet<>();
    private static BooleanSupplier active;
    private BallotAccess() { }
    static KeyBinding binding() { return OPEN; }
    static UUID currentHint() { return currentHint; }
    static void register(BooleanSupplier enabled) {
        active=enabled;
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            tick++;
            openings.values().removeIf(o->o.expires()<tick);
            if(cooldown>0)cooldown--;
            while(OPEN.wasPressed())open(client);
        });
        ScreenEvents.AFTER_INIT.register((client,screen,width,height)->
            ScreenKeyboardEvents.allowKeyPress(screen).register((current,input)->{
                if(OPEN.matchesKey(input)&&open(client))return false;
                return true;
            }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{lastNotice=null;currentHint=null;cooldown=0;openings.clear();terminal.clear();tick=0;});
        ClientPlayNetworking.registerGlobalReceiver(Signal.ID,(signal,context)->context.client().execute(()->{
            MinecraftClient client=context.client();
            if(!signal.open()) { terminal.add(signal.ballot());if(signal.ballot().equals(currentHint))currentHint=null;return; }
            currentHint=signal.ballot();
            if(client.player==null||signal.ballot().equals(lastNotice))return;
            lastNotice=signal.ballot();
            Text notice=OPEN.isUnbound()?Text.translatable("message.last_council.ballot.command",signal.title(),"/chronicle council vote")
                    :Text.translatable("message.last_council.ballot.open",signal.title(),OPEN.getBoundKeyLocalizedText(),"/chronicle council vote");
            client.player.sendMessage(notice,false);
        }));
    }
    private static boolean open(MinecraftClient client) {
        if(active==null||!active.getAsBoolean()||client.player==null||cooldown>0||!ClientPlayNetworking.canSend(Request.ID))return false;
        if(client.currentScreen instanceof BallotScreen||client.currentScreen instanceof ConfirmScreen)return false;
        if(TextInputGuard.editingText(client.currentScreen))return false;
        Screen prior=client.currentScreen;
        if(prior!=null&&currentHint==null) {
            cooldown=10;
            client.player.sendMessage(Text.translatable("message.last_council.ballot.check","/chronicle council vote"),false);
            return true;
        }
        // Containers must return cursor/escrow normally. Pure editor screens are suspended
        // as the ballot's parent, so their unsaved draft is never closed or discarded.
        if(prior instanceof HandledScreen<?>) {
            prior.close();
            if(client.currentScreen!=null)return true;
        }
        cooldown=10;
        UUID request=UUID.randomUUID();expectOpening(request,client.currentScreen,CouncilPayloads.NONE);
        ClientPlayNetworking.send(new Request(request,Action.BALLOT,0,CouncilPayloads.NONE,"","","",false,false));
        return true;
    }
    static void expectOpening(UUID request,Screen expected,UUID ballot) {
        if(openings.size()>=32)openings.clear();
        openings.put(request,new Opening(expected,ballot,tick+200));
    }
    static boolean openResponse(Vote vote,MinecraftClient client) {
        Opening opening=openings.remove(vote.request());
        if(opening==null) {
            // Server command replies have no client request ID. Do not displace a
            // screen opened after the command was sent.
            if(!vote.request().equals(CouncilPayloads.NONE)||client.currentScreen!=null)return false;
        } else if(opening.expires()<tick||opening.expected()!=client.currentScreen
                ||!opening.ballot().equals(CouncilPayloads.NONE)&&!opening.ballot().equals(vote.ballot().id()))return false;
        if(vote.ballot().status()==BallotStatus.OPEN&&(terminal.contains(vote.ballot().id())
                ||currentHint!=null&&!currentHint.equals(vote.ballot().id())))return false;
        client.setScreen(new BallotScreen(vote,client.currentScreen));return true;
    }
}
