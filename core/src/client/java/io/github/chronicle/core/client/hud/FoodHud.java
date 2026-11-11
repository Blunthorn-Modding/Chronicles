package io.github.chronicle.core.client.hud;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.stats.FoodCapacity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Replaces only the vanilla food layer while authoritative food capacity differs from twenty.
 * The ordinary vanilla layer remains untouched for every normal-capacity player. A configured
 * optional editor element takes over only after it actually renders.
 */
public final class FoodHud {
    public static final Identifier ID = Identifier.of(ChronicleCore.MOD_ID, "expanded_food");
    private static boolean registered;

    private FoodHud() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, vanilla -> (context, tickCounter) -> {
            // The optional element already draws all food icons. It must suppress
            // vanilla, rather than falling through to the native ten-slot renderer.
            if (MinecraftClient.getInstance().player != null
                    && FoodCapacity.capacity(MinecraftClient.getInstance().player) != 20 && fancyElementRenderedRecently()) return;
            if (shouldRenderCustom()) {
                ChronicleFoodRenderer.render(context, MinecraftClient.getInstance().player);
            } else {
                vanilla.render(context, tickCounter);
            }
        });
        // Replacing the vanilla provider avoids a second ten-pixel contribution. A second
        // food row needs twenty pixels so air/mount bars above it do not overlap it.
        HudStatusBarHeightRegistry.addRight(VanillaHudElements.FOOD_BAR,
                player -> statusBarHeight(player));
        registerFancyElementIfAvailable();
    }

    static boolean shouldRenderCustom() {
        MinecraftClient client = MinecraftClient.getInstance();
        return shouldRenderCustom(client.player);
    }

    private static boolean shouldRenderCustom(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player == null || client.options.hudHidden || player.isSpectator() || player.hasVehicle()) return false;
        if (FoodCapacity.capacity(player) == 20) return false;
        return !fancyElementRenderedRecently();
    }

    private static int statusBarHeight(net.minecraft.entity.player.PlayerEntity player) {
        ClientPlayerEntity clientPlayer = MinecraftClient.getInstance().player;
        if (player != clientPlayer) return 0;
        if (shouldRenderCustom(clientPlayer)) return ChronicleFoodRenderer.height(player);
        // Vanilla handles ordinary capacity. A saved editable element chooses its own
        // position and therefore must not leave a phantom right-side row behind.
        return FoodCapacity.capacity(player) == 20 && !MinecraftClient.getInstance().options.hudHidden ? 10 : 0;
    }

    private static void registerFancyElementIfAvailable() {
        if (!FabricLoader.getInstance().isModLoaded("fancymenu") || !FabricLoader.getInstance().isModLoaded("spiffyhud")) return;
        try {
            Class.forName("io.github.chronicle.core.client.hud.ChronicleFancyFood")
                    .getMethod("register").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            ChronicleCore.LOGGER.warn("Chronicle expanded-food editor element is unavailable; using the native HUD fallback", failure);
        }
    }

    private static boolean fancyElementRenderedRecently() {
        if (!FabricLoader.getInstance().isModLoaded("fancymenu") || !FabricLoader.getInstance().isModLoaded("spiffyhud")) return false;
        try {
            return (boolean) Class.forName("io.github.chronicle.core.client.hud.ChronicleFancyFood")
                    .getMethod("renderedRecently").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

}
