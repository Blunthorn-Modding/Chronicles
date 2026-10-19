package io.github.chronicle.quietbelow;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.CampaignEvent;
import io.github.chronicle.core.campaign.EventOutcome;
import io.github.chronicle.quietbelow.content.ResonanceMarkerBlock;
import io.github.chronicle.quietbelow.stage.QuietBelowStages;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Campaign-owned content and event registration. */
public final class QuietBelow implements ModInitializer {
    public static final String MOD_ID = "quiet_below";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final RegistryKey<Item> ECHO_LENS_KEY = itemKey("echo_lens");
    public static final RegistryKey<Item> RESONANCE_MARKER_ITEM_KEY = itemKey("resonance_marker");
    public static final RegistryKey<Block> RESONANCE_MARKER_KEY = blockKey("resonance_marker");

    /** Public registry constants for common tests and campaign integration. */
    public static final Block BLOCK = Registry.register(Registries.BLOCK, RESONANCE_MARKER_KEY,
            new ResonanceMarkerBlock(AbstractBlock.Settings.create()
                    .registryKey(RESONANCE_MARKER_KEY)
                    .strength(1.5f, 6.0f)
                    .nonOpaque()
                    .requiresTool()));
    public static final Item ECHO_LENS = Registry.register(Registries.ITEM, ECHO_LENS_KEY,
            new Item(new Item.Settings().registryKey(ECHO_LENS_KEY).maxCount(1)));
    public static final Item RESONANCE_MARKER = Registry.register(Registries.ITEM, RESONANCE_MARKER_ITEM_KEY,
            new BlockItem(BLOCK, new Item.Settings().registryKey(RESONANCE_MARKER_ITEM_KEY)
                    .useBlockPrefixedTranslationKey()));

    private static RegistryKey<Item> itemKey(String path) {
        return RegistryKey.of(RegistryKeys.ITEM, id(path));
    }

    private static RegistryKey<Block> blockKey(String path) {
        return RegistryKey.of(RegistryKeys.BLOCK, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        QuietBelowStages.register();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ECHO_LENS));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(RESONANCE_MARKER));
        ChronicleCore.events().register(new CampaignEvent("quiet_below:readiness_check",
                "Records a framework readiness check without changing the world.", true, Map.of(),
                state -> new EventOutcome(Map.of("quiet_below:readiness_checked", true), "The Quiet Below readiness check was recorded.")));
        ChronicleCore.events().register(new CampaignEvent("quiet_below:mine_whisper",
                "Reveal a new public observation at Resonance Markers; changes no blocks.", true, Map.of(),
                state -> new EventOutcome(Map.of("quiet_below:fragment_revealed", true),
                        "A new trace can be observed at Resonance Markers, with or without an Echo Lens.")));
        LOGGER.info("The Quiet Below initialized");
    }
}
