package io.github.chronicle.core.client;

import io.github.chronicle.core.network.ItemEnchantingPayloads;
import io.github.chronicle.core.screen.ItemEnchantingScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Original Forge geometry, real slots and a same-handler recipe guide. */
public final class ItemEnchantingScreen extends HandledScreen<ItemEnchantingScreenHandler> {
    private static final Identifier POPUP = Identifier.ofVanilla("popup/background");
    private static final Identifier PANEL = Identifier.of("chronicle_core", "workshop/panel");
    private static final Identifier SLOT = Identifier.of("chronicle_core", "workshop/slot");
    private static final Identifier RESULT_SLOT = Identifier.of("chronicle_core", "workshop/result");
    private static final Identifier PLUS = Identifier.of("chronicle_core", "workshop/plus");
    private static final Identifier ARROW = Identifier.of("chronicle_core", "workshop/arrow");
    private static final int INK = 0xFF404040;
    private static final int GUIDE_WIDTH = 304;
    private static final int GUIDE_HEIGHT = 208;
    private static final int SLOT_FRAME_SIZE = 18;
    private static final int SLOT_FRAME_OFFSET = 1;
    private static final int LABEL_Y = 26;
    private static final int STATUS_X = 9;
    private static final int STATUS_Y = 76;
    private static final int STATUS_WIDTH = 202;
    private static final int RECIPES_X = 150;
    private static final int RECIPES_Y = 94;
    private static final int RECIPES_WIDTH = 62;
    private static final int INVENTORY_LABEL_X = 29;
    private static final int INVENTORY_LABEL_Y = 118;
    private final Map<String, ButtonWidget> buttons = new LinkedHashMap<>();
    private ItemEnchantingPayloads.View state;
    private boolean guide, timedOut;
    private int pendingTicks = -1, page;

    public ItemEnchantingScreen(ItemEnchantingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = ItemEnchantingScreenHandler.WIDTH;
        backgroundHeight = ItemEnchantingScreenHandler.HEIGHT;
        state = handler.view();
    }

    public ItemEnchantingPayloads.View state() { return state; }
    public boolean pending() { return pendingTicks >= 0; }
    public int recipePageForTest() { return page; }

    public void accept(ItemEnchantingPayloads.View next) {
        if (next.opening() || next.syncId() != handler.syncId || !next.session().equals(state.session())
                || next.revision() <= state.revision()) return;
        state = next.recipes() == null ? next.withRecipes(state.recipes()) : next;
        handler.view(state);
        pendingTicks = -1;
        timedOut = false;
        handler.clientPending(false);
        updateWidgets();
    }

    @Override protected void init() {
        super.init();
        buttons.clear();
        button("Recipes", text("recipes"), x + RECIPES_X, y + RECIPES_Y, RECIPES_WIDTH,
                () -> { guide = true; setFocused(null); updateWidgets(); });
        int left = guideLeft(), top = guideTop();
        button("Back", text("back"), left + 116, top + 182, 72, this::back);
        button("Previous", Text.literal("<"), left + 12, top + 182, 24, () -> { page--; updateWidgets(); });
        button("Next", Text.literal(">"), left + 268, top + 182, 24, () -> { page++; updateWidgets(); });
        updateWidgets();
    }

    private void button(String id, Text label, int left, int top, int width, Runnable action) {
        buttons.put(id, addDrawableChild(ButtonWidget.builder(label, ignored -> action.run())
                .dimensions(left, top, width, 20).build()));
    }
    private static Text text(String suffix) { return Text.translatable("screen.chronicle.item_workshop." + suffix); }
    private List<ItemEnchantingPayloads.RecipeHint> recipes() { return state.recipes(); }
    private int pages() { return Math.max(1, (recipes().size() + 5) / 6); }
    private int guideLeft() { return (width - GUIDE_WIDTH) / 2; }
    private int guideTop() { return (height - GUIDE_HEIGHT) / 2; }
    private void back() { guide = false; setFocused(null); updateWidgets(); }
    private void updateWidgets() {
        page = Math.clamp(page, 0, pages() - 1);
        if (buttons.isEmpty()) return;
        var recipeButton = buttons.get("Recipes");
        recipeButton.visible = !guide;
        recipeButton.active = !recipes().isEmpty() && !pending();
        for (String id : List.of("Back", "Previous", "Next")) buttons.get(id).visible = guide;
        buttons.get("Previous").active = page > 0;
        buttons.get("Next").active = page + 1 < pages();
    }
    private Text status() {
        if (pending()) return text("status.forging");
        if (timedOut) return text("status.reopen");
        return switch (state.state()) {
            case READY -> text("status.ready");
            case EMPTY_TARGET -> text("status.empty_target");
            case EMPTY_MATERIAL -> text("status.empty_material");
            case SHORT_MATERIAL -> text("status.short_material");
            case UNCHANGED -> text("status.unchanged");
            case QUIET -> text("status.quiet");
            case UNPOWERED -> text("status.unpowered");
            case INCOMPATIBLE -> text("status.incompatible");
            case UNAVAILABLE -> text("status.unavailable");
        };
    }
    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, PANEL, x, y, backgroundWidth, backgroundHeight);
        for (int index = 0; index < ItemEnchantingScreenHandler.PLAYER_END; index++) {
            Slot slot = handler.getSlot(index);
            Identifier sprite = index == 2 ? RESULT_SLOT : SLOT;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite,
                    x + slot.x - SLOT_FRAME_OFFSET, y + slot.y - SLOT_FRAME_OFFSET,
                    SLOT_FRAME_SIZE, SLOT_FRAME_SIZE);
        }
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, PLUS, x + 72, y + 46, 10, 10);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, ARROW, x + 120, y + 44, 28, 14);
    }
    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, 9, 8, INK, false);
        drawCentered(context, text("target"), 53, LABEL_Y);
        drawCentered(context, text("material"), 97, LABEL_Y);
        drawCentered(context, text("result"), 169, LABEL_Y);
        context.drawText(textRenderer, textRenderer.trimToWidth(status().getString(), STATUS_WIDTH), STATUS_X, STATUS_Y, INK, false);
        context.drawText(textRenderer, text("inventory"), INVENTORY_LABEL_X, INVENTORY_LABEL_Y, INK, false);
    }
    private void drawCentered(DrawContext context, Text value, int center, int top) {
        context.drawText(textRenderer, value, center - textRenderer.getWidth(value) / 2, top, INK, false);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (guide) { renderGuide(context, mouseX, mouseY, delta); return; }
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        for (int index = 0; index < 2; index++) {
            Slot slot = handler.getSlot(index);
            if (!slot.hasStack() && inside(mouseX, mouseY, x + slot.x, y + slot.y, 16, 16))
                context.drawTooltip(textRenderer, text(index == 0 ? "target" : "material"), mouseX, mouseY);
        }
    }
    private void renderGuide(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = guideLeft(), top = guideTop();
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, left, top, GUIDE_WIDTH, GUIDE_HEIGHT);
        context.drawTextWithShadow(textRenderer, text("recipes"), left + 12, top + 12, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, (page + 1) + "/" + pages(), left + 256, top + 12, 0xFFFFFFFF);
        ItemEnchantingPayloads.RecipeHint hovered = null;
        for (int row = 0; row < 6 && page * 6 + row < recipes().size(); row++) {
            var recipe = recipes().get(page * 6 + row);
            int rowTop = top + 32 + row * 24;
            ItemStack icon = icon(recipe);
            context.drawItem(icon, left + 12, rowTop);
            context.drawStackOverlay(textRenderer, icon, left + 12, rowTop, recipe.count() > 1 ? Integer.toString(recipe.count()) : "");
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(recipe.count() + " x " + icon.getName().getString(), 250), left + 34, rowTop, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(enchantment(recipe), 250), left + 34, rowTop + 10, 0xFFFFFFFF);
            if (inside(mouseX, mouseY, left + 10, rowTop - 1, 284, 23)) hovered = recipe;
        }
        // Draw the panel first: native controls must remain above it.
        for (var button : buttons.values()) if (button.visible) button.render(context, mouseX, mouseY, delta);
        if (hovered != null) {
            List<Text> lines = List.of(Text.literal(hovered.count() + " x " + icon(hovered).getName().getString()), Text.literal(enchantment(hovered)));
            context.drawTooltip(textRenderer, lines, mouseX, mouseY);
        }
    }
    private static ItemStack icon(ItemEnchantingPayloads.RecipeHint recipe) {
        return new ItemStack(Registries.ITEM.getOptionalValue(Identifier.of(recipe.ingredient())).orElse(Items.BARRIER));
    }
    private static String enchantment(ItemEnchantingPayloads.RecipeHint recipe) {
        return recipe.label() + " " + Text.translatable("enchantment.level." + recipe.level()).getString();
    }
    private static boolean inside(double mouseX, double mouseY, int left, int top, int width, int height) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    @Override protected void onMouseClick(Slot slot, int index, int button, SlotActionType type) {
        if (guide || pending() || timedOut) return;
        if (index == 2) {
            int destination = switch (type) { case PICKUP -> 0; case QUICK_MOVE -> 1; case SWAP -> 2; case THROW -> 3; default -> -1; };
            if (destination >= 0) take(destination, destination == 1 ? 0 : button);
            return;
        }
        super.onMouseClick(slot, index, button, type);
    }
    private void take(int destination, int button) {
        if (guide || pending() || timedOut || state.state() != ItemEnchantingPayloads.State.READY
                || client == null || client.player == null || client.player.currentScreenHandler != handler
                || !ClientPlayNetworking.canSend(ItemEnchantingPayloads.Take.ID)) return;
        var request = new ItemEnchantingPayloads.Take(handler.syncId, state.session(), state.token(), destination, button);
        pendingTicks = 0;
        handler.clientPending(true);
        updateWidgets();
        ClientPlayNetworking.send(request);
    }
    public boolean takeForTest(int destination, int button) { take(destination, button); return pending(); }
    public boolean triggerForTest(String id) {
        var button = buttons.get(id);
        if (button == null || !button.visible || !button.active) return false;
        button.onPress(new MouseInput(0, 0));
        return true;
    }
    @Override protected void handledScreenTick() {
        super.handledScreenTick();
        if (pending() && ++pendingTicks >= 200) {
            pendingTicks = -1;
            timedOut = true;
            // Never replay an uncertain take. Native close still recovers remaining inputs.
            handler.clientPending(true);
            updateWidgets();
        }
    }
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (!guide) return super.mouseClicked(click, doubled);
        for (var button : buttons.values()) if (button.visible && button.mouseClicked(click, doubled)) { setFocused(button); return true; }
        return true;
    }
    @Override public boolean mouseReleased(Click click) {
        if (!guide) return super.mouseReleased(click);
        for (var button : buttons.values()) if (button.visible) button.mouseReleased(click);
        return true;
    }
    @Override public boolean mouseDragged(Click click, double dx, double dy) { return guide || super.mouseDragged(click, dx, dy); }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!guide) return super.mouseScrolled(mx, my, horizontal, vertical);
        if (vertical != 0) { page += vertical < 0 ? 1 : -1; updateWidgets(); }
        return true;
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (!guide) return super.keyPressed(input);
        if (input.key() == InputUtil.GLFW_KEY_ESCAPE || input.key() == InputUtil.GLFW_KEY_BACKSPACE) { back(); return true; }
        // Navigation only. Inventory, drop, hotbar and offhand shortcuts cannot reach hidden slots.
        if (input.key() == InputUtil.GLFW_KEY_TAB || input.key() == InputUtil.GLFW_KEY_ENTER
                || input.key() == InputUtil.GLFW_KEY_SPACE || input.key() == InputUtil.GLFW_KEY_LEFT
                || input.key() == InputUtil.GLFW_KEY_RIGHT || input.key() == InputUtil.GLFW_KEY_UP
                || input.key() == InputUtil.GLFW_KEY_DOWN) super.keyPressed(input);
        return true;
    }
    @Override public boolean shouldPause() { return false; }
}
