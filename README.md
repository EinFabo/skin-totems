# Skin Totems

Fabric mod for Minecraft **26.2** (client-side only): every Totem of Undying shows the skin of the
player holding it, either as a flat skin totem in the style of
[Custom Totem Generator](https://github.com/EinFabo/Custom-Totem-Generator) (a 1:1, pixel-exact port of
`generate_totem_image`) or as a little 3D **plushie**. It works on any server, and other players
don't need the mod.

- Every player (and mannequin) holds a totem with their own skin
- Your own hotbar, inventory, totem pop animation and advancement toasts show your skin
- Dropped totems, item frames, armor stands and mobs keep the regular totem

Download: [Modrinth](https://modrinth.com/mod/skin-totems)

## Settings

Open them via Mod Menu (gear next to "Skin Totems") or a keybind (*Controls → Miscellaneous*,
unbound by default). There is a live preview of your own player, and everything is stored in
`config/skintotems.json`.

- Style: Totem / Plushie
- Where to show it: other players, own hand, inventory, totem animation
- Totem: hat layer, outline
- Plushie: second skin layer, size (50–150 %)

## Resource packs

| Pack contains | Effect |
|---|---|
| `assets/minecraft/models/item/totem_of_undying.json` (`display`) | size/position also applies to skin totems |
| `assets/skintotems/textures/totem/outline.png` | custom outline around skin totems (32x32; other sizes are scaled) |
| `assets/minecraft/textures/item/totem_of_undying.png` | only the regular totem (dropped etc.); skin totems stay |
| `assets/minecraft/items/totem_of_undying.json` | replaces the item definition and turns skin totems off |
| `assets/skintotems/models/item/plushie.json` (`display`) | size/position of the plushie |
| `assets/skintotems/plushie/plushie.json` | plushie shape (cuboids with player-model skin UVs, see `PlushieGeometry`) |

The mod adds the item model type `skintotems:player_totem`:

```json
{ "model": {
    "type": "skintotems:player_totem",
    "base": "minecraft:item/totem_of_undying",
    "plushie_base": "skintotems:item/plushie",
    "fallback": { "type": "minecraft:model", "model": "minecraft:item/totem_of_undying" } } }
```

## Building & testing

```bash
./gradlew build                                   # jar: build/libs/skintotems-<version>.jar, runs unit tests
tools/run-gametest.sh                             # real client, hidden (Xvfb :99) and muted;
                                                  # screenshots in build/run/clientGameTest/screenshots
tools/run-gametest.sh -PplushieOnly               # plushie and settings shots only
tools/run-gametest.sh -Pshowcase                  # 1920x1080 gallery shots
tools/run-gametest.sh -PforceSkinDownload         # tests the fallback that downloads skins itself
python3 tools/make_reference_totems.py            # regenerate reference images with the original Python code
python3 tools/render_plushie.py SKIN.png OUT.png  # render the plushie as a crisp PNG (icon, artwork)
```

Requires Java 25 (`org.gradle.java.home` in `gradle.properties`).

## License

[MIT](LICENSE) © EinFabo
