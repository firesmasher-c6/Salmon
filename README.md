# Salmon

<p align="center">
  <strong>A performance-focused Minecraft server for busy worlds, long-running servers, and plugin-heavy communities.</strong>
</p>

<p align="center">
  <a href="https://github.com/firesmasher-c6/Salmon/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/firesmasher-c6/Salmon/build.yml?branch=main&label=build" alt="Build status"></a>
  <a href="https://github.com/firesmasher-c6/Salmon"><img src="https://img.shields.io/github/license/firesmasher-c6/Salmon?label=license" alt="GPLv3 license"></a>
  <img src="https://img.shields.io/badge/Minecraft-1.21.11-3C8527?logo=minecraft&logoColor=white" alt="Minecraft 1.21.11">
  <img src="https://img.shields.io/badge/Java-21+-ED8B00?logo=openjdk&logoColor=white" alt="Java 21 or later">
</p>

Salmon is a Minecraft Java server project based on Paper. It keeps the
Paper-compatible server and plugin foundation while adding Salmon-branded
configuration, optimization controls, diagnostics, and startup scripts.

> Salmon's optimization features are designed to be configurable and
> observable. Spawn caps can reject new entities; optional cleanup can remove
> entities permanently. Read the configuration comments and back up your world
> before enabling behavior that changes it.

## What Salmon adds

- **Per-chunk entity caps** for primed TNT and hostile, passive, water, and
  ambient mobs. The caps apply to new spawns; they do not remove entities
  already in a world.
- **Opt-in persistent hostile-mob cleanup**, with age, entity-type, player
  distance, and entity-protection controls. It is disabled by default.
- **Plugin execution metrics** for event handlers and Bukkit scheduled tasks.
  Salmon reports slow calls; it does not kill, restart, or rewrite plugins.
- **A dedicated `SalmonMC Thread`** for background-safe work such as script
  discovery and metric aggregation. World changes and commands are handed to
  the Minecraft server thread.
- **Startup `.slms` scripts** for trusted, English-like commands and block
  changes. Scripts run with console authority; treat them like executable
  server configuration.
- **The `/salmon` command** for status and script reload operations.

Salmon does not promise that every third-party plugin is bug-free or compatible.
Test plugin updates and configuration changes on a copy of your server before
deploying them to a live world.

## Get started

### Run a Salmon server

Use a Salmon server bundle from a trusted Salmon release or build one from this
repository. The distributable produced by the build is a Paperclip-style bundle
in `paper-server/build/libs/`.

1. Install a **Java 21 or later** runtime.
2. Place the bundled server JAR in its own server directory.
3. Start it, review the generated files, and set `eula=true` in `eula.txt`
   after accepting the [Minecraft EULA](https://www.minecraft.net/eula).
4. Start the server again and tune Salmon in `salmon.yml` and
   `config/salmon-opts.yml`.

Name the runnable server bundle `salmon-1.21.11-1e.jar` (`1e` means
**First Build, Experimental**) and start it from your server directory:

```sh
java -Xms1G -Xmx4G -jar salmon-1.21.11-1e.jar --nogui
```

Keep the server JAR, world, and configuration files in the same server
directory. Make backups before changing optimization settings or running
scripts on a world you care about.

### First-start configuration

Salmon creates these files in the server directory when they are missing:

| File | Purpose |
| --- | --- |
| `salmon.yml` | Master Salmon enable/disable switch |
| `config/salmon-opts.yml` | Active optimization and plugin-metric settings |
| `config/salmon-defaults.yml` | Reference/default values for optimization settings |
| `salmon-scripts/` | Trusted `.slms` scripts and their README |

The current starter chunk caps are **16 primed TNT**, **32 hostile mobs**,
**64 passive creatures**, **48 water creatures**, and **16 ambient creatures
per chunk**. Set an individual cap to `0` to disable that cap. New matching
spawns are rejected at the limit; existing entities are not culled by spawn
caps.

Persistent-mob cleanup is off by default because it permanently removes
entities. Plugin metrics are observational and do not control plugin lifecycle.
Configuration changes require a server restart. Salmon copies templates only
when a file is absent; it does not overwrite existing configuration files
after an update.

### Salmon commands

Run these as an operator (or grant the `salmon.command` permission):

```text
/salmon
/salmon status
/salmon scripts reload
```

`/salmon scripts reload` reads and runs scripts again. Commands in scripts run
as the console, so reloading may repeat commands with lasting effects.

### Salmon scripts

Scripts are plain-text `.slms` files in `salmon-scripts/`. They execute once
at startup, in filename order, and require a metadata block. Script commands
run with console authority, so only use files you trust.

```slms
meta(
    name: "Welcome",
    version: 1.0.0,
    author: "ServerAdmin"
)

run command say Welcome to the server!
set block minecraft:stone at 0 64 12 in world world
```

The implemented instructions are `run command ...` and
`set block <namespace:block> at <x> <y> <z> in world <world>`. Block
instructions can generate chunks and apply changes on the server thread.
See **[SALMON-SCRIPTS.md](SALMON-SCRIPTS.md)** for the full syntax dictionary,
examples, limits, and troubleshooting guide.

## Build Salmon from source

### Requirements

- Git
- Java 21 or later
- An internet connection for Gradle and Minecraft build dependencies

### Build the server bundle

```sh
git clone https://github.com/firesmasher-c6/Salmon.git
cd Salmon
./gradlew applyPatches
./gradlew createMojmapBundlerJar
```

The runnable bundled server is written to `paper-server/build/libs/` with a
generated `paper-bundler-*-mojmap.jar` filename. Rename that bundle to the
release filename before distributing or running it:

```sh
mv paper-server/build/libs/paper-bundler-1.21.11-R0.1-SNAPSHOT-mojmap.jar \
  paper-server/build/libs/salmon-1.21.11-1e.jar
```

Here, `1e` identifies the **first experimental build** for Minecraft 1.21.11.
For subsequent builds, increment the build number/suffix you choose and keep
the filename consistent in your release notes and server launch command.
The plain `Salmon-*.jar` output is not the bundled server distribution; rename
and use the `paper-bundler-*-mojmap.jar` artifact. Building the project does
not start the server.

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Compatibility and upstream

Salmon is based on [Paper](https://github.com/PaperMC/Paper), which provides
the underlying server and Bukkit/Paper plugin platform. Salmon-specific
features and branding are maintained in this repository. Paper API packages,
plugin compatibility details, and upstream behavior may still use Paper names.

For information about the upstream API, see
[Paper's documentation](https://docs.papermc.io/paper/) and the
[Paper API project](https://github.com/PaperMC/Paper/tree/main/paper-api).

## Project links

- [GitHub repository](https://github.com/firesmasher-c6)
- [Website](https://codestuff.pages.dev) — deprecated
- [Guns.lol](https://guns.lol/firesmasher)
- [YouTube](https://youtube.com/@FireSmasher)
- [TikTok](https://www.tiktok.com/@FireSmasher_C6)
- [TalonAI](https://talonai.wisp.uno)

## Credits and license

- **Lead Architect:** FireSmasher
- **Lead Developer:** FireSmasher
- **Lead Programmer:** GitHub Copilot
- **Publisher:** FireSmasher
- **License:** [GPLv3](LICENSE)

Salmon is built on Paper; see the upstream project's
[repository](https://github.com/PaperMC/Paper) for its contributors and
project details.
