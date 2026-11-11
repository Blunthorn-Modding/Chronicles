package io.github.chronicle.quietbelow;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringCatalog;
import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.loot.LootEntry;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.managed.ManagedBlockDefinition;
import io.github.chronicle.core.managed.ManagedBlockItem;
import java.util.List;
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

    public static final RegistryKey<Block> ANCIENT_FORGE_KEY=blockKey("ancient_forge");
    public static final RegistryKey<Item> ANCIENT_FORGE_ITEM_KEY=itemKey("ancient_forge");
    public static final Block ANCIENT_FORGE_BLOCK=Registry.register(Registries.BLOCK,ANCIENT_FORGE_KEY,
            new io.github.chronicle.quietbelow.content.AncientForgeBlock(AbstractBlock.Settings.create().registryKey(ANCIENT_FORGE_KEY).strength(3.5f,8).requiresTool().nonOpaque()
                    .luminance(state -> state.get(io.github.chronicle.quietbelow.content.AncientForgeBlock.HEAT) == 2 ? 12 : 0)));
    public static final Item ANCIENT_FORGE=Registry.register(Registries.ITEM,ANCIENT_FORGE_ITEM_KEY,
            new ManagedBlockItem(ANCIENT_FORGE_BLOCK,new Item.Settings().registryKey(ANCIENT_FORGE_ITEM_KEY).useBlockPrefixedTranslationKey(), "quiet_below:ancient_forge"));
    public static final net.minecraft.block.entity.BlockEntityType<io.github.chronicle.quietbelow.content.AncientForgeBlockEntity> FORGE_BLOCK_ENTITY=
            Registry.register(Registries.BLOCK_ENTITY_TYPE,id("ancient_forge"),net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
                    .create(io.github.chronicle.quietbelow.content.AncientForgeBlockEntity::new,ANCIENT_FORGE_BLOCK).build());

    public static final RegistryKey<Block> ANCIENT_ALTAR_KEY=blockKey("ancient_altar");
    public static final RegistryKey<Item> ANCIENT_ALTAR_ITEM_KEY=itemKey("ancient_altar");
    public static final Block ANCIENT_ALTAR_BLOCK=Registry.register(Registries.BLOCK,ANCIENT_ALTAR_KEY,
            new io.github.chronicle.quietbelow.content.AncientAltarBlock(AbstractBlock.Settings.create().registryKey(ANCIENT_ALTAR_KEY).strength(3.5f,8).requiresTool().nonOpaque()));
    public static final Item ANCIENT_ALTAR=Registry.register(Registries.ITEM,ANCIENT_ALTAR_ITEM_KEY,
            new ManagedBlockItem(ANCIENT_ALTAR_BLOCK,new Item.Settings().registryKey(ANCIENT_ALTAR_ITEM_KEY).useBlockPrefixedTranslationKey(), "quiet_below:ancient_altar"));
    public static final net.minecraft.block.entity.BlockEntityType<io.github.chronicle.quietbelow.content.AncientAltarBlockEntity> ALTAR_BLOCK_ENTITY=
            Registry.register(Registries.BLOCK_ENTITY_TYPE,id("ancient_altar"),net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
                    .create(io.github.chronicle.quietbelow.content.AncientAltarBlockEntity::new,ANCIENT_ALTAR_BLOCK).build());

    public static final RegistryKey<Block> RELIC_HEART_KEY=blockKey("relic_heart");
    public static final RegistryKey<Item> RELIC_HEART_ITEM_KEY=itemKey("relic_heart");
    /** Mapmaker item; a placed Heart has no campaign behavior until explicitly bound. */
    public static final Block RELIC_HEART=Registry.register(Registries.BLOCK,RELIC_HEART_KEY,
            new io.github.chronicle.quietbelow.content.RelicHeartBlock(AbstractBlock.Settings.create().registryKey(RELIC_HEART_KEY)
                    .strength(3.5f, 3_600_000.0f).requiresTool().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK).nonOpaque()));
    public static final Item RELIC_HEART_ITEM=Registry.register(Registries.ITEM,RELIC_HEART_ITEM_KEY,
            new ManagedBlockItem(RELIC_HEART,new Item.Settings().registryKey(RELIC_HEART_ITEM_KEY).useBlockPrefixedTranslationKey(), "quiet_below:relic_heart"));

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
        io.github.chronicle.quietbelow.content.QuietBelowEventSounds.register();
        io.github.chronicle.core.server.ScenarioContentRuntime.registerScenario("quiet_below:scenario", "The Quiet Below","quiet_below:mechanics");
        boolean custom=io.github.chronicle.core.server.ScenarioAuthoringProfiles.active().isPresent();
        var scenarioDefaults = custom?java.util.Optional.of(new io.github.chronicle.core.scenario.ScenarioContent("quiet_below:scenario",Map.of(),Map.of(),Map.of(),Map.of())):io.github.chronicle.quietbelow.content.QuietBelowScenarioDefaults.loadOptional();
        scenarioDefaults.ifPresent(io.github.chronicle.quietbelow.content.QuietBelowScenarioDefaults::register);
        if (scenarioDefaults.isEmpty()) {
        try (var input=QuietBelow.class.getResourceAsStream("/data/quiet_below/chronicle/player-roles.yaml")) {
            if(input==null)throw new IllegalStateException("Missing bundled player-role defaults");
            ChronicleCore.playerRoles().registerYaml("quiet_below:player-roles",new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        } catch(java.io.IOException exception){throw new IllegalStateException("Cannot read bundled player-role defaults",exception);}
        }

        ChronicleCore.managedBlocks().register(new ManagedBlockDefinition("quiet_below:ancient_altar", "quiet_below:ancient_altar",
                "Ancient Altar", "A placed ritual station supplied by The Quiet Below.", 1, ManagedBlockDefinition.LimitMode.EDITABLE));
        ChronicleCore.managedBlocks().register(new ManagedBlockDefinition("quiet_below:ancient_forge", "quiet_below:ancient_forge",
                "Ancient Forge", "A placed forging station supplied by The Quiet Below.", 1, ManagedBlockDefinition.LimitMode.EDITABLE));
        ChronicleCore.managedBlocks().register(new ManagedBlockDefinition("quiet_below:relic_heart", "quiet_below:relic_heart",
                "Relic Heart", "The physical root of The Quiet Below's sculk network.", 1, ManagedBlockDefinition.LimitMode.FIXED));
        io.github.chronicle.quietbelow.ecology.QuietBelowManagedBlocks.register();
        io.github.chronicle.quietbelow.ecology.EcologyHeartDefianceTrial.register();
        io.github.chronicle.quietbelow.ecology.EcologyHeartAirCutTrial.register();
        io.github.chronicle.quietbelow.ecology.EradicationRecoveryTrial.register();
        io.github.chronicle.quietbelow.ecology.testing.CanonicalAuthoringTrial.register();
        io.github.chronicle.quietbelow.content.ForgeNotesTrial.register();
        io.github.chronicle.core.server.EnchantmentCapRuntime.recommendDefaults(MOD_ID,
                new io.github.chronicle.core.enchanting.EnchantmentCaps(true,1,java.util.Map.of()));
        io.github.chronicle.quietbelow.content.SculkAppearanceHandshake.register();
        // Optional public cues. Registering definitions arms no triggers or story timetable.
        if (scenarioDefaults.isEmpty()) {
        ChronicleCore.observations().register(new ObservationCatalog.Definition("quiet_below:city_presence", "City vibration",
                "The ancient stone carries a low vibration.", "minecraft:ambient.cave", ObservationCatalog.Audience.REGION));
        ChronicleCore.observations().register(new ObservationCatalog.Definition("quiet_below:distant_stir", "Distant stirring",
                "A low, unfamiliar rumble breaks the quiet.", "minecraft:ambient.cave", ObservationCatalog.Audience.PARTICIPANTS));
        }
        ChronicleCore.authoring().register(new AuthoringCatalog.Role("quiet_below:source_city", "Source city",
                "The source ancient city region; registers geometry without enabling growth.", AuthoringCatalog.Kind.REGION));
        ChronicleCore.authoring().register(new AuthoringCatalog.Role("quiet_below:sculk_takeover", "Sculk takeover region",
                "Optional region to adopt existing sculk into campaign ownership. Run Take over region separately; disconnected sculk stays dormant.", AuthoringCatalog.Kind.REGION));
        ChronicleCore.authoring().register(new AuthoringCatalog.Role("quiet_below:relic_heart", "Relic Heart",
                "Unique sculk root. Assign this block site before indexing; mine the weakened Heart with a pickaxe during active play to end the network.", AuthoringCatalog.Kind.BLOCK));
        ChronicleCore.authoring().register(new AuthoringCatalog.Role("quiet_below:ancient_altar", "Ancient Altar",
                "Optional contextual Altar site. Placed Ancient Altar blocks work without this registration.", AuthoringCatalog.Kind.BLOCK));
        ChronicleCore.authoring().register(new AuthoringCatalog.Role("quiet_below:ancient_forge", "Ancient Forge",
                "Optional contextual Forge site. Placed Ancient Forge blocks work without this registration.", AuthoringCatalog.Kind.BLOCK));
        // Map-authoring defaults, adjustable in the server's Core loot configuration.
        if (scenarioDefaults.isEmpty()) {
        io.github.chronicle.quietbelow.content.ForgeRecipeDefaults.HINTS.forEach(hint -> ChronicleCore.books().register(
                new io.github.chronicle.core.books.BookDefinition("quiet_below:field_journal_" + switch(hint.ingredient()) {
                    case "echo_shard" -> "i"; case "amethyst_shard" -> "ii"; case "prismarine_shard" -> "iii"; case "flint" -> "iv"; default -> "v";
                }, hint.title(), "Unknown", "Abandoned experiments by a later explorer of the ancient city.", hint.pages(), io.github.chronicle.core.books.BookDefinition.Rule.UNTIL_FOUND)));
        ChronicleCore.chestTypes().register(io.github.chronicle.quietbelow.content.ForgeRecipeDefaults.notesType());
        ChronicleCore.chestTypes().register(new LootType("quiet_below:settlement_supplies", "Settlement supplies",
                "Everyday supplies; a suggested starting distribution, editable by the organizer.", 4, 0, List.of(
                new LootEntry("minecraft:bread", 65, 2, 5, "{}"),
                new LootEntry("minecraft:torch", 70, 4, 12, "{}"),
                new LootEntry("minecraft:iron_ingot", 20, 1, 3, "{}"))));
        ChronicleCore.chestTypes().register(new LootType("quiet_below:mine_cache", "Mine cache",
                "Mining supplies; the recommendation is advisory, not a session requirement.", 3, 0, List.of(
                new LootEntry("minecraft:coal", 70, 3, 8, "{}"),
                new LootEntry("minecraft:raw_iron", 40, 1, 4, "{}"),
                new LootEntry("minecraft:torch", 65, 4, 10, "{}"))));
        ChronicleCore.chestTypes().register(new LootType("quiet_below:ancient_cache", "Ancient cache",
                "Optional ancient-site finds; this does not activate ecology or reveal events.", 2, 0, List.of(
                new LootEntry("minecraft:amethyst_shard", 60, 1, 4, "{}"),
                new LootEntry("minecraft:book", 35, 1, 2, "{}"),
                new LootEntry("minecraft:echo_shard", 10, 1, 1, "{}"))));
        }
        QuietBelowStages.register();
        io.github.chronicle.quietbelow.ecology.QuietBelowEcology.register();
        io.github.chronicle.quietbelow.ecology.EcologySoakTrial.register();
        io.github.chronicle.quietbelow.ecology.CapturedShutdownTrial.register();
        io.github.chronicle.quietbelow.ecology.QuietBelowAreaChanges.register();
        if(!custom)io.github.chronicle.core.server.Departures.registerDefaults(java.util.List.of(
            new io.github.chronicle.core.departures.DepartureDefinition("quiet_below:ship", "The only ship", 1,
                java.util.Optional.empty(), java.util.Optional.empty(),
                "You are taking the only ship off this island. Everyone aboard will come with you. Those left behind will have to fend for themselves. Are you sure?",
                "This is a point of no return. You and all characters aboard will leave the island, ending your participation. If all remaining participants are aboard, the campaign will end.",
                java.util.Optional.empty(), true)));

        io.github.chronicle.quietbelow.content.AncientForges.register();
        io.github.chronicle.quietbelow.content.AncientAltars.register();
        io.github.chronicle.quietbelow.ecology.AncientForgeTrial.register();
        io.github.chronicle.quietbelow.ecology.SourceStationsTrial.register();
        io.github.chronicle.quietbelow.ecology.EcologySpreadTrial.register();
        io.github.chronicle.quietbelow.ecology.AncientAltarTrial.register();
        io.github.chronicle.quietbelow.content.TemporaryAltarTrial.register();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ECHO_LENS));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(RESONANCE_MARKER));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(ANCIENT_FORGE));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(ANCIENT_ALTAR));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(RELIC_HEART_ITEM));
        io.github.chronicle.quietbelow.testing.StageEventFixture.register();
        LOGGER.info("The Quiet Below initialized");
    }
}
