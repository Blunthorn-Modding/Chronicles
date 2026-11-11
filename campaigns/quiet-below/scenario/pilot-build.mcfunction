# The Quiet Below isolated pilot fixture. Invoke manually once in a fresh pilot world:
# /function chronicle_pilot:build
# No load/tick tag references this function. All block coordinates remain inside
# x/z -48..48 and y 52..80. Sign text is intentionally concise; the full source
# wording lives in this campaign's scenario/clues.md.

execute if data storage chronicle_pilot:setup built run return 0
setworldspawn 0 65 0

# Common, public board and paths.
fill -6 64 -4 6 64 5 minecraft:cobblestone
fill -6 65 -4 6 68 -4 minecraft:spruce_planks
fill -6 65 5 6 68 5 minecraft:spruce_planks
fill -6 65 -3 -6 68 4 minecraft:spruce_planks
fill 6 65 -3 6 68 4 minecraft:spruce_planks
fill -5 65 -3 5 67 4 minecraft:air
fill 4 65 5 4 66 5 minecraft:air
fill -34 64 5 34 64 18 minecraft:gravel
fill -1 64 12 1 65 14 minecraft:cobblestone
setblock 0 65 13 minecraft:water

# Three small social cottages: floor, shell, hollow interior, roof and a two-block door.
fill -31 64 17 -17 64 31 minecraft:cobblestone
fill -31 65 17 -17 69 31 minecraft:spruce_planks
fill -30 65 18 -18 69 30 minecraft:air
fill -31 70 17 -17 71 31 minecraft:spruce_slab
fill -24 65 17 -24 66 17 minecraft:air
setblock -25 65 20 minecraft:red_bed[part=foot,facing=south]
setblock -25 65 21 minecraft:red_bed[part=head,facing=south]
setblock -29 65 19 minecraft:lantern[hanging=false,waterlogged=false]
setblock -31 67 24 minecraft:glass_pane
setblock -17 67 24 minecraft:glass_pane
fill -5 64 17 9 64 31 minecraft:cobblestone
fill -5 65 17 9 69 31 minecraft:spruce_planks
fill -4 65 18 8 69 30 minecraft:air
fill -5 70 17 9 71 31 minecraft:spruce_slab
fill 2 65 17 2 66 17 minecraft:air
setblock 0 65 20 minecraft:blue_bed[part=foot,facing=south]
setblock 0 65 21 minecraft:blue_bed[part=head,facing=south]
setblock -3 65 19 minecraft:lantern[hanging=false,waterlogged=false]
setblock -5 67 24 minecraft:glass_pane
setblock 9 67 24 minecraft:glass_pane
fill 18 64 17 32 64 31 minecraft:cobblestone
fill 18 65 17 32 69 31 minecraft:spruce_planks
fill 19 65 18 31 69 30 minecraft:air
fill 18 70 17 32 71 31 minecraft:spruce_slab
fill 25 65 17 25 66 17 minecraft:air
setblock 24 65 20 minecraft:green_bed[part=foot,facing=south]
setblock 24 65 21 minecraft:green_bed[part=head,facing=south]
setblock 20 65 19 minecraft:lantern[hanging=false,waterlogged=false]
setblock 18 67 24 minecraft:glass_pane
setblock 32 67 24 minecraft:glass_pane

# Workshop and intentionally accessible ingredients for the Echo Lens recipe.
fill 17 64 -7 33 64 8 minecraft:stone_bricks
fill 17 65 -7 33 69 8 minecraft:spruce_planks
fill 18 65 -6 32 69 7 minecraft:air
fill 17 70 -7 33 72 8 minecraft:spruce_slab
fill 24 65 -7 24 66 -7 minecraft:air
setblock 20 65 0 minecraft:barrel[facing=south]
setblock 19 65 0 minecraft:crafting_table
setblock 22 65 0 minecraft:stonecutter
item replace block 20 65 0 container.0 with minecraft:copper_ingot 16
item replace block 20 65 0 container.1 with minecraft:glass_pane 16
item replace block 20 65 0 container.2 with minecraft:amethyst_shard 8
item replace block 20 65 0 container.3 with minecraft:stone_bricks 16
item replace block 20 65 0 container.4 with minecraft:calcite 16
setblock 24 65 1 minecraft:oak_planks
setblock 24 66 1 minecraft:barrel[facing=south]

# Mine yard, chamber envelope, carved interior and a west-passage reading nook.
fill -9 63 -32 10 63 -18 minecraft:cobblestone
fill -9 64 -32 10 68 -18 minecraft:air
fill -14 54 -44 9 62 -26 minecraft:deepslate
fill -7 55 -42 2 60 -28 minecraft:air
fill -13 55 -37 -8 56 -35 minecraft:air

# Two-wide nine-step descent from the mine yard into the chamber, with three air blocks overhead.
fill -1 63 -32 0 63 -32 minecraft:cobblestone
fill -1 64 -32 0 66 -32 minecraft:air
fill -1 62 -33 0 62 -33 minecraft:cobblestone
fill -1 63 -33 0 65 -33 minecraft:air
fill -1 61 -34 0 61 -34 minecraft:cobblestone
fill -1 62 -34 0 64 -34 minecraft:air
fill -1 60 -35 0 60 -35 minecraft:cobblestone
fill -1 61 -35 0 63 -35 minecraft:air
fill -1 59 -36 0 59 -36 minecraft:cobblestone
fill -1 60 -36 0 62 -36 minecraft:air
fill -1 58 -37 0 58 -37 minecraft:cobblestone
fill -1 59 -37 0 61 -37 minecraft:air
fill -1 57 -38 0 57 -38 minecraft:cobblestone
fill -1 58 -38 0 60 -38 minecraft:air
fill -1 56 -39 0 56 -39 minecraft:cobblestone
fill -1 57 -39 0 59 -39 minecraft:air
fill -1 55 -40 0 55 -40 minecraft:cobblestone
fill -1 56 -40 0 58 -40 minecraft:air

setblock -5 60 -40 minecraft:lantern[hanging=true]
setblock 0 60 -30 minecraft:lantern[hanging=true]
setblock -12 55 -35 minecraft:lantern

# M3 content is a clue, never an automated event. The solid east-wall seam is left as deepslate.
setblock 2 57 -37 quiet_below:resonance_marker[facing=west]
setblock -12 55 -36 minecraft:lectern[facing=south,has_book=false]

# Public and environmental copies of the three original clues. 1.21.5+ uses
# native text components in sign messages; no written-book component syntax is required.
setblock -2 65 6 minecraft:oak_sign[rotation=0]{front_text:{color:'white',has_glowing_text:true,messages:[{text:'Surveyors margin'},{text:'A line appeared'},{text:'where yesterday'},{text:'was only stone.'}]}}
setblock 0 65 6 minecraft:oak_sign[rotation=0]{front_text:{color:'white',has_glowing_text:true,messages:[{text:'Keep supports'},{text:'Weight moves'},{text:'slow and patient.'},{text:'Bring reasons.'}]}}
setblock 2 65 6 minecraft:oak_sign[rotation=0]{front_text:{color:'white',has_glowing_text:true,messages:[{text:'Valley remembers'},{text:'pressure.'},{text:'Choice must stand'},{text:'in daylight.'}]}}
setblock 1 55 -37 minecraft:oak_sign[rotation=0]{front_text:{color:'white',has_glowing_text:true,messages:[{text:'No pick made this.'},{text:'Vibration came'},{text:'through the rock.'},{text:'Warning or door?'}]}}
setblock 23 65 1 minecraft:oak_sign[rotation=0]{front_text:{color:'white',has_glowing_text:true,messages:[{text:'Do we shore it?'},{text:'Do we leave it?'},{text:'Decide together.'},{text:'Not just tools.'}]}}
setblock -11 55 -36 minecraft:oak_sign[rotation=0]{front_text:{color:'white',has_glowing_text:true,messages:[{text:'Spoke kindly:'},{text:'sound changed.'},{text:'Taking all:'},{text:'sound changed too.'}]}}
data modify storage chronicle_pilot:setup built set value 1b
