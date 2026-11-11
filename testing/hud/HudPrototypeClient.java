package io.github.chronicle.testing.hud;

import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Disposable test mod, compiled separately; never shipped in Chronicle or a pack. */
public final class HudPrototypeClient implements ClientModInitializer {
    private static final String ADDRESS = "127.0.0.1:25575";
    private final List<String> checks = new ArrayList<>();
    private final long deadline = System.nanoTime() + 180_000_000_000L;
    private Path output;
    private Path layoutFile;
    private Object layout;
    private Screen editor;
    private int ticks;
    private int idleTicks;
    private int step;
    private boolean connecting;
    private boolean finished;

    @Override public void onInitializeClient() {
        String requested = System.getenv("CHRONICLE_HUD_SMOKE");
        if (requested == null || requested.isBlank()) return;
        output = Path.of(requested);
        System.out.println("Chronicle HUD helper registered: " + output);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void checkClientDefaults(MinecraftClient client) throws Exception {
        Object options=Class.forName("de.keksuccino.fancymenu.FancyMenu").getMethod("getOptions").invoke(null);
        Object welcome=options.getClass().getField("showWelcomeScreen").get(options);
        require(Boolean.FALSE.equals(welcome.getClass().getMethod("getValue").invoke(welcome)),"FancyMenu ignored the supplied option");
        checks.add("fresh FancyMenu startup reached Title without welcome bypass or documentation browser");
        Path preset=FabricLoader.getInstance().getConfigDir().resolve("chronicle/client-servers.json");
        String original=Files.readString(preset);
        var definitions=com.google.gson.JsonParser.parseString(original).getAsJsonObject().getAsJsonArray("servers");
        require(!definitions.isEmpty(),"review preset discovery omitted the running local review");
        var list=new net.minecraft.client.option.ServerList(client);list.loadFile();
        for(var entry:definitions)require(list.get(entry.getAsJsonObject().get("address").getAsString())!=null,"startup did not seed review server");
        String address=definitions.get(0).getAsJsonObject().get("address").getAsString();
        var first=list.get(address);list.remove(first);
        String alias=address.replace("127.0.0.1:","localhost:");
        var renamed=new ServerInfo("My renamed review",alias,ServerInfo.ServerType.OTHER);
        renamed.setResourcePackPolicy(ServerInfo.ResourcePackPolicy.DISABLED);
        list.add(renamed,true);
        list.add(new ServerInfo("My existing server","127.0.0.1:25670",ServerInfo.ServerType.OTHER),false);list.saveFile();
        io.github.chronicle.core.client.ClientServerPresets.apply(client);
        var merged=new net.minecraft.client.option.ServerList(client);merged.loadFile();
        require(merged.size()==definitions.size()+1,"merge created a duplicate or removed an existing server");
        require(merged.get(alias)!=null&&merged.get(alias).name.equals("My renamed review"),"merge renamed a user entry");
        require(merged.get(alias).getResourcePackPolicy()==ServerInfo.ResourcePackPolicy.DISABLED,"merge replaced resource-pack preference");
        require(merged.get("127.0.0.1:25670")!=null,"merge removed an unrelated entry");
        require(merged.get(definitions.size()-1).address.equals("127.0.0.1:25670")&&merged.get(definitions.size()).address.equals(alias),"merge changed native entry order");
        checks.add("native server-list merge preserves custom entries/names/pack policy and unhides loopback alias without duplicate");
        Path serverFile=FabricLoader.getInstance().getGameDir().resolve("servers.dat");byte[] before=Files.readAllBytes(serverFile);
        io.github.chronicle.core.client.ClientServerPresets.apply(client);
        require(java.util.Arrays.equals(before,Files.readAllBytes(serverFile)),"idempotent merge rewrote server list");
        try {
            Files.writeString(preset,"{invalid");io.github.chronicle.core.client.ClientServerPresets.apply(client);
            require(java.util.Arrays.equals(before,Files.readAllBytes(serverFile)),"bad preset overwrote native server list");
            Files.writeString(preset,"{\"version\":1,\"servers\":[{\"name\":\"Remote\",\"address\":\"example.invalid:25565\"}]}");
            io.github.chronicle.core.client.ClientServerPresets.apply(client);
            require(java.util.Arrays.equals(before,Files.readAllBytes(serverFile)),"remote preset modified native server list");
            Files.writeString(preset,original);Files.writeString(serverFile,"invalid existing list");
            io.github.chronicle.core.client.ClientServerPresets.apply(client);
            require(Files.readString(serverFile).equals("invalid existing list"),"corrupt native list was overwritten");
        } finally {Files.writeString(preset,original);Files.write(serverFile,before);}
        checks.add("idempotent startup and malformed preset/native-list recovery preserve existing bytes");
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() < deadline, "HUD prototype timed out at " + step);
            if (client.player == null) {
                require(client.currentScreen == null || !client.currentScreen.getClass().getName().equals("de.keksuccino.fancymenu.WelcomeScreen"),
                        "FancyMenu welcome appeared despite the shipped startup defaults");
                if (++idleTicks % 100 == 0)
                    System.out.println("Chronicle HUD waiting: screen=" + (client.currentScreen == null ? "none" : client.currentScreen.getClass().getName())
                            + ", overlay=" + (client.getOverlay() == null ? "none" : client.getOverlay().getClass().getName()));
                if (!connecting && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                    connecting = true;
                    ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS),
                            new ServerInfo("Chronicle disposable HUD fixture", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
                }
                return;
            }
            require(client.getCurrentServerEntry() != null && ADDRESS.equals(client.getCurrentServerEntry().address),
                    "Refusing to probe a non-fixture server");
            if (++ticks % 30 != 0) return;
            switch (step++) {
                case 0 -> {
                    for (String mod : List.of("spiffyhud", "fancymenu", "konkrete", "melody"))
                        require(FabricLoader.getInstance().isModLoaded(mod), "Missing " + mod);
                    require(client.options.getGuiScale().getValue() == 2, "GUI scale must remain 2");
                    checks.add("all four pinned HUD mods loaded alongside Chronicle; GUI scale 2");
                    checkClientDefaults(client);
                    client.player.networkHandler.sendChatCommand("attribute @s minecraft:max_health base set 22");
                    client.player.networkHandler.sendChatCommand("gamemode survival @s");
                }
                case 1 -> {
                    require(client.player.getMaxHealth() == 22, "Native extra heart fixture did not synchronize");
                    client.getToastManager().clear();
                    client.inGameHud.getChatHud().clear(false);
                    Class<?> overlayType = Class.forName("de.keksuccino.spiffyhud.customization.SpiffyOverlayScreen");
                    Screen overlay = (Screen) overlayType.getConstructor(boolean.class).newInstance(true);
                    Class<?> layoutType = Class.forName("de.keksuccino.fancymenu.customization.layout.Layout");
                    layout = layoutType.getConstructor(Screen.class).newInstance(overlay);
                    layoutFile = FabricLoader.getInstance().getGameDir().resolve("config/fancymenu/customization/chronicle_hud_probe.txt");
                    Files.createDirectories(layoutFile.getParent());
                    field(layout, "layoutFile", layoutFile.toFile());
                    addNativeElement("spiffy_player_heart_health_bar", -110);
                    addNativeElement("spiffy_player_food_bar", 10);
                    editor = (Screen) Class.forName("de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen")
                            .getConstructor(Screen.class, layoutType).newInstance(overlay, layout);
                    client.setScreen(editor);
                }
                case 2 -> {
                    require(elements().size() == 2, "Editor did not build both heart/food elements");
                    checks.add("real Spiffy HUD editor opened and built native heart/food elements with max health 22");
                    screenshot(client, "hud-editor-small.png");
                    Object element = elements().get(1).getClass().getField("element").get(elements().get(1));
                    field(element, "posOffsetX", 67);
                    editor.getClass().getMethod("saveLayout").invoke(editor);
                    require(Files.isRegularFile(layoutFile), "Editor did not save layout");
                    Class.forName("de.keksuccino.fancymenu.customization.layout.LayoutHandler").getMethod("reloadLayouts").invoke(null);
                    checks.add("editor public save API persisted a moved element to the disposable client profile");
                    client.getWindow().setWindowedSize(1280, 720);
                }
                case 3 -> {
                    Object reloaded = null;
                    List<?> layouts = (List<?>) Class.forName("de.keksuccino.fancymenu.customization.layout.LayoutHandler")
                            .getMethod("getAllLayouts").invoke(null);
                    for (Object candidate : layouts) {
                        if (layoutFile.toFile().equals(candidate.getClass().getField("layoutFile").get(candidate))) reloaded = candidate;
                    }
                    require(reloaded != null, "Saved layout missing after reload");
                    Object collection = reloaded.getClass().getMethod("buildElementInstances").invoke(reloaded);
                    List<Object> instances = new ArrayList<>((List<?>) collection.getClass().getField("foregroundElements").get(collection));
                    instances.addAll((List<?>) collection.getClass().getField("backgroundElements").get(collection));
                    require(instances.size() == 2, "Saved elements missing after reload");
                    boolean movedFood = false;
                    for (Object instance : instances) {
                        Object builder = instance.getClass().getField("builder").get(instance);
                        if ("spiffy_player_food_bar".equals(builder.getClass().getMethod("getIdentifier").invoke(builder)))
                            movedFood = instance.getClass().getField("posOffsetX").getInt(instance) == 67;
                    }
                    require(movedFood, "Moved element position lost after reload");
                    require(client.getWindow().getFramebufferWidth() == 1280, "Window resize failed");
                    checks.add("public layout reload retained both elements and moved position; window resized at GUI scale 2");
                    screenshot(client, "hud-editor-wide.png");
                }
                case 4 -> {
                    for (String image : List.of("hud-editor-small.png", "hud-editor-wide.png"))
                        require(Files.isRegularFile(output.resolve("screenshots").resolve(image)), "Missing screenshot " + image);
                    finish(client, true, "Framework/editor prototype only; dynamic food capacity requires a Chronicle addon element");
                }
                default -> throw new IllegalStateException("Invalid HUD test step");
            }
        } catch (Throwable failure) {
            screenshot(client, "hud-failure.png");
            finish(client, false, failure.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private void addNativeElement(String identifier, int x) throws Exception {
        Object builder = Class.forName("de.keksuccino.fancymenu.customization.element.ElementRegistry")
                .getMethod("getBuilder", String.class).invoke(null, identifier);
        require(builder != null, "Missing public element builder " + identifier);
        Object element = builder.getClass().getMethod("buildDefaultInstance").invoke(builder);
        field(element, "posOffsetX", x);
        field(element, "posOffsetY", -60);
        Class<?> base = Class.forName("de.keksuccino.fancymenu.customization.element.AbstractElement");
        Object serialized = builder.getClass().getMethod("serializeElementInternal", base).invoke(builder, element);
        ((List<Object>) layout.getClass().getField("serializedElements").get(layout)).add(serialized);
    }

    private List<?> elements() throws Exception {
        return (List<?>) editor.getClass().getField("normalEditorElements").get(editor);
    }

    private static void field(Object object, String name, Object value) throws Exception {
        object.getClass().getField(name).set(object, value);
    }

    private void screenshot(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(output.toFile(), name, client.getFramebuffer(), 1, ignored -> {});
    }

    private void finish(MinecraftClient client, boolean passed, String detail) {
        if (finished) return;
        finished = true;
        try {
            Files.writeString(output.resolve("hud-prototype.json"), new Gson().toJson(Map.of(
                    "result", passed ? "PASS" : "FAIL", "checks", checks, "detail", detail)));
        } catch (Exception failure) {
            failure.printStackTrace();
        }
        client.scheduleStop();
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }
}
