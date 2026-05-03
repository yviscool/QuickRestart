# QuickRestart for Slay the Spire 1

Personal preservation repo for a customized `QuickRestart` mod for Slay the Spire 1.

This repo keeps the current source code, resources, and build metadata so the mod is not lost again.

## Origin

- Upstream project: `erasels/QuickRestart`
- This repo stores a locally customized branch used for Slay the Spire 1

## What This Build Adds

- Separate room snapshots instead of relying only on the live `autosave`
- Room restart support for normal fights, elites, bosses, events, victory/boss reward flow, and shops
- Better handling for A20 Act 3 double-boss / post-boss continuation cases
- Bootstrap room snapshots for existing in-progress saves
- Configurable hotkeys:
  - latest checkpoint restart
  - manual checkpoint save
  - room-start restart
- Configurable top-center status text:
  - show/hide hotkey hint
  - show/hide checkpoint state
  - vertical offset
  - text scale
- Safer startup and render behavior for early room-load timing
- Fixed `ReturnToMenuButtonPatches` crash caused by fragile patch parameter name injection

## Current Hotkey Defaults

- `F5`: restart latest checkpoint
- `Shift+F5`: save manual checkpoint
- `Ctrl+F5`: restart room-start checkpoint

All three are configurable in the in-game mod settings panel.

## Repository Layout

- `src/main/java`: mod source
- `src/main/resources`: `ModTheSpire.json`, localization, image assets
- `pom.xml`: legacy Maven project file kept for source preservation

## Build Notes

This project uses local Slay the Spire / ModTheSpire / BaseMod jars.

### Maven

1. Edit `Steam.path` in `pom.xml`
2. Make sure the required jars exist under your local Slay the Spire install
3. Run:

```bash
mvn package
```

### Local ECJ Build Used During Recovery

The current jar in the local repair workflow was rebuilt with:

```bash
java -jar ecj.jar -g -1.8 -cp desktop-1.0.jar:ModTheSpire.jar:BaseMod.jar -d build/classes $(find src/main/java -name '*.java')
cd build/classes
zip -qr quickRestart.jar quickRestart
```

## Install

Copy the built `quickRestart.jar` into your Slay the Spire `mods/` directory.

## Tested Environment

- Java `1.8.0_131`
- ModTheSpire `3.30.3`
- BaseMod `5.56.0`
- Slay the Spire 1 desktop build used in local recovery workflow

## Notes

- This repo is intended as a preservation source repo, not an official upstream mirror.
- Some modded custom room types may still need extra whitelist support if they should participate in room snapshots.
