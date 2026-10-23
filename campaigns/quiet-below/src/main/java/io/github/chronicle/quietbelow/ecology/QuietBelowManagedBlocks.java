package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ManagedBlocks;
import io.github.chronicle.core.server.MapSiteAssignments;
import io.github.chronicle.quietbelow.QuietBelow;
import net.minecraft.registry.Registries;

/** Quiet Below's structural policy layered on Core's generic physical-instance accounting. */
public final class QuietBelowManagedBlocks {
    public static final String ALTAR = "quiet_below:ancient_altar";
    public static final String FORGE = "quiet_below:ancient_forge";
    public static final String HEART = "quiet_below:relic_heart";
    private static final String HEART_REQUIREMENT = "quiet_below:relic_heart";

    private QuietBelowManagedBlocks() { }

    public static void register() {
        ManagedBlocks.registerPolicy(HEART, new HeartPolicy());
    }

    private static final class HeartPolicy implements ManagedBlocks.PlacementPolicy {
        @Override public String deny(ManagedBlocks.PlacementRequest request) {
            QuietBelowEcology.RootAuthority authority = QuietBelowEcology.rootAuthority(request.server());
            String structural = structuralProblem(authority, request.dimension(), request.position());
            if (structural != null) return structural;
            if (authority.phase() == QuietBelowEcology.RootPhase.BOUND) {
                try {
                    AuthoringSite resolved = MapSiteAssignments.resolve(request.server(), HEART_REQUIREMENT).orElse(null);
                    if (!(resolved instanceof AuthoringSite.Anchor anchor) || !anchor.id().equals(authority.siteId())
                            || anchor.revision() != authority.siteRevision() || !anchor.position().equals(authority.position()))
                        return "The bound Relic Heart pin is unavailable or changed; placement is denied.";
                } catch (RuntimeException exception) {
                    return "The bound Relic Heart assignment is stale; placement is denied.";
                }
            } else {
                try {
                    if (MapSiteAssignments.bindings(request.server()).containsKey(HEART_REQUIREMENT))
                        MapSiteAssignments.resolve(request.server(), HEART_REQUIREMENT);
                } catch (RuntimeException exception) {
                    return "The Relic Heart assignment is stale; resolve it before placement.";
                }
            }
            return null;
        }

        @Override public void afterReservation(ManagedBlocks.PlacementRequest request) throws Exception {
            QuietBelowEcology.RootAuthority authority = QuietBelowEcology.rootAuthority(request.server());
            boolean immutable = authority.phase() != QuietBelowEcology.RootPhase.UNBOUND;
            if (authority.phase() == QuietBelowEcology.RootPhase.UNKNOWN || authority.phase() == QuietBelowEcology.RootPhase.ENDED)
                throw new IllegalStateException("Relic Heart root authority changed during placement.");
            MapSiteAssignments.ensureManagedAnchor(request.server(), HEART_REQUIREMENT, HEART, request.dimension(), request.position(),
                    Registries.BLOCK.getId(QuietBelow.RELIC_HEART).toString(), immutable);
        }

        @Override public String protectsAuthoringSite(net.minecraft.server.MinecraftServer server, String siteId) {
            QuietBelowEcology.RootAuthority authority = QuietBelowEcology.rootAuthority(server);
            boolean root = authority.siteId() != null && authority.siteId().equals(siteId);
            if (!root) try {
                var binding = MapSiteAssignments.bindings(server).get(HEART_REQUIREMENT);
                root = binding != null && binding.siteId().equals(siteId);
            } catch (RuntimeException unavailable) {
                root = HEART.equals(siteId);
            }
            if (!root && authority.phase() == QuietBelowEcology.RootPhase.UNKNOWN) try {
                AuthoringSite site = AuthoringRuntime.get(server).service().state().sites().get(siteId);
                root = site instanceof AuthoringSite.Anchor anchor
                        && Registries.BLOCK.getId(QuietBelow.RELIC_HEART).toString().equals(anchor.expectedBlock());
            } catch (RuntimeException unavailable) {
                root = HEART.equals(siteId);
            }
            if (!root) return null;
            QuietBelowEcology.RootPhase phase = authority.phase();
            return phase == QuietBelowEcology.RootPhase.UNBOUND ? null :
                    "The bound or historical Relic Heart anchor cannot be changed.";
        }

        @Override public String protectsAssignment(net.minecraft.server.MinecraftServer server, String requirementId) {
            if (!HEART_REQUIREMENT.equals(requirementId)) return null;
            QuietBelowEcology.RootPhase phase = QuietBelowEcology.rootAuthority(server).phase();
            return phase == QuietBelowEcology.RootPhase.UNBOUND ? null :
                    "The bound or historical Relic Heart assignment cannot be changed.";
        }
    }

    static String structuralProblem(QuietBelowEcology.RootAuthority authority, String dimension,
                                    io.github.chronicle.core.authoring.BlockPosition position) {
        if (authority.phase() == QuietBelowEcology.RootPhase.UNKNOWN)
            return "Relic Heart placement is unavailable while ecology authority cannot be verified.";
        if (authority.phase() == QuietBelowEcology.RootPhase.ENDED)
            return "The Relic Heart was destroyed; the ended network cannot receive another root.";
        if (authority.phase() == QuietBelowEcology.RootPhase.BOUND
                && (!dimension.equals(authority.dimension()) || !position.equals(authority.position())))
            return "The bound Relic Heart can only be repaired at its original position.";
        return null;
    }
}
