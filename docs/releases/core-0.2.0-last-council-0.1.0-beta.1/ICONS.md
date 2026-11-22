# Modrinth project icon

Use **last-council-icon.png** for The Last Council's Modrinth project icon.

It is the default Minecraft Java **1.21.10 golden helmet item texture**, enlarged
with nearest-neighbor scaling on a **512 x 512 transparent PNG**. The original
16 x 16 texture is scaled 28 times and given 32 pixels of transparent padding,
matching the earlier Chronicle book-and-quill icon's presentation. There is no
recoloring, added text, background decoration or AI-generated artwork.

Original source: `assets/minecraft/textures/item/golden_helmet.png` from the
locally cached Minecraft Java 1.21.10 client. Original texture SHA-256:
`8ae45e17781348267aa32b03e7e4f8c828d1f62b2b9a3f4216b04b6932f73868`.

The helmet artwork remains Minecraft artwork belonging to Mojang/Microsoft;
the project's MIT license does not relicense it.

The local recipe is `reports/modrinth-icons/render-last-council-icon.ps1`,
relative to the workspace root. Every output pixel is checked against the
original texture or transparent padding. This prepares an upload file only;
no hosted project icon or existing release JAR was changed.
