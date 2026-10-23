"""Extract pinned local Minecraft UI assets for the Forge ritual HTML preview.

This does not download anything and deliberately writes outside Git under the
workspace reports directory. The committed HTML references that review-only
asset directory so it can demonstrate the actual popup, slots, widgets, items,
and bitmap font without vendoring Mojang artwork into the repository.
"""

from pathlib import Path
from zipfile import ZipFile


REPOSITORY = Path(__file__).resolve().parents[1]
WORKSPACE = REPOSITORY.parent
OUTPUT = WORKSPACE / "reports" / "forge-ritual-preview-assets"
HTML = REPOSITORY / "docs" / "design" / "forge-ritual.html"
ASSETS = (
    "assets/minecraft/textures/gui/sprites/popup/background.png",
    "assets/minecraft/textures/gui/sprites/popup/background.png.mcmeta",
    "assets/minecraft/textures/gui/sprites/container/slot.png",
    "assets/minecraft/textures/gui/sprites/container/slot_highlight_back.png",
    "assets/minecraft/textures/gui/sprites/container/slot_highlight_back.png.mcmeta",
    "assets/minecraft/textures/gui/sprites/widget/button.png",
    "assets/minecraft/textures/gui/sprites/widget/button.png.mcmeta",
    "assets/minecraft/textures/gui/sprites/widget/button_highlighted.png",
    "assets/minecraft/textures/gui/sprites/widget/button_highlighted.png.mcmeta",
    "assets/minecraft/textures/gui/sprites/widget/button_disabled.png",
    "assets/minecraft/textures/gui/sprites/widget/button_disabled.png.mcmeta",
    "assets/minecraft/textures/font/ascii.png",
    "assets/minecraft/textures/item/amethyst_shard.png",
    "assets/minecraft/textures/item/book.png",
    "assets/minecraft/textures/item/bow.png",
    "assets/minecraft/textures/item/bowl.png",
    "assets/minecraft/textures/item/bread.png",
    "assets/minecraft/textures/block/cobblestone.png",
    "assets/minecraft/textures/item/diamond.png",
    "assets/minecraft/textures/item/echo_shard.png",
    "assets/minecraft/textures/item/iron_ingot.png",
    "assets/minecraft/textures/item/iron_sword.png",
    "assets/minecraft/textures/item/leather.png",
    "assets/minecraft/textures/block/oak_log.png",
    "assets/minecraft/textures/block/torch.png",
)


def pinned_client_jar() -> Path:
    candidates = sorted(
        (WORKSPACE / "cache" / "gradle" / "caches" / "fabric-loom" / "minecraftMaven"
         / "net" / "minecraft" / "minecraft-clientonly").glob("**/*1.21.10*.jar")
    )
    if not candidates:
        raise FileNotFoundError("Pinned Minecraft 1.21.10 client jar is not available in the local cache.")
    return candidates[-1]


def extract() -> Path:
    source = pinned_client_jar()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with ZipFile(source) as archive:
        names = set(archive.namelist())
        missing = [asset for asset in ASSETS if asset not in names]
        if missing:
            raise RuntimeError("Pinned client jar lacks preview assets: " + ", ".join(missing))
        for asset in ASSETS:
            target = OUTPUT / asset
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(asset))
    return source


if __name__ == "__main__":
    jar = extract()
    print(f"Extracted {len(ASSETS)} pinned assets from {jar}")
    print(f"Preview: {HTML.resolve().as_uri()}?state=staged&size=640")
