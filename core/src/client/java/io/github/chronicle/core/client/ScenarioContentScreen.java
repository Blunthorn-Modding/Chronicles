package io.github.chronicle.core.client;

import io.github.chronicle.core.menu.CoreMenuModel.View;
import io.github.chronicle.core.scenario.ScenarioExportPreview;
import java.util.List;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Operator review popup. Filesystem destinations and definitions remain server-owned. */
final class ScenarioContentScreen extends Screen {
    private static final Identifier POPUP = Identifier.ofVanilla("popup/background");
    private static final int PROVIDER_ROWS = 6;
    private final CoreMenuScreen parent;
    private long openingToken;
    private final List<ScenarioExportPreview> providers;
    private ScenarioExportPreview preview;
    private String status;
    private boolean stale;
    private int providerOffset;

    ScenarioContentScreen(CoreMenuScreen parent, long openingToken, List<ScenarioExportPreview> providers,
                          ScenarioExportPreview preview, String status) {
        super(Text.literal("Scenario content"));
        this.parent = parent;
        this.openingToken = openingToken;
        this.providers = List.copyOf(providers);
        this.preview = preview;
        this.status = status == null ? "" : status;
    }

    long openingToken() { return openingToken; }
    CoreMenuScreen parentMenu() { return parent; }
    private int panelWidth() { return Math.min(440, Math.max(240, width - 24)); }
    private int panelTop() { return Math.max(6, (height - 240) / 2 + 6); }

    @Override protected void init() {
        int w = panelWidth(), x = (width - w) / 2, y = panelTop(), footer = y + 206;
        if (preview == null) {
            int rows = Math.min(PROVIDER_ROWS, providers.size());
            providerOffset = Math.clamp(providerOffset, 0, Math.max(0, providers.size() - rows));
            for (int index = 0; index < rows; index++) {
                var value = providers.get(providerOffset + index);
                var choice = addDrawableChild(ButtonWidget.builder(
                        Text.literal(textRenderer.trimToWidth(value.title(), w - 16)),
                        ignored -> parent.openScenarioContent(value.providerId(), this))
                        .dimensions(x + 8, y + 66 + index * 20, w - 16, 18).build());
                choice.active = value.available() && !stale;
                if (!value.reason().isBlank()) choice.setTooltip(Tooltip.of(Text.literal(value.reason())));
            }
            if (providers.size() > rows) {
                var up = addDrawableChild(ButtonWidget.builder(Text.literal("Up"), ignored -> {
                    providerOffset--; clearAndInit();
                }).dimensions(x, y + 188, 54, 18).build());
                up.active = providerOffset > 0;
                var down = addDrawableChild(ButtonWidget.builder(Text.literal("Down"), ignored -> {
                    providerOffset++; clearAndInit();
                }).dimensions(x + 58, y + 188, 54, 18).build());
                down.active = providerOffset + rows < providers.size();
            }
        } else {
            var export = addDrawableChild(ButtonWidget.builder(Text.literal("Export content..."),
                    ignored -> parent.exportScenarioContent(this))
                    .dimensions(x, footer, Math.max(110, w - 58), 18).build());
            export.active = !stale && preview.available() && parent.state().editing()
                    && parent.state().editable() && parent.state().hold().isBlank();
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), ignored -> close())
                .dimensions(x + w - 54, footer, 54, 18).build());
    }

    void serverUpdated(View updated, boolean changedToken) {
        if (changedToken) { markStale("The server state changed. Close and review this content again."); return; }
        if (!updated.success()) { status = updated.message(); clearAndInit(); return; }
        if (updated.scenarioPreview() != null) {
            openingToken = updated.token();
            preview = updated.scenarioPreview();
            status = updated.message();
            clearAndInit();
        }
    }

    void markStale(String reason) { stale = true; status = reason; clearAndInit(); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = panelWidth(), x = (width - w) / 2, y = panelTop();
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, POPUP, x - 6, y - 6, w + 12, 240);
        line(context, "Scenario content", x, y + 22, w, 0xFFFFFFFF);
        if (preview == null) {
            line(context, "Choose the scenario to export.", x, y + 46, w, 0xFFCCCCCC);
        } else {
            line(context, preview.title(), x, y + 36, w, 0xFFCCCCCC);
            line(context, "Capture current content for the next build.", x, y + 50, w, 0xFFCCCCCC);
            line(context, "Map setup stays with this map.", x, y + 62, w, 0xFFFFCC66);
            line(context, "Roles: " + preview.roles() + "    Books: " + preview.books(), x, y + 86, w, 0xFFFFFFFF);
            line(context, "Loot tables: " + preview.lootTables() + "    Events: " + preview.events(), x, y + 98, w, 0xFFFFFFFF);
            line(context, "Encounter groups: " + preview.encounterGroups(), x, y + 110, w, 0xFFFFFFFF);
            line(context, "Only reusable definitions are exported.", x, y + 130, w, 0xFFCCCCCC);
            if (preview.strippedRoleBindings() > 0)
                line(context, preview.strippedRoleBindings() + " role destinations stay on the map.", x, y + 144, w, 0xFFCCCCCC);
            wrapped(context, "Events start disabled; review their map bindings before use.", x, y + 158, w, 0xFFCCCCCC, 2);
            if (!status.isBlank()) wrapped(context, status, x, y + 184, w,
                    stale ? 0xFFFFCC66 : 0xFF77FF77, 2);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void line(DrawContext context, String text, int x, int y, int w, int color) {
        context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(text, w)), x, y, color);
    }
    private void wrapped(DrawContext context, String text, int x, int y, int w, int color, int maximum) {
        int line = 0;
        for (var value : textRenderer.wrapLines(Text.literal(text), Math.max(1, w))) {
            if (line >= maximum) break;
            context.drawTextWithShadow(textRenderer, value, x, y + line++ * 10, color);
        }
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { parent.dismissScenario(this); client.setScreen(parent); }
}
