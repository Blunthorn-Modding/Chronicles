# Map-owned Player Role configuration

Effective editable Player Role definitions now live at `world/chronicle/player-roles.yaml`, beside the map-owned role assignment and once-only entry records. This single complete file keeps role starting/respawn Point bindings with the map that owns those Points.

For a new world only, Core reads `config/chronicle/player-roles.yaml` as a strict, read-only bootstrap input when it exists. Its bytes and initialization marker are never changed. The new world receives a canonical world-local copy. If neither legacy file nor marker exists, registered scenario defaults seed the world-local file instead.

Once the world file or its `.initialized` marker exists, it is authoritative. A missing initialized world file fails closed; Core never restores it from legacy or scenario defaults. Corrupt, oversized, special, or symlinked legacy input also fails without creating a world copy. Scenario exports remain portable and strip all role Point bindings.
