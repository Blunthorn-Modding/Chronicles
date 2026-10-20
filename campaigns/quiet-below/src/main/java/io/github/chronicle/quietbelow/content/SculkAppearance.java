package io.github.chronicle.quietbelow.content;

import io.github.chronicle.quietbelow.ecology.EcologyAppearance;
import net.minecraft.state.property.IntProperty;

/** Model selection only; no new block, block entity or entity registrations. */
public final class SculkAppearance {
    public static final IntProperty VITALITY=IntProperty.of(EcologyAppearance.PROPERTY,0,3);
    private SculkAppearance() { }
}
