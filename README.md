# Salmon

<p align="center">
  <strong>A performance-focused Minecraft server for busy worlds, long-running servers, and plugin-heavy communities.</strong>
</p>

<p align="center">
  <a href="https://github.com/firesmasher-c6/Salmon/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/firesmasher-c6/Salmon/build.yml?branch=dev%2F26.2&label=build" alt="Build status"></a>
  <a href="https://github.com/firesmasher-c6/Salmon/releases/latest"><img src="https://img.shields.io/github/v/release/firesmasher-c6/Salmon?label=latest%20release" alt="Latest Salmon release"></a>
  <a href="https://github.com/firesmasher-c6/Salmon"><img src="https://img.shields.io/github/license/firesmasher-c6/Salmon?label=license" alt="GPLv3 license"></a>
  <img src="https://img.shields.io/badge/Minecraft-1.21.11-3C8527?logo=minecraft&logoColor=white" alt="Minecraft 26.2 (dev)">
  <img src="https://img.shields.io/badge/Java-21+-ED8B00?logo=openjdk&logoColor=white" alt="Java 25 or later">
</p>

**Salmon `26.2-2e-001` is the development line based on Paper 26.2.** Salmon keeps its Paper-compatible server foundation while moving performance and packaging work onto the newer Paper base. It keeps the Paper-compatible server and
plugin foundation while adding Salmon-branded configuration, optimization
controls, diagnostics, and a plugin API.

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
- **Opt-in plugin execution metrics** for event handlers and Bukkit scheduled
  tasks. They are disabled by default because timing collection adds overhead;
  Salmon does not kill, restart, or rewrite plugins.
- **Chunky pregeneration setup** can download a compatible Chunky plugin from
  Modrinth before Paper discovers plugins. Paper loads the plugin normally;
  Salmon does not run pregeneration itself.
- **Modrinth plugin services** search and install Paper/Spigot plugin projects,
  verify downloaded JAR checksums, and install required plugin dependencies.
  Installed plugins require a server restart.
- **SalmonAPI** provides plugin helpers for asynchronous work, server-thread
  scheduling, and plugin lookups.
- **A dedicated `SalmonMC Thread`** for background-safe maintenance and
  optional plugin-metric reporting. World changes and commands stay on the
  Minecraft server thread.
- **The `/salmon` command** for status and Modrinth plugin services.
- **GitHub release checks** for `/ver` (and at startup), reporting whether the
  running Salmon build is current, outdated, or ahead of the latest published
  release.
- **MOTD Builder and `/smp`** for configurable server-list messages and
  maintenance mode with a dedicated name/UUID allowlist.
- **Redstone Optimization** using Paper's Alternate Current engine for much
  faster redstone dust updates on large SMP circuits.

Salmon does not promise that every third-party plugin is bug-free or compatible.
Test plugin updates and configuration changes on a copy of your server before
deploying them to a live world.

## Get started

### Run a Salmon server

Use a Salmon server bundle from a trusted Salmon release or build one from this
repository. The distributable produced by the build is a Paperclip-style bundle
in `paper-server/build/libs/`.

1. Install a **Java 25 or later** runtime.
2. Place the bundled server JAR in its own server directory.
3. Start it, review the generated files, and set `eula=true` in `eula.txt`
   after accepting the [Minecraft EULA](https://www.minecraft.net/eula).
4. Start the server again and tune Salmon in `salmon.yml` and
   `config/salmon-opts.yml`.

Salmon's current version is **Minecraft `1.21.11`, build `3r-009`**. Rename the
runnable server bundle to `salmon-1.21.11-3r-009.jar` and start it from your
server directory:

```sh
java -Xms1G -Xmx4G -jar salmon-1.21.11-3r-009.jar --nogui
```

> **Development notice:** `3r-009` is planned to be Salmon's final build for
> October 2026. Feature and optimization work is paused until November 1, 2026,
> when development is scheduled to resume. Necessary bug fixes and patches may
> still be made during the pause.

Keep the server JAR, world, and configuration files in the same server
directory. Make backups before changing optimization settings or running
world-affecting tools on a world you care about.

### First-start configuration

Salmon creates these files in the server directory when they are missing:

| File | Purpose |
| --- | --- |
| `salmon.yml` | Master switch, MOTD templates, and maintenance allowlist |
| `config/salmon-opts.yml` | Active optimization and plugin-metric settings |
| `config/salmon-defaults.yml` | Reference/default values for optimization settings |

The current starter chunk caps are **16 primed TNT**, **32 hostile mobs**,
**64 passive creatures**, **48 water creatures**, and **16 ambient creatures
per chunk**. Set an individual cap to `0` to disable that cap. New matching
spawns are rejected at the limit; existing entities are not culled by spawn
caps.

Persistent-mob cleanup is off by default because it permanently removes
entities. Plugin metrics are off by default because collecting plugin timings
adds overhead; enable them only when diagnosing performance. They do not
control plugin lifecycle. Existing servers that already have
`plugins.metrics.enabled: true` in `config/salmon-opts.yml` must change it to
`false` to opt out; explicit existing settings are preserved.
Redstone uses Alternate Current by default for high-throughput dust updates.
It changes dust update order, so location-dependent or timing-sensitive
contraptions may behave differently. Select `vanilla` when vanilla update
behavior is more important than maximum redstone throughput.
Configuration changes require a server restart. Salmon copies templates only
when a file is absent; it does not overwrite existing configuration files
after an update.

### Salmon commands

Run these as an operator (or grant the `salmon.command` permission):

```text
/salmon
/salmon status
/salmon lagreport
/salmon modrinth search <query>
/salmon modrinth install <slug>
```

`/salmon lagreport` prints a read-only, on-demand snapshot of Paper's existing
TPS/MSPT statistics, loaded entity and chunk counts, and plugin timing metrics
if they are enabled. It does not run continuously or add per-tick tracking.
`/ver` checks Salmon's latest published GitHub release instead of Paper's build
feed; the network request runs asynchronously, is cached for 12 minutes, and
reports local builds that are ahead of the published release.

Modrinth searches and downloads run on dedicated Salmon worker threads, not the
Minecraft tick thread. Plugin JARs are validated and installed in the server's
plugins directory; restart to load them. Installing third-party code carries
the same trust risks as manually adding a plugin.

Chunky auto-install can be disabled with
`plugins.chunky-pregeneration.enabled: false` in `config/salmon-opts.yml`.
Modrinth commands can be disabled with `plugins.modrinth.enabled: false`.
Chunky preparation runs its network and file operations on a dedicated worker
before Paper's plugin discovery; startup waits for completion so Paper does not
race an unfinished plugin download.

Redstone is configured with `redstone.implementation` in
`config/salmon-opts.yml`:

```yaml
redstone:
  implementation: alternate_current
```

Available modes are `alternate_current` (fastest bundled redstone dust engine),
`eigencraft`, `vanilla`, and `paper` (respect each world's Paper configuration).
Salmon applies the selected engine to all worlds at startup and when Paper
configurations are reloaded.

### MOTD Builder and SMP maintenance

Configure `motd.lines` and `motd.maintenance-lines` in `salmon.yml`; these
templates override `server.properties`, support `&` color codes, and replace
`{online}` and `{max}`. Apply edits without restarting with `/smp motd reload`.
When maintenance mode is enabled, the maintenance MOTD is shown and only players
in the dedicated maintenance allowlist can join. This allowlist is separate
from Minecraft's regular whitelist; add staff before turning maintenance on.

```text
/smp
/smp maintenance on
/smp maintenance off
/smp maintenance whitelist add <uuid|playername>
/smp maintenance whitelist rm <uuid|playername>
/smp maintenance whitelist list
/smp motd reload
```

### Salmon plugin API

Plugins can compile against Salmon's Paper API and use
`dev.firesmasher.salmon.plugins.api.SalmonAPI` for background work and server
thread scheduling:

```java
import dev.firesmasher.salmon.plugins.api.SalmonAPI;

SalmonAPI api = SalmonAPI.get();
api.supplyAsync(this, () -> loadLargeIndex())
    .thenAccept(index -> api.runOnServerThread(this, server -> applyIndex(index)));
```

Do not access live Bukkit objects from the background task; return immutable
data and apply changes through `runOnServerThread`. SalmonAPI also provides
next-tick and delayed/repeating task scheduling, loaded-plugin lookups, and
console command dispatch. SalmonAPI is Salmon-specific and is not present on
unmodified Paper servers.

### Salmon plugin descriptor

Paper plugins can opt into Salmon-specific APIs and the Salmon category in
`/plugins` by using `salmon-plugin.yml` in the JAR root. Its configuration
uses the same schema, loader, dependency declarations, bootstrapper, and
classloading behavior as `paper-plugin.yml`:

```yaml
name: ExampleSalmonPlugin
version: 1.0.0
main: example.plugin.ExamplePlugin
api-version: '1.21'
```

`salmon-plugin.yml` is optional. Existing `paper-plugin.yml` and `plugin.yml`
plugins retain their Paper and Bukkit categories and loading behavior. If a
JAR contains multiple descriptors, Salmon's descriptor takes precedence and
the plugin is listed under **Salmon Plugins**.

## Build Salmon from source

### Requirements

- Git
- Java 25 or later
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
  paper-server/build/libs/salmon-1.21.11-3r-009.jar
```

The release identity is **Minecraft `1.21.11`, build `3r-009`**. Keep the
filename consistent in your release notes and server launch command.
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

## Development status (26.2)

- `dev/26.2` is the active development line for Paper 26.2-based Salmon builds.
- Version label for this line: `26.2-2e-001` (development, not a supported release yet).
- Upcoming public builds will be tagged after compile, startup, and regression testing.
- Please use `dev/26.2` for validation and test feedback, not production deployments.
