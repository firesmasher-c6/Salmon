package dev.firesmasher.salmon.modifiers;

import dev.firesmasher.salmon.SalmonRuntime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;

public final class SalmonScriptManager {
    private static final Logger LOGGER = Logger.getLogger("Salmon");
    private static final long MAX_SCRIPT_BYTES = 1_048_576L;
    private static final String README_RESOURCE = "configurations/salmon-scripts-README.txt";
    private static final String README_NAME = "README.txt";

    private final CraftServer server;
    private final Path scriptsDirectory;

    public SalmonScriptManager(final CraftServer server, final Path serverDirectory) throws IOException {
        this.server = server;
        this.scriptsDirectory = serverDirectory.resolve("salmon-scripts");
        Files.createDirectories(this.scriptsDirectory);
        this.createReadmeIfMissing();
    }

    public void loadAtStartup() {
        final SalmonRuntime runtime = SalmonRuntime.getInstance();
        if (runtime == null || !runtime.isEnabled()) {
            return;
        }
        runtime.executeBackground(this::readScripts);
    }

    private void readScripts() {
        final List<Path> files;
        try (var paths = Files.list(this.scriptsDirectory)) {
            files = paths
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> path.getFileName().toString().endsWith(".slms"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Could not list Salmon scripts in " + this.scriptsDirectory, ex);
            return;
        }

        final List<SalmonScript> scripts = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        for (Path file : files) {
            try {
                if (Files.size(file) > MAX_SCRIPT_BYTES) {
                    throw new SalmonScriptException("script exceeds the 1 MiB size limit");
                }
                final SalmonScript script = SalmonScriptParser.parse(file, Files.readString(file, StandardCharsets.UTF_8));
                if (!names.add(script.metadata().name().toLowerCase(Locale.ROOT))) {
                    throw new SalmonScriptException("duplicate script name '" + script.metadata().name() + "'");
                }
                scripts.add(script);
            } catch (SalmonScriptException | IOException ex) {
                LOGGER.log(Level.SEVERE, "Skipping Salmon script " + file + ": " + ex.getMessage(), ex);
            }
        }

        if (!scripts.isEmpty()) {
            LOGGER.info("Loaded " + scripts.size() + " valid Salmon script(s) from " + this.scriptsDirectory);
            this.server.getServer().execute(() -> this.executeScripts(scripts));
        } else {
            LOGGER.info("No valid Salmon scripts found in " + this.scriptsDirectory);
        }
    }

    private void executeScripts(final List<SalmonScript> scripts) {
        this.executeScript(scripts, 0);
    }

    private void executeScript(final List<SalmonScript> scripts, final int scriptIndex) {
        if (scriptIndex >= scripts.size()) {
            LOGGER.info("Finished executing " + scripts.size() + " Salmon script(s)");
            return;
        }
        final SalmonScript script = scripts.get(scriptIndex);
        LOGGER.info("Running Salmon script '" + script.metadata().name() + "' v" + script.metadata().version()
            + " by " + script.metadata().author() + " (" + script.source().getFileName() + ")");
        new ScriptExecution(scripts, scriptIndex, script).runNext();
    }

    private void execute(final SalmonScript.Instruction instruction) {
        if (instruction instanceof SalmonScript.RunCommand runCommand) {
            if (!this.server.dispatchCommand(this.server.getConsoleSender(), runCommand.command())) {
                LOGGER.warning("Salmon script command was not recognized at line " + runCommand.line() + ": " + runCommand.command());
            } else {
                LOGGER.info("Salmon script ran command at line " + runCommand.line() + ": " + runCommand.command());
            }
            return;
        }
        throw new IllegalStateException("Unsupported Salmon instruction: " + instruction.getClass().getName());
    }

    private final class ScriptExecution {
        private final List<SalmonScript> scripts;
        private final int scriptIndex;
        private final SalmonScript script;
        private int instructionIndex;

        private ScriptExecution(final List<SalmonScript> scripts, final int scriptIndex, final SalmonScript script) {
            this.scripts = scripts;
            this.scriptIndex = scriptIndex;
            this.script = script;
        }

        private void runNext() {
            while (this.instructionIndex < this.script.instructions().size()) {
                final SalmonScript.Instruction instruction = this.script.instructions().get(this.instructionIndex++);
                try {
                    if (instruction instanceof SalmonScript.SetBlock setBlock) {
                        if (this.setBlock(setBlock)) {
                            return;
                        }
                        continue;
                    }
                    SalmonScriptManager.this.execute(instruction);
                } catch (RuntimeException ex) {
                    this.logFailure(instruction, ex);
                }
            }

            LOGGER.info("Finished Salmon script '" + this.script.metadata().name() + "' ("
                + this.script.instructions().size() + " instruction(s))");
            SalmonScriptManager.this.executeScript(this.scripts, this.scriptIndex + 1);
        }

        private boolean setBlock(final SalmonScript.SetBlock instruction) {
            final World world = SalmonScriptManager.this.server.getWorld(instruction.world());
            if (world == null) {
                throw new IllegalArgumentException("world '" + instruction.world() + "' is not loaded");
            }
            if (instruction.y() < world.getMinHeight() || instruction.y() >= world.getMaxHeight()) {
                throw new IllegalArgumentException("y coordinate " + instruction.y() + " is outside world height");
            }
            final Material material = Material.matchMaterial(instruction.material());
            if (material == null || !material.isBlock()) {
                throw new IllegalArgumentException("'" + instruction.material() + "' is not a known block material");
            }

            final int chunkX = instruction.x() >> 4;
            final int chunkZ = instruction.z() >> 4;
            final List<CompletableFuture<Chunk>> chunksToLoad = new ArrayList<>(9);
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    final int nearbyChunkX = chunkX + offsetX;
                    final int nearbyChunkZ = chunkZ + offsetZ;
                    if (!world.isChunkLoaded(nearbyChunkX, nearbyChunkZ)) {
                        chunksToLoad.add(world.getChunkAtAsync(nearbyChunkX, nearbyChunkZ, true));
                    }
                }
            }
            if (chunksToLoad.isEmpty()) {
                this.applyBlock(world.getChunkAt(chunkX, chunkZ), instruction, material);
                return false;
            }

            LOGGER.info("Loading target and neighboring chunks asynchronously for Salmon script '"
                + this.script.metadata().name()
                + "' at " + instruction.x() + " " + instruction.y() + " " + instruction.z()
                + " in world '" + world.getName() + "'");
            CompletableFuture.allOf(chunksToLoad.toArray(CompletableFuture<?>[]::new))
                .whenComplete((ignored, failure) -> SalmonScriptManager.this.server.getServer().execute(() -> {
                    if (failure != null) {
                        this.logFailure(instruction,
                            new IllegalStateException("could not asynchronously load the target and neighboring chunks", failure));
                    } else {
                        try {
                            this.applyBlock(world.getChunkAt(chunkX, chunkZ), instruction, material);
                        } catch (RuntimeException ex) {
                            this.logFailure(instruction, ex);
                        }
                    }
                    this.runNext();
                }));
            return true;
        }

        private void applyBlock(
            final Chunk chunk,
            final SalmonScript.SetBlock instruction,
            final Material material
        ) {
            chunk.getBlock(instruction.x() & 15, instruction.y(), instruction.z() & 15).setType(material);
            LOGGER.info("Salmon script set " + material.getKey() + " at " + instruction.x() + " "
                + instruction.y() + " " + instruction.z() + " in world '" + instruction.world()
                + "' (line " + instruction.line() + ")");
        }

        private void logFailure(final SalmonScript.Instruction instruction, final RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                "Salmon script '" + this.script.metadata().name() + "' failed at line " + instruction.line()
                    + "; later instructions in this script will still be attempted",
                ex);
        }
    }

    private void createReadmeIfMissing() throws IOException {
        final Path readme = this.scriptsDirectory.resolve(README_NAME);
        if (Files.exists(readme)) {
            return;
        }
        try (var input = SalmonScriptManager.class.getClassLoader().getResourceAsStream(README_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing Salmon scripts documentation resource: " + README_RESOURCE);
            }
            Files.copy(input, readme);
        }
    }
}
