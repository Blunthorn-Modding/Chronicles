# Forge and Altar support feeding - dev.38

The operator reported that one enchant worked, then the Forge became unpowered.
The saved review world supplies the reproduction: Forge `(10,65,23)` has exactly
one adjacent campaign sculk cell, below it at `(10,64,23)`. That cell is still
Heart-connected but has zero vitality. The wider network is healthy.

The feeding classifier accepted only directly adjacent air or owned dry veins.
The Forge occupied its support's last exposed face; the other five faces were
solid ground or sculk. Its support therefore became `SEALED` and slowly drained.
Crafting consumes no ecology vitality; the first craft and subsequent loss of
power happened across that timer. The short native readiness tests verified
the model and a craft but did not age this support past its loss duration.

## Narrow repair

Only the supplied Ancient Forge and Ancient Altar are ambient-air passages for
feeding. For an adjacent station, check at most its five non-origin neighbors
through loaded chunks. Actual air provides `SHADED` exposure to the support.
Station/outlet light is never sampled: the Forge's own emitted light cannot
amplify its food. The support itself cannot count as an outlet. Unloaded outlets
are unknown, and an entirely buried station remains sealed.

This is one hop only. It creates no growth route, ownership, connection, chunk
ticket or immunity for ordinary buildings. The actual loaded adjacent sculk
still needs Heart connectivity and at least25% vitality. Existing station
readiness, extent thresholds, pause/empty/hold gates and emitted light are
unchanged. Core receives no gameplay change.

Existing exhausted sculk recovers naturally through the exposed station. The
preserved review's five-minute recovery setting takes roughly75 active seconds
to reach25% from zero, plus bounded connection/assessment time. Nothing resets
the saved vitality, world, tuning or accepted prayers.

## Verification

Four focused regressions cover exposed Forge and Altar after1,500 ticks, an
enclosed station and ordinary solid block, unloaded outlets without unloaded
reads, and exhausted-support recovery followed by real detachment. The bright
test world still produces `SHADED`, proving the relay does not use light.

The native `feeding_age` fixture performs an enchant through the actual campaign
gate, accelerates1,500 ecology ticks at one-minute loss/recovery, verifies the
single support remains100%/living/`SHADED`, then performs a second real enchant.
Existing off/kindling/active/light, cut/reconnect, isolation, pause/disable, full
tracker, item conservation, two-client and restart checks remain in the suite.
Exact commands and evidence are in [TEST_PLAN](TEST_PLAN.md).

## Separate UI design review

The current player UI is unchanged in dev.38. The operator rejected dev.37's
presentation and requests the Altar's native popup framework, two item inputs,
a bowl gesture, no labels/status/recipe browser, and concealed outcomes.
[Ritual proposal](M11_FORGE_RITUAL_DESIGN.md) and
[interactive mockup](design/forge-ritual.html) await operator review **before**
runtime implementation. Target-consuming failed attempts, real owned output,
animation and future recipe books are design work, not part of this fix.
