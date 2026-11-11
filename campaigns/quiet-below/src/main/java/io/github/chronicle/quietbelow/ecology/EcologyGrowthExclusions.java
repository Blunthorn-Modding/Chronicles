package io.github.chronicle.quietbelow.ecology;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/** Campaign surface-growth exclusions; map datapacks may extend the ordinary block tag. */
public final class EcologyGrowthExclusions {
    public static final TagKey<Block> BLOCKS=TagKey.of(RegistryKeys.BLOCK,
            Identifier.of("quiet_below","sculk_growth_excluded"));
    private EcologyGrowthExclusions() { }
}
