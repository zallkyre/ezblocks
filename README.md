# OpenWork Mod

A Structure Block utility for Minecraft 26.3 (Fabric).

Vanilla's Structure Block screen is slow to use for anything but a single hand-picked
structure. OpenWork replaces it with a browser over every structure registered in your
world, plus one-click actions for the operations builders actually repeat.

## Features

- **Instant mode toggle** — right click a Structure Block with an empty hand to flip it
  between `SAVE` and `LOAD`. No screen, no menu.
- **Structure browser** — sneak and right click to open a searchable, paged grid of every
  structure in the world registry, including your datapack additions.
- **Size management** — set the bounding box directly with per-axis `+`/`-` steppers, or
  `Auto-Size` to fit the selection.
- **Save and place** — `Save` marks the current region, `Save & Place` marks it and
  immediately generates the structure.

## Controls

| Input | Result |
| --- | --- |
| Right click (empty hand) | Toggle `SAVE` / `LOAD` |
| Sneak + right click | Open the structure browser |
| `Save` | Save the current region |
| `Save & Place` | Save the region and generate it |
| `Auto-Size` | Fit the bounding box to the selection |

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API
- Java 25 or newer

## Installation

1. Install the Fabric Loader for Minecraft 26.3.
2. Download the matching Fabric API version.
3. Drop `openworkmod-1.0.0.jar` and the Fabric API jar into your `mods` folder.

## Building from source

```sh
./gradlew.bat build
```

The release jar is written to `build/libs/`.

## Notes

- All block edits are performed server side. Clients only send requests.
- Saving a structure block does not save entities within the region.
- This mod replaces the vanilla Structure Block interaction, so the vanilla screen is no
  longer reachable.

## License

MIT
