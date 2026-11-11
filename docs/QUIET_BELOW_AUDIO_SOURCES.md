# Quiet Below bundled Event audio sources

Quiet Below ships eight short Event clips inside its mod resources. They are local Ogg
Vorbis files, not runtime streams: the Freesound URLs below are provenance only. The
files are the publicly accessible preview-quality Ogg downloads inspected on 2026-11-03,
with no audio edits or transcoding. Their attribution is also included in the packaged
`quiet_below-sound-credits.txt`; neither that notice nor the files are covered by the
Chronicle code license.

| Event ID | Source and author | License | SHA-256 |
| --- | --- | --- | --- |
| `quiet_below:event.sinkhole_opening` | [Landmass / Earth Rumble — el-bee](https://freesound.org/people/el-bee/sounds/363122/) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | `77fd3fe736c6f38f999a71db2123635457b39e0e0631f208f54c6fd80673dffa` |
| `quiet_below:event.sculk_awakening` | [Dark Horn Blast — hykenfreak](https://freesound.org/people/hykenfreak/sounds/218072/) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | `7656c9baa98c6b44026b533d33c6892b1f7810c6aedcb8747dc0b5cc381bf636` |
| `quiet_below:event.whisper_from_below` | [What the frick W fx.wav — .Andre_Onate](https://freesound.org/people/.Andre_Onate/sounds/482658/) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | `70e4a430a65436a4f3c85ffa839e0b5f48e4a6cec7132d925c9c0cd07a097f93` |
| `quiet_below:event.city_discovery` | [Reversed Discovery Sound.wav — DeVern](https://freesound.org/people/DeVern/sounds/472587/) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | `aaf203debfe71e1d29078ca0b638f0f893563b05939fe6f4ef20f0b7d7972641` |
| `quiet_below:event.station_awakened` | [Eerie Moment — miksmusic](https://freesound.org/people/miksmusic/sounds/497708/) | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | `7b734cb43f2dac4088b55dcf979aba1c4b880fcff277dacd1882717b0783339e` |
| `quiet_below:event.altar_offering` | [Broken bell.wav — Physon712](https://freesound.org/people/Physon712/sounds/580871/) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | `8b279a21c38cc408f50e77ac7e3953ffe844d809333eeaf7890f0f71a8f7c846` |
| `quiet_below:event.sculk_growth` | [Plant Growing — IENBA](https://freesound.org/people/IENBA/sounds/648958/) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | `2a6593454936ce434a81d14a5b4c31c7347506f665ff01e66d7dff0785f24c72` |
| `quiet_below:event.heart_destroyed` | [bittersweet.wav — joanne_pang](https://freesound.org/people/joanne_pang/sounds/841957/) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | `515f60044edf81380ff31e31bab19f2ea4d2e783910b5a72a347c4951a614b96` |

The `sinkhole_opening` source page requests credit for Arto Koivisto and lists the
derivative source entries [Mnslugger20/259758](https://freesound.org/people/Mnslugger20/sounds/259758/)
and [prozaciswack/82722](https://freesound.org/people/prozaciswack/sounds/82722/). The
`station_awakened` credit includes: “Sound effects by TunePocket – Unlimited royalty free
music and sound effects for video creators”.

Run `python scripts/bundled_audio.py` for source resources, or pass one or more built
Quiet Below jar paths to check their packaged copies offline. The validator enforces the
fixed eight-entry schema, safe paths, Ogg Vorbis (not Opus), hashes, registry/subtitle
links, credits, and the resource size bounds (256 KiB manifest, 128 sounds, 8 MiB/file,
64 MiB total).
