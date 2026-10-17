package dev.firesmasher.salmon.modifiers;

import dev.firesmasher.salmon.SalmonRuntime;
import dev.firesmasher.salmon.plugins.api.SalmonAPI;
import dev.firesmasher.salmon.plugins.api.SalmonScriptContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

    private final class ScriptExecution {
        private static final int INSTRUCTIONS_PER_TICK = 500;
        private static final int MAX_TOTAL_INSTRUCTIONS = 1_000_000;
        private static final int MAX_CALL_DEPTH = 32;

        private final List<SalmonScript> scripts;
        private final int scriptIndex;
        private final SalmonScript script;
        private final Map<String, Object> variables = new HashMap<>();
        private final Map<Integer, LoopState> loops = new HashMap<>();
        private int programCounter;
        private int totalInstructions;
        private boolean paused;
        private boolean finished;

        private ScriptExecution(final List<SalmonScript> scripts, final int scriptIndex, final SalmonScript script) {
            this.scripts = scripts;
            this.scriptIndex = scriptIndex;
            this.script = script;
        }

        private void runNext() {
            this.paused = false;
            int instructionsThisTick = 0;
            while (!this.finished && !this.paused && this.programCounter < this.script.instructions().size()
                && instructionsThisTick < INSTRUCTIONS_PER_TICK) {
                final SalmonScript.Instruction instruction = this.script.instructions().get(this.programCounter++);
                instructionsThisTick++;
                if (++this.totalInstructions > MAX_TOTAL_INSTRUCTIONS) {
                    this.finished = true;
                    LOGGER.severe("Salmon script '" + this.script.metadata().name()
                        + "' stopped after exceeding the execution limit of " + MAX_TOTAL_INSTRUCTIONS
                        + " instructions");
                    break;
                }
                try {
                    this.execute(instruction);
                } catch (RuntimeException ex) {
                    this.logFailure(instruction, ex);
                    if (instruction instanceof SalmonScript.BranchFalse branch) {
                        this.programCounter = branch.target();
                    } else if (instruction instanceof SalmonScript.ForStart loop) {
                        this.loops.remove(loop.id());
                        this.programCounter = loop.exitTarget();
                    } else if (instruction instanceof SalmonScript.ForCheck loop) {
                        this.loops.remove(loop.id());
                        this.programCounter = loop.exitTarget();
                    } else if (instruction instanceof SalmonScript.ForNext) {
                        this.finished = true;
                    }
                }
            }

            if (this.finished || this.paused) {
                return;
            }
            if (this.programCounter < this.script.instructions().size()) {
                final SalmonRuntime runtime = SalmonRuntime.getInstance();
                if (runtime == null || !runtime.isEnabled()) {
                    this.finished = true;
                    LOGGER.warning("Salmon stopped before script '" + this.script.metadata().name()
                        + "' could resume");
                    return;
                }
                runtime.executeBackgroundAfter(
                    () -> SalmonScriptManager.this.server.getServer().execute(this::runNext),
                    50L,
                    TimeUnit.MILLISECONDS
                );
                return;
            }
            LOGGER.info("Finished Salmon script '" + this.script.metadata().name() + "' ("
                + this.totalInstructions + " instruction(s) executed)");
            SalmonScriptManager.this.server.getServer().execute(
                () -> SalmonScriptManager.this.executeScript(this.scripts, this.scriptIndex + 1)
            );
        }

        private void execute(final SalmonScript.Instruction instruction) {
            if (instruction instanceof SalmonScript.SetVariable set) {
                final Object value = this.evaluate(set.value(), 0);
                this.variables.put(set.name(), value);
                return;
            }
            if (instruction instanceof SalmonScript.Evaluate evaluate) {
                this.evaluate(evaluate.expression(), 0);
                return;
            }
            if (instruction instanceof SalmonScript.BranchFalse branch) {
                if (!truthy(this.evaluate(branch.condition(), 0))) {
                    this.programCounter = branch.target();
                }
                return;
            }
            if (instruction instanceof SalmonScript.Jump jump) {
                this.programCounter = jump.target();
                return;
            }
            if (instruction instanceof SalmonScript.ForStart loop) {
                final double start = number(this.evaluate(loop.start(), 0), "numeric for start");
                final double end = number(this.evaluate(loop.end(), 0), "numeric for end");
                final double step = number(this.evaluate(loop.step(), 0), "numeric for step");
                if (step == 0.0d || !Double.isFinite(start) || !Double.isFinite(end) || !Double.isFinite(step)) {
                    throw new IllegalArgumentException("numeric for loop requires finite values and a non-zero step");
                }
                this.variables.put(loop.name(), start);
                this.loops.put(loop.id(), new LoopState(end, step));
                if ((step > 0.0d && start > end) || (step < 0.0d && start < end)) {
                    this.loops.remove(loop.id());
                    this.programCounter = loop.exitTarget();
                }
                return;
            }
            if (instruction instanceof SalmonScript.ForCheck loop) {
                final LoopState state = this.loops.get(loop.id());
                if (state == null) {
                    throw new IllegalStateException("numeric for loop state is missing");
                }
                final double current = number(this.variables.get(loop.name()), "numeric for variable");
                if ((state.step() > 0.0d && current > state.end()) || (state.step() < 0.0d && current < state.end())) {
                    this.loops.remove(loop.id());
                    this.programCounter = loop.exitTarget();
                }
                return;
            }
            if (instruction instanceof SalmonScript.ForNext loop) {
                final LoopState state = this.loops.get(loop.id());
                if (state == null) {
                    throw new IllegalStateException("numeric for loop state is missing");
                }
                final double next = number(this.variables.get(loop.name()), "numeric for variable") + state.step();
                if (!Double.isFinite(next)) {
                    throw new IllegalArgumentException("numeric for loop exceeded finite numeric range");
                }
                this.variables.put(loop.name(), next);
                this.programCounter = loop.target();
                return;
            }
            if (instruction instanceof SalmonScript.RunCommand command) {
                final String text = string(this.evaluate(command.command(), 0)).replaceFirst(";$", "").trim();
                if (!SalmonScriptManager.this.server.dispatchCommand(
                    SalmonScriptManager.this.server.getConsoleSender(), text
                )) {
                    LOGGER.warning("Salmon script command was not recognized at line " + command.line() + ": " + text);
                } else {
                    LOGGER.info("Salmon script ran command at line " + command.line() + ": " + text);
                }
                return;
            }
            if (instruction instanceof SalmonScript.SetBlock block) {
                this.setBlock(block);
                return;
            }
            if (instruction instanceof SalmonScript.Wait wait) {
                final double ticksValue = number(this.evaluate(wait.ticks(), 0), "wait duration");
                if (ticksValue < 0.0d || ticksValue > 72000.0d || ticksValue != Math.floor(ticksValue)) {
                    throw new IllegalArgumentException("wait expects a whole number of ticks from 0 to 72000");
                }
                if (ticksValue == 0.0d) {
                    return;
                }
                this.paused = true;
                final SalmonRuntime runtime = SalmonRuntime.getInstance();
                if (runtime == null || !runtime.isEnabled()) {
                    this.paused = false;
                    throw new IllegalStateException("Salmon runtime stopped while script was waiting");
                }
                try {
                    runtime.executeBackgroundAfter(
                        () -> SalmonScriptManager.this.server.getServer().execute(this::runNext),
                        (long) ticksValue * 50L,
                        TimeUnit.MILLISECONDS
                    );
                } catch (RuntimeException ex) {
                    this.paused = false;
                    throw ex;
                }
                LOGGER.info("Salmon script '" + this.script.metadata().name() + "' waiting for "
                    + (long) ticksValue + " tick(s)");
                return;
            }
            throw new IllegalStateException("Unsupported Salmon instruction: " + instruction.getClass().getName());
        }

        private void setBlock(final SalmonScript.SetBlock instruction) {
            final Material material = Material.matchMaterial(string(this.evaluate(instruction.material(), 0)));
            final int x = blockCoordinate(this.evaluate(instruction.x(), 0), "x");
            final int y = blockCoordinate(this.evaluate(instruction.y(), 0), "y");
            final int z = blockCoordinate(this.evaluate(instruction.z(), 0), "z");
            final String worldName = string(this.evaluate(instruction.world(), 0));
            if (material == null || !material.isBlock()) {
                throw new IllegalArgumentException("setBlock material must be a known block material");
            }
            this.setBlockInWorld(material, x, y, z, worldName, instruction.line());
        }

        private void setBlockInWorld(final Material material, final int x, final int y, final int z,
            final String worldName, final int line) {
            final World world = SalmonScriptManager.this.server.getWorld(worldName);
            if (world == null) {
                throw new IllegalArgumentException("world '" + worldName + "' is not loaded");
            }
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                throw new IllegalArgumentException("y coordinate " + y + " is outside world height");
            }

            final int chunkX = x >> 4;
            final int chunkZ = z >> 4;
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
                this.applyBlock(world.getChunkAt(chunkX, chunkZ), x, y, z, material, worldName, line);
                return;
            }

            this.paused = true;
            LOGGER.info("Loading target and neighboring chunks asynchronously for Salmon script '"
                + this.script.metadata().name()
                + "' at " + x + " " + y + " " + z + " in world '" + world.getName() + "'");
            CompletableFuture.allOf(chunksToLoad.toArray(CompletableFuture<?>[]::new))
                .whenComplete((ignored, failure) -> SalmonScriptManager.this.server.getServer().execute(() -> {
                    if (failure != null) {
                        this.logFailure(line, new IllegalStateException(
                            "could not asynchronously load the target and neighboring chunks", failure));
                    } else {
                        try {
                            this.applyBlock(world.getChunkAt(chunkX, chunkZ), x, y, z, material, worldName, line);
                        } catch (RuntimeException ex) {
                            this.logFailure(line, ex);
                        }
                    }
                    this.runNext();
                }));
        }

        private void applyBlock(
            final Chunk chunk,
            final int x, final int y, final int z,
            final Material material, final String worldName, final int line
        ) {
            chunk.getBlock(x & 15, y, z & 15).setType(material);
            LOGGER.info("Salmon script set " + material.getKey() + " at " + x + " "
                + y + " " + z + " in world '" + worldName + "' (line " + line + ")");
        }

        private void logFailure(final SalmonScript.Instruction instruction, final RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                "Salmon script '" + this.script.metadata().name() + "' failed at line " + instruction.line()
                    + "; execution will continue where safe (a failing loop operation may skip or stop the loop)",
                ex);
        }

        private void logFailure(final int line, final RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                "Salmon script '" + this.script.metadata().name() + "' failed at line " + line
                    + "; execution will continue where safe",
                ex);
        }

        private Object evaluate(final SalmonScript.Expression expression, final int depth) {
            if (expression instanceof SalmonScript.Literal literal) {
                return literal.value();
            }
            if (expression instanceof SalmonScript.Variable variable) {
                if (!this.variables.containsKey(variable.name())) {
                    throw new IllegalArgumentException("unknown variable '" + variable.name() + "' at line " + variable.line());
                }
                return this.variables.get(variable.name());
            }
            if (expression instanceof SalmonScript.Unary unary) {
                final Object value = this.evaluate(unary.value(), depth);
                return switch (unary.operator()) {
                    case "not" -> !truthy(value);
                    case "-" -> -number(value, "unary minus");
                    case "#" -> (double) string(value).length();
                    default -> throw new IllegalArgumentException("unsupported unary operator " + unary.operator());
                };
            }
            if (expression instanceof SalmonScript.Binary binary) {
                final Object left = this.evaluate(binary.left(), depth);
                if (binary.operator().equals("and")) {
                    return truthy(left) ? this.evaluate(binary.right(), depth) : left;
                }
                if (binary.operator().equals("or")) {
                    return truthy(left) ? left : this.evaluate(binary.right(), depth);
                }
                final Object right = this.evaluate(binary.right(), depth);
                return binary(binary.operator(), left, right);
            }
            if (expression instanceof SalmonScript.Call call) {
                final List<Object> arguments = call.arguments().stream().map(argument -> this.evaluate(argument, depth)).toList();
                return this.call(call, arguments, depth);
            }
            throw new IllegalArgumentException("unsupported expression " + expression.getClass().getSimpleName());
        }

        private Object call(final SalmonScript.Call call, final List<Object> arguments, final int depth) {
            final String name = call.name().toLowerCase(Locale.ROOT);
            if (name.equals("print")) {
                LOGGER.info("[Script " + this.script.metadata().name() + "] "
                    + String.join("\t", arguments.stream().map(SalmonScriptManager::string).toList()));
                return null;
            }
            if (this.script.functions().containsKey(call.name())) {
                if (depth >= MAX_CALL_DEPTH) {
                    throw new IllegalArgumentException("function recursion exceeded " + MAX_CALL_DEPTH + " calls");
                }
                final SalmonScript.Function function = this.script.functions().get(call.name());
                if (arguments.size() != function.parameters().size()) {
                    throw new IllegalArgumentException("function '" + call.name() + "' expects "
                        + function.parameters().size() + " argument(s)");
                }
                final Map<String, Object> savedVariables = new HashMap<>(this.variables);
                for (int index = 0; index < arguments.size(); index++) {
                    this.variables.put(function.parameters().get(index), arguments.get(index));
                }
                try {
                    return this.evaluate(function.body(), depth + 1);
                } finally {
                    this.variables.clear();
                    this.variables.putAll(savedVariables);
                }
            }
            final SalmonAPI salmonAPI = SalmonAPI.get();
            if (salmonAPI.scriptFunctionNames().contains(call.name())) {
                return salmonAPI.invokeScriptFunction(call.name(),
                    new SalmonScriptContext(this.script.metadata().name(), call.line()), arguments);
            }
            return builtIn(name, arguments);
        }

        private record LoopState(double end, double step) {
        }
    }

    private static boolean truthy(final Object value) {
        return value != null && !Boolean.FALSE.equals(value);
    }

    private static double number(final Object value, final String context) {
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException(context + " requires a finite number");
    }

    private static int blockCoordinate(final Object value, final String axis) {
        final double coordinate = number(value, axis + " block coordinate");
        if (coordinate != Math.rint(coordinate) || coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(axis + " block coordinate must be a whole 32-bit integer");
        }
        return (int) coordinate;
    }

    private static String string(final Object value) {
        if (value == null) {
            return "nil";
        }
        if (value instanceof Double number) {
            if (number == Math.rint(number)) {
                return Long.toString(number.longValue());
            }
            return Double.toString(number);
        }
        return String.valueOf(value);
    }

    private static Object binary(final String operator, final Object left, final Object right) {
        return switch (operator) {
            case "+" -> number(left, "addition") + number(right, "addition");
            case "-" -> number(left, "subtraction") - number(right, "subtraction");
            case "*" -> number(left, "multiplication") * number(right, "multiplication");
            case "/" -> {
                final double divisor = number(right, "division");
                if (divisor == 0.0d) {
                    throw new IllegalArgumentException("division by zero");
                }
                yield number(left, "division") / divisor;
            }
            case "%" -> {
                final double divisor = number(right, "modulo");
                if (divisor == 0.0d) {
                    throw new IllegalArgumentException("modulo by zero");
                }
                yield number(left, "modulo") % divisor;
            }
            case ".." -> string(left) + string(right);
            case "==" -> valuesEqual(left, right);
            case "~=" -> !valuesEqual(left, right);
            case "<", "<=", ">", ">=" -> compare(operator, left, right);
            default -> throw new IllegalArgumentException("unsupported binary operator " + operator);
        };
    }

    private static boolean valuesEqual(final Object left, final Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
        }
        return java.util.Objects.equals(left, right);
    }

    private static boolean compare(final String operator, final Object left, final Object right) {
        final int order;
        if (left instanceof Number && right instanceof Number) {
            order = Double.compare(number(left, "comparison"), number(right, "comparison"));
        } else if (left instanceof String leftString && right instanceof String rightString) {
            order = leftString.compareTo(rightString);
        } else {
            throw new IllegalArgumentException("ordered comparisons require two numbers or two strings");
        }
        return switch (operator) {
            case "<" -> order < 0;
            case "<=" -> order <= 0;
            case ">" -> order > 0;
            case ">=" -> order >= 0;
            default -> throw new IllegalArgumentException("unsupported comparison " + operator);
        };
    }

    private static Object builtIn(final String name, final List<Object> arguments) {
        return switch (name) {
            case "tostring" -> {
                requireArguments(name, arguments, 1);
                yield string(arguments.get(0));
            }
            case "tonumber" -> {
                requireArguments(name, arguments, 1);
                try {
                    yield Double.parseDouble(string(arguments.get(0)));
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
            case "type" -> {
                requireArguments(name, arguments, 1);
                final Object value = arguments.get(0);
                yield value == null ? "nil" : value instanceof Boolean ? "boolean"
                    : value instanceof Number ? "number" : "string";
            }
            case "math.abs" -> {
                requireArguments(name, arguments, 1);
                yield Math.abs(number(arguments.get(0), name));
            }
            case "math.floor" -> {
                requireArguments(name, arguments, 1);
                yield Math.floor(number(arguments.get(0), name));
            }
            case "math.ceil" -> {
                requireArguments(name, arguments, 1);
                yield Math.ceil(number(arguments.get(0), name));
            }
            case "math.sqrt" -> {
                requireArguments(name, arguments, 1);
                yield Math.sqrt(number(arguments.get(0), name));
            }
            case "math.min" -> {
                requireAtLeast(name, arguments, 1);
                yield arguments.stream().mapToDouble(value -> number(value, name)).min().orElseThrow();
            }
            case "math.max" -> {
                requireAtLeast(name, arguments, 1);
                yield arguments.stream().mapToDouble(value -> number(value, name)).max().orElseThrow();
            }
            case "string.lower" -> {
                requireArguments(name, arguments, 1);
                yield string(arguments.get(0)).toLowerCase(Locale.ROOT);
            }
            case "string.upper" -> {
                requireArguments(name, arguments, 1);
                yield string(arguments.get(0)).toUpperCase(Locale.ROOT);
            }
            case "string.len" -> {
                requireArguments(name, arguments, 1);
                yield (double) string(arguments.get(0)).length();
            }
            default -> throw new IllegalArgumentException("unknown function '" + name + "'");
        };
    }

    private static void requireArguments(final String name, final List<Object> arguments, final int expected) {
        if (arguments.size() != expected) {
            throw new IllegalArgumentException(name + " expects " + expected + " argument(s)");
        }
    }

    private static void requireAtLeast(final String name, final List<Object> arguments, final int expected) {
        if (arguments.size() < expected) {
            throw new IllegalArgumentException(name + " expects at least " + expected + " argument(s)");
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
