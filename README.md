# Easy Gamemode

Client-side Fabric mod for Minecraft **1.21.11**. Press **G** to open a GUI that changes any online player's gamemode (or everyone's) with one click. Nothing is installed on the server.

- **EVERYONE** row at the top: one click sets all online players (`/gamemode <mode> @a`)
- One row per online player with head, name and four buttons: Survival, Creative, Adventure, Spectator
- Active gamemode highlighted, taken from the server's player list (never assumed)
- Search bar, scrollable list, auto-refresh on join/leave
- Uses the normal `/gamemode` command, so the server's permission checks apply (OP required)
- Rejected commands show a short error in the GUI
- Keybind (default `G`) is changeable in Controls; press it again to close

## Build

Requires JDK 21.

```
gradle build
```

The jar appears in `build/libs/` (use the one without `-sources`). GitHub Actions also builds it on every push: open the run and download the `easy-gamemode` artifact.

## Install

Put the jar and [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder (Fabric Loader 0.18+).
