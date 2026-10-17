# Salmon 1.21.11 — 1r-007

<p align="center">
  <a href="https://github.com/firesmasher-c6/Salmon/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/firesmasher-c6/Salmon/build.yml?branch=ver%2F1.21.11&label=build" alt="Build status"></a>
  <a href="https://github.com/firesmasher-c6/Salmon/releases/tag/1r-007"><img src="https://img.shields.io/badge/release-1r--007-167DCE" alt="Release 1r-007"></a>
  <img src="https://img.shields.io/badge/Minecraft-1.21.11-3C8527?logo=minecraft&logoColor=white" alt="Minecraft 1.21.11">
  <img src="https://img.shields.io/badge/Java-21%2B-ED8B00?logo=openjdk&logoColor=white" alt="Java 21 or later">
  <a href="https://github.com/firesmasher-c6/Salmon"><img src="https://img.shields.io/github/license/firesmasher-c6/Salmon?label=license" alt="GPLv3 license"></a>
</p>

**1r-007 is the first stable release of Salmon.** It combines the Salmon
optimizations and server features developed for Minecraft 1.21.11, with plugin
timing metrics disabled by default.

## Download

Download **[`salmon-1.21.11-1r-007.jar`](https://github.com/firesmasher-c6/Salmon/releases/download/1r-007/salmon-1.21.11-1r-007.jar)**.
This is the runnable Paperclip server bundle.

## Highlights

- First stable Salmon release, based on Paper for Minecraft Java Edition
  `1.21.11`.
- Chunk entity spawn limits, optional persistent-mob cleanup, selectable
  redstone implementations, MOTD Builder, SMP maintenance mode, and Salmon
  server commands.
- Plugin timing metrics are **off by default** because collection adds
  overhead to plugin event handlers and scheduled tasks. Operators may
  temporarily enable them in `config/salmon-opts.yml` to diagnose performance.

## Features and optimizations

### Chunk spawn limits

Salmon can cap the number of newly spawned entities in a chunk:

| Entity group | Default cap per chunk |
| --- | ---: |
| Primed TNT | 16 |
| Hostile entities | 32 |
| Passive creatures | 64 |
| Water creatures | 48 |
| Ambient creatures | 16 |

The caps reject new matching spawns after the configured limit; they do not
remove entities already present. Configure limits in
`config/salmon-opts.yml`. Set an individual cap to `0` to disable it.

### Persistent-mob cleanup

Optional periodic cleanup can remove configured old, persistent hostile mobs.
It is **disabled by default** and includes controls for age, entity types,
maximum removals per scan, player distance, names, tamed or leashed mobs, and
passengers. Removal is permanent: back up worlds and review all settings before
enabling it.

### Redstone optimization

The default redstone implementation is Paper's **Alternate Current**, selected
for high-throughput redstone dust updates. It changes dust update order, so
location-dependent or timing-sensitive contraptions may behave differently.
Available choices are `alternate_current`, `eigencraft`, `vanilla`, and `paper`.
Choose `vanilla` when vanilla update behavior matters more than throughput.

### Plugin timing metrics

Metrics for plugin event handlers and Bukkit scheduler tasks are **disabled by
default**. Timing each call adds work to plugin execution and may affect
performance, especially on busy servers. Enable them only when actively
investigating slow plugin calls; Salmon reports measurements and does not kill,
restart, or rewrite plugins.

Existing installations keep their current configuration because Salmon does
not overwrite existing config files. To disable metrics on an upgraded server,
set this value in `config/salmon-opts.yml`:

```yaml
plugins:
  metrics:
    enabled: false
```

### MOTD Builder and maintenance mode

The MOTD configured in `salmon.yml` overrides `server.properties`. Templates
support `&` color codes and `{online}` / `{max}` placeholders. `/smp motd reload`
applies MOTD edits without a restart.

Maintenance mode selects a maintenance MOTD and denies logins to players not
listed in Salmon's separate name/UUID maintenance allowlist. This allowlist is
not Minecraft's regular whitelist. Add staff before enabling maintenance:

```text
/smp maintenance whitelist add <uuid|playername>
/smp maintenance on
/smp maintenance off
/smp maintenance whitelist rm <uuid|playername>
/smp maintenance whitelist list
```

The built-in `/salmon` command provides server status and optional Modrinth
plugin search/install services. `/smp` manages MOTD reload and SMP maintenance.
Both commands support tab completion. Salmon registers these commands after
plugin command lifecycle registrations and refreshes command trees.

### Modrinth and Chunky

Salmon can search and install Paper/Spigot plugins from Modrinth, verify
downloaded JAR checksums, and install required plugin dependencies. New plugin
files require a server restart. Plugin installation runs third-party code:
review the project and its dependencies before installing.

Optional Chunky preparation downloads a compatible Paper Chunky plugin before
Paper discovers plugins. Paper loads Chunky normally; Salmon does not perform
chunk pregeneration itself. Both Modrinth services and Chunky preparation can
be configured in `config/salmon-opts.yml`.

### Salmon plugin API and plugin descriptor

SalmonAPI provides Salmon-specific plugin helpers for asynchronous work,
server-thread scheduling, task scheduling, console command dispatch, and
plugin lookup. It is not available on unmodified Paper. Salmon also supports
the optional `salmon-plugin.yml` descriptor for listing compatible plugins in
the Salmon Plugins category; Bukkit and Paper plugin descriptors remain
supported.

## Install

1. Install Java 21 or later.
2. Place `salmon-1.21.11-1r-007.jar` in an empty server directory.
3. Start it once to create Salmon configuration files.
4. Review `salmon.yml` and `config/salmon-opts.yml`, accept the Minecraft EULA
   in `eula.txt`, then restart.

```sh
java -Xms1G -Xmx4G -jar salmon-1.21.11-1r-007.jar --nogui
```

Configuration templates are copied only when files are absent; updates do not
overwrite existing settings. Review existing settings when upgrading. Back up
worlds before enabling entity cleanup or changing spawn limits for an
established server.

## Development notice

Until **November 1, 2026**, Salmon development is limited to bug fixes and
necessary patches. New features and optimization work are paused while the
project's GitHub Copilot usage allowance resets. As of this release, 73% of
that allowance has been used. Development can resume after the reset.

## Project

- [Source repository](https://github.com/firesmasher-c6/Salmon)
- [All releases](https://github.com/firesmasher-c6/Salmon/releases)
- [GPLv3 license](https://github.com/firesmasher-c6/Salmon/blob/ver/1.21.11/LICENSE)
- [Paper upstream](https://github.com/PaperMC/Paper)

Salmon is an independent Paper-based project and is not an official PaperMC
product.
