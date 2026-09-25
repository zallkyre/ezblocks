# EZ Blocks

Makes structure blocks easier to use by replacing their GUI with a better one.

## The problem

The vanilla structure block screen is annoying. It is one dropdown of every
structure in your world, and once you pick one that is it. No search, and you
have to remember the exact name of what you saved three worlds ago.

## What it does

Right click a structure block to open the EZ Blocks browser. From there you can
search everything you have saved, set the size, and save it back out. You never
have to remember a name.

| Control | What it does |
| --- | --- |
| Search box | Type part of a structure name to filter the list |
| Structure list | Click a structure to select it. Click again to clear |
| Size boxes | Change the X, Y and Z size of the block |
| `Auto-Size` | Fills the size boxes with whatever the selected structure is |
| `Save` | Writes the structure back to the block |
| `Save & Place` | Writes it and drops a structure block into your inventory |

### Block mode

If the block is already loaded with a structure, `Save & Place` and the browser
shortcut both act on that structure directly.

## Other controls

| Input | What it does |
| --- | --- |
| Right click a structure block | Open the browser |
| Right click a loaded structure block | Save the structure |
| `Esc` | Close the browser, or go back to the vanilla screen from block mode |

## Requirements

- Minecraft `26.3`
- Fabric Loader `0.19.5` or newer
- Fabric API
- Java 25

Works on both the client and a dedicated server. Everyone in a multiplayer game
needs it installed.

## Build it yourself

```
./gradlew build
```

The finished jar is in `build/libs/`.

To play it in a dev environment, run `./gradlew runClient`.

## License

MIT. See `LICENSE`.
