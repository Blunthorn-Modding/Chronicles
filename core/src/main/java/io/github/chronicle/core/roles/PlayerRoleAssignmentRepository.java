package io.github.chronicle.core.roles;
import java.io.IOException;
public interface PlayerRoleAssignmentRepository { PlayerRoleAssignments load() throws IOException; void save(PlayerRoleAssignments state) throws IOException; }
