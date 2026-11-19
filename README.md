# ParkourCombat 2.0

Independent Forge add-on for Minecraft 1.20.1 that will build on ParCool parkour actions and Better Combat melee combat.

## Current status

**Scaffold stage only.** This repository currently contains:

- minimal Forge 47.4.0 / Minecraft 1.20.1 / Gradle 8.8 / JDK 17 project;
- the main mod class and mod metadata;
- locked local dependency JARs in `libs/` (unmodified audited copies);
- project documentation and build/test records.

It does **not** contain:

- the ParCool adapter;
- any combat, damage, affix or network logic;
- any Mixin;
- any changed or repackaged third-party JAR.

## Fixed environment (locked)

| Component | Version |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.0 |
| Java | 17 |
| Gradle | 8.8 |
| ParCool | 3.4.3.3 |
| Better Combat Forge | 1.9.0+1.20.1 |
| Mod ID | `parkourcombat` |
| Package | `com.MrGhost.parkourCombat` |

ParCool and Better Combat versions must never be changed, replaced or repackaged.
See `docs/DEPENDENCIES.md` for file names, original paths and SHA-256 hashes.

## Build

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat compileJava
.\gradlew.bat build
```

The Gradle daemon is pinned to JDK 17 by `org.gradle.java.home` in `gradle.properties`.

## Test runs

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat runServer
.\gradlew.bat runClient
```

Acceptance status is recorded in `docs/SCAFFOLD_ACCEPTANCE.md`.
Client gameplay is **not** verified by the scaffold stage.

## Layout

```
libs/                                  locked third-party JARs (copies, unmodified)
src/main/java/com/MrGhost/parkourCombat/ParkourCombat.java
src/main/resources/META-INF/mods.toml
src/main/resources/pack.mcmeta
docs/DEPENDENCIES.md                   file names, versions, SHA-256
docs/SCAFFOLD_ACCEPTANCE.md            build/run evidence and NOT TESTED items
docs/logs/                             captured build and run logs
```

## Rules

The project development rules are supplied by the project owner (`AI_PROJECT_RULES.md` is not yet present in this repository).
Do not start the next phase without explicit approval.
