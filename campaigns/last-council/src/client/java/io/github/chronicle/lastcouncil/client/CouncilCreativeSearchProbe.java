package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.core.client.TextInputGuard;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import org.lwjgl.glfw.GLFW;

/** Opt-in native regression probe; driven by the Council smoke harness. */
final class CouncilCreativeSearchProbe {
    private static CreativeInventoryScreen creative;
    private static TextFieldWidget search;
    private static InputUtil.Key previousProceedingsKey;
    private static Method selectTab;
    private CouncilCreativeSearchProbe() { }

    static void begin(MinecraftClient client) throws Exception {
        if (creative != null) throw new IllegalStateException("Creative search probe already running");
        if (client.player == null || client.world == null) throw new IllegalStateException("Creative search probe requires a connected player");
        previousProceedingsKey = KeyBindingHelper.getBoundKeyOf(ProceedingsAccess.binding());
        try {
            selectTab = CreativeInventoryScreen.class.getDeclaredMethod("setSelectedTab", ItemGroup.class);
            selectTab.setAccessible(true);
            creative = new CreativeInventoryScreen(client.player, client.player.getEntityWorld().getEnabledFeatures(), false);
            client.setScreen(creative);
            selectTab.invoke(creative, ItemGroups.getSearchGroup());
            search = creative.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast)
                    .findFirst().orElseThrow(() -> new IllegalStateException("Creative search widget missing"));
            require(search.visible && search.active && search.isFocused(), "Creative search is not visible, active and focused");
            require(TextInputGuard.editingText(creative), "Creative search was not recognized as text input");
            search.setText("");
            type(client, GLFW.GLFW_KEY_I, 'i');
            type(client, GLFW.GLFW_KEY_O, 'o');
            type(client, GLFW.GLFW_KEY_R, 'r');
            type(client, GLFW.GLFW_KEY_K, 'k');
            ProceedingsAccess.binding().setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_H));
            KeyBinding.updateKeysByCode();
            type(client, GLFW.GLFW_KEY_H, 'h');
            require("iorkh".equals(search.getText()), "Creative search query changed during key dispatch: " + search.getText());
        } catch (Exception | AssertionError failure) {
            cleanup(client);
            throw failure;
        }
    }

    static void finish(MinecraftClient client) throws Exception {
        if (creative == null) throw new IllegalStateException("Creative search probe was not started");
        try {
            require(client.currentScreen == creative, "A queued hotkey replaced Creative search");
            require("iorkh".equals(search.getText()), "Creative search query changed after ticks: " + search.getText());
            selectTab.invoke(creative, ItemGroups.getDefaultTab());
            require(!search.visible, "Creative search remained visible after switching tabs");
            search.setFocused(true);
            require(!TextInputGuard.editingText(creative), "Hidden Creative search blocked non-text inventory access");
        } finally {
            cleanup(client);
        }
    }

    private static void type(MinecraftClient client, int key, int character) {
        KeyInput input = new KeyInput(key, 0, 0);
        require(client.currentScreen == creative, "Creative search was replaced during key dispatch");
        require(ScreenKeyboardEvents.allowKeyPress(creative).invoker().allowKeyPress(creative, input), "A hotkey consumed a Creative search key");
        creative.keyPressed(input);
        KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(key));
        require(creative.charTyped(new CharInput(character, 0)), "Creative search did not accept a typed character");
    }

    private static void cleanup(MinecraftClient client) {
        if (previousProceedingsKey != null) {
            ProceedingsAccess.binding().setBoundKey(previousProceedingsKey);
            KeyBinding.updateKeysByCode();
        }
        if (creative != null && client.currentScreen == creative) creative.close();
        creative = null; search = null; selectTab = null; previousProceedingsKey = null;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
