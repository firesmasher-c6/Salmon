# Salmon 1.21.11 - 2e-001

Salmon `2e-001` is an experimental build for Minecraft `1.21.11`, based on
Paper's plugin platform with additional Salmon optimizations and APIs.

## Download

Download `salmon-1.21.11-2e-001.jar` from the release assets.

## Highlights

- Per-chunk caps for TNT and hostile, passive, water, and ambient entities.
- Configurable cleanup for old persistent hostile mobs.
- Plugin execution timing reports.
- Startup `.slms` scripting with expressions, functions, branches, loops,
  waits, commands, and block changes.
- SalmonAPI for Paper/Bukkit plugin integration, async work, server-thread
  scheduling, console commands, and script extensions.
- `salmon-plugin.yml`, a Paper-compatible plugin descriptor that adds a Salmon
  category to `/plugins`.
- Optional Chunky setup via Modrinth and operator/console Modrinth plugin
  search and installation, including required dependencies.

## Install and start

1. Install Java 21 or later.
2. Put `salmon-1.21.11-2e-001.jar` in an empty server directory.
3. Start the server once to create its configuration files.
4. Review the generated configurations and accept the Minecraft EULA in
   `eula.txt`.
5. Start the server again:

   ```sh
   java -Xms1G -Xmx4G -jar salmon-1.21.11-2e-001.jar --nogui
   ```

Back up worlds before enabling entity cleanup or running scripts that change
the world. Modrinth installations add third-party plugin code to the server;
review projects and their dependencies before installing. Newly installed
plugins require a restart.

## Compatibility and status

This is an experimental build. Salmon retains Paper/Bukkit plugin support;
Salmon-specific APIs and `salmon-plugin.yml` require Salmon. Test plugins and
world behavior on a backup before deploying to a production server.

## Source

https://github.com/firesmasher-c6/Salmon/tree/ver/1.21.11
