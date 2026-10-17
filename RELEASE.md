# Salmon 1.21.11 - 4e-003

Salmon `4e-003` is an experimental build for Minecraft `1.21.11`, based on
Paper's plugin platform with additional Salmon optimizations, APIs, and SMP
features.

## Download

Download `salmon-1.21.11-4e-003.jar` from this GitHub release.

## Highlights

- Redstone Optimization defaults to Paper's Alternate Current engine, which
  greatly speeds up redstone dust updates in large circuits. It changes dust
  update order, so location-dependent or timing-sensitive contraptions may
  behave differently. Configure `redstone.implementation` in
  `config/salmon-opts.yml` to choose `alternate_current`, `eigencraft`,
  `vanilla`, or `paper`.
- MOTD Builder in `salmon.yml`; configured MOTDs override `server.properties`,
  support `&` color codes and `{online}` / `{max}` placeholders, and can be
  reloaded without restarting.
- `/smp maintenance on|off` displays a maintenance MOTD and restricts logins
  to the dedicated maintenance allowlist.
- `/smp maintenance whitelist add|rm|list` manages allowed player names and
  UUIDs; this allowlist is separate from Minecraft's regular whitelist.
- Per-chunk caps for TNT and hostile, passive, water, and ambient entities.
- Configurable cleanup for old persistent hostile mobs and plugin execution
  timing reports.
- Startup `.slms` scripting with expressions, functions, branches, loops,
  waits, commands, and block changes.
- SalmonAPI, `salmon-plugin.yml`, optional Chunky setup, and Modrinth plugin
  search and installation.

## Install and start

1. Install Java 21 or later.
2. Put `salmon-1.21.11-4e-003.jar` in an empty server directory.
3. Start the server once to create its configuration files.
4. Review `salmon.yml` and `config/salmon-opts.yml`, accept the Minecraft EULA
   in `eula.txt`, then restart.

   ```sh
   java -Xms1G -Xmx4G -jar salmon-1.21.11-4e-003.jar --nogui
   ```

Before enabling maintenance mode, add staff using
`/smp maintenance whitelist add <uuid|playername>`.

Back up worlds before enabling entity cleanup or running scripts that change
the world. Modrinth installations add third-party plugin code to the server;
review projects and their dependencies before installing. Newly installed
plugins require a restart.

## Source

https://github.com/firesmasher-c6/Salmon/tree/ver/1.21.11
