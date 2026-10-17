# Salmon 1.21.11 — 2r-008

<p align="center">
  <img src="https://img.shields.io/badge/release-2r--008-167DCE" alt="Release 2r-008">
  <img src="https://img.shields.io/badge/Minecraft-1.21.11-3C8527?logo=minecraft&logoColor=white" alt="Minecraft 1.21.11">
  <img src="https://img.shields.io/badge/Java-21%2B-ED8B00?logo=openjdk&logoColor=white" alt="Java 21 or later">
  <a href="https://github.com/firesmasher-c6/Salmon"><img src="https://img.shields.io/github/license/firesmasher-c6/Salmon?label=license" alt="GPLv3 license"></a>
</p>

`2r-008` is the second stable Salmon release. It adds an on-demand, read-only
`/salmon lagreport` command and checks Salmon releases through GitHub's API.
This is planned to be Salmon's final build for October 2026.

## Highlights

- Adds `/salmon lagreport`, available to operators and users with the
  `salmon.command` permission.
- Makes no periodic sampling changes: a report runs only when requested.
- Reads TPS and average MSPT from Paper's existing server statistics.
- Reads the existing Bukkit world entity and loaded-chunk counts.
- Includes plugin timing metrics only when they are enabled; reading metrics
  does not drain or reset their current collection window.
- Keeps plugin timing metrics disabled by default.
- Checks the latest Salmon release through GitHub's API in `/ver` and at
  startup, rather than reporting Paper build updates. Results are cached for
  12 minutes in `/ver`; network checks run asynchronously.
- Removes Salmon Scripts and the `.slms` runtime/API from this build.

## Lag report contents

The command displays:

- 1-minute, 5-minute, and 15-minute TPS.
- The existing Paper 5-second average MSPT.
- World, player, entity, and loaded-chunk counts.
- Up to five highest-total plugin timing entries from the current metrics
  window, when plugin metrics are enabled.

The command is read-only and does not enable plugin metrics. Entity and chunk
counts are a point-in-time view of loaded server data, not a scan of unloaded
chunks. No new tick hooks, hot-loop instrumentation, or background sampling
were added for this feature.

To collect plugin timing entries, an operator must explicitly enable metrics
in `config/salmon-opts.yml`:

```yaml
plugins:
  metrics:
    enabled: true
```

Collecting plugin timings adds overhead to measured plugin handlers and tasks;
leave the setting `false` except when actively diagnosing a problem. The
lagreport can still show TPS/MSPT and world counts while plugin metrics are
disabled.

## Other Salmon features

- Per-chunk spawn caps for primed TNT and hostile, passive, water, and ambient
  entities. These reject new matching spawns; they do not remove entities
  already present. Default caps are 16 TNT, 32 hostile, 64 passive, 48 water,
  and 16 ambient entities per chunk.
- Optional persistent hostile-mob cleanup, disabled by default. When enabled,
  eligible entities are permanently removed; configure protections carefully
  and back up worlds.
- Alternate Current redstone optimization by default, with `eigencraft`,
  `vanilla`, and `paper` alternatives. Alternate Current changes dust update
  order and may affect timing- or location-sensitive contraptions.
- MOTD Builder configured in `salmon.yml`; templates override
  `server.properties` and support `&` color codes and `{online}` / `{max}`.
- `/smp` maintenance mode, MOTD reload, and a dedicated UUID/name allowlist.
  This allowlist is separate from Minecraft's regular whitelist; add staff
  before enabling maintenance.
- `/salmon` Modrinth search and installation, with dependency handling and
  downloaded-JAR checksum validation. Installed plugins require restart and
  are third-party code; review them before installing.
- Optional Chunky plugin preparation before plugin discovery. Paper loads
  Chunky normally; Salmon does not itself pregenerate chunks.
- SalmonAPI for plugin background work, server-thread scheduling, and plugin
  lookups; optional `salmon-plugin.yml` support alongside Paper/Bukkit
  descriptors.

Salmon is based on Paper and does not guarantee every third-party plugin or
redstone contraption behaves identically. Review configuration comments,
back up worlds, and test changes on a copy before using them on a production
server.

## Install

Use the runnable Paperclip bundle named
`salmon-1.21.11-2r-008.jar`. Install Java 21 or later, place the JAR in an
empty server directory, start once to generate configuration, accept the
Minecraft EULA, review Salmon settings, and restart:

```sh
java -Xms1G -Xmx4G -jar salmon-1.21.11-2r-008.jar --nogui
```

## Development notice

Feature and optimization work is paused until **November 1, 2026**, when Salmon
development is scheduled to resume. Necessary bug fixes and patches may still
be made during the pause.

## Project

- [Source repository](https://github.com/firesmasher-c6/Salmon)
- [Releases](https://github.com/firesmasher-c6/Salmon/releases)
- [GPLv3 license](LICENSE)
- [Paper upstream](https://github.com/PaperMC/Paper)

Salmon is an independent Paper-based project and is not an official PaperMC
product.
