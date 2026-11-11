package io.github.chronicle.core.roles;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Save-before-publish role-seat service. It deliberately does not grant kits or teleport players. */
public final class PlayerRoleAssignmentService {
    private final PlayerRoleAssignmentRepository repository; private PlayerRoleAssignments current;
    public PlayerRoleAssignmentService(PlayerRoleAssignmentRepository repository) throws IOException { this.repository = java.util.Objects.requireNonNull(repository); current = java.util.Objects.requireNonNull(repository.load()); }
    public synchronized PlayerRoleAssignments state() { return current; }
    public synchronized void assign(UUID player, String roleId, Map<String, PlayerRole> roles) throws IOException {
        require(player, roleId, roles); var candidate = new LinkedHashMap<>(current.assigned()); candidate.put(player, roleId);
        int seats = (int) candidate.values().stream().filter(roleId::equals).count(); if (seats > roles.get(roleId).capacity()) throw new IllegalStateException("Player Role capacity is full");
        commit(candidate, current.started(), current.migratedExisting(), current.startingDisabled(), current.respawnDisabled());
    }
    public synchronized void unassign(UUID player) throws IOException { if (player == null) throw new IllegalArgumentException("player UUID is required"); var candidate = new LinkedHashMap<>(current.assigned()); if (candidate.remove(player) != null) commit(candidate, current.started(), current.migratedExisting(), current.startingDisabled(), current.respawnDisabled()); }
    public synchronized void markStarted(UUID player) throws IOException { if (player == null) throw new IllegalArgumentException("player UUID is required"); var marks = new LinkedHashSet<>(current.started()); if (marks.add(player)) commit(current.assigned(), marks, current.migratedExisting(), current.startingDisabled(), current.respawnDisabled()); }
    /** Migration happens exactly once and never grants/moves anything. */
    public synchronized void bootstrapExisting(Set<UUID> knownParticipants) throws IOException { if (current.migratedExisting()) return; var marks = new LinkedHashSet<>(current.started()); marks.addAll(knownParticipants == null ? Set.of() : knownParticipants); commit(current.assigned(), marks, true, current.startingDisabled(), current.respawnDisabled()); }
    /** Persist a deliberate policy opt-out separately from an absent role default. */
    public synchronized void setPolicyDisabled(UUID player, boolean arrival, boolean disabled) throws IOException { if(player==null)throw new IllegalArgumentException("player UUID is required");var changed=new LinkedHashSet<>(arrival?current.startingDisabled():current.respawnDisabled());if(disabled?changed.add(player):changed.remove(player))commit(current.assigned(),current.started(),current.migratedExisting(),arrival?changed:current.startingDisabled(),arrival?current.respawnDisabled():changed); }
    private void commit(Map<UUID,String> assigned, Set<UUID> started, boolean migrated, Set<UUID> startingDisabled, Set<UUID> respawnDisabled) throws IOException { var next = new PlayerRoleAssignments(Math.addExact(current.revision(),1), assigned, started, migrated, startingDisabled, respawnDisabled); repository.save(next); current=next; }
    private static void require(UUID player,String roleId,Map<String,PlayerRole> roles) { if(player==null||roleId==null||roles==null||!roles.containsKey(roleId))throw new IllegalArgumentException("Choose a registered Player Role"); }
}
