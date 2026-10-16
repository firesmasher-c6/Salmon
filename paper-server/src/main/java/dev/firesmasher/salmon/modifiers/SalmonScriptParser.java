package dev.firesmasher.salmon.modifiers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SalmonScriptParser {
    private static final Pattern METADATA_FIELD = Pattern.compile("^(name|version|author):\\s*(.+?)\\s*,?$");
    private static final Pattern BLOCK_INSTRUCTION = Pattern.compile(
        "^set block ([a-z0-9_.-]+:[a-z0-9_./-]+) at (-?\\d+) (-?\\d+) (-?\\d+) in world (.+)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?");
    private static final int MAX_INSTRUCTIONS = 4096;

    private SalmonScriptParser() {
    }

    public static SalmonScript parse(final Path source, final String contents) throws SalmonScriptException {
        final String[] lines = contents.split("\\R", -1);
        int lineIndex = 0;
        while (lineIndex < lines.length && ignored(lines[lineIndex])) {
            lineIndex++;
        }
        if (lineIndex >= lines.length || !lines[lineIndex].trim().equals("meta(")) {
            throw new SalmonScriptException("required meta(...) block must start the script");
        }

        final Map<String, String> metadata = new HashMap<>();
        lineIndex++;
        boolean closedMetadata = false;
        for (; lineIndex < lines.length; lineIndex++) {
            final String line = lines[lineIndex].trim();
            if (ignored(line)) {
                continue;
            }
            if (line.equals(")")) {
                closedMetadata = true;
                lineIndex++;
                break;
            }
            final Matcher matcher = METADATA_FIELD.matcher(line);
            if (!matcher.matches()) {
                throw new SalmonScriptException("invalid metadata field at line " + (lineIndex + 1));
            }
            final String key = matcher.group(1);
            final String rawValue = matcher.group(2).trim();
            final String value;
            if (key.equals("version")) {
                value = rawValue;
                if (!VERSION.matcher(value).matches()) {
                    throw new SalmonScriptException("invalid version at line " + (lineIndex + 1) + "; expected a version such as 1.0.0");
                }
            } else {
                if (rawValue.length() < 2 || rawValue.charAt(0) != '"' || rawValue.charAt(rawValue.length() - 1) != '"') {
                    throw new SalmonScriptException(key + " must be enclosed in double quotes at line " + (lineIndex + 1));
                }
                value = rawValue.substring(1, rawValue.length() - 1).trim();
                if (value.isEmpty() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                    throw new SalmonScriptException(key + " must not be empty at line " + (lineIndex + 1));
                }
            }
            if (metadata.putIfAbsent(key, value) != null) {
                throw new SalmonScriptException("duplicate metadata field '" + key + "' at line " + (lineIndex + 1));
            }
        }
        if (!closedMetadata) {
            throw new SalmonScriptException("meta(...) block is not closed");
        }
        for (String key : List.of("name", "version", "author")) {
            if (!metadata.containsKey(key)) {
                throw new SalmonScriptException("meta(...) is missing required field '" + key + "'");
            }
        }

        final List<SalmonScript.Instruction> instructions = new ArrayList<>();
        for (; lineIndex < lines.length; lineIndex++) {
            final String line = lines[lineIndex].trim();
            if (ignored(line)) {
                continue;
            }
            if (instructions.size() >= MAX_INSTRUCTIONS) {
                throw new SalmonScriptException("script exceeds the maximum of " + MAX_INSTRUCTIONS + " instructions");
            }
            final int sourceLine = lineIndex + 1;
            if (line.regionMatches(true, 0, "run command ", 0, "run command ".length())) {
                final String command = line.substring("run command ".length()).trim();
                if (command.isEmpty() || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
                    throw new SalmonScriptException("command is empty at line " + sourceLine);
                }
                instructions.add(new SalmonScript.RunCommand(sourceLine, command));
                continue;
            }

            final Matcher blockMatcher = BLOCK_INSTRUCTION.matcher(line);
            if (blockMatcher.matches()) {
                final String world = blockMatcher.group(5).trim();
                if (world.isEmpty()) {
                    throw new SalmonScriptException("world name is empty at line " + sourceLine);
                }
                try {
                    instructions.add(new SalmonScript.SetBlock(
                        sourceLine,
                        blockMatcher.group(1).toLowerCase(java.util.Locale.ROOT),
                        Integer.parseInt(blockMatcher.group(2)),
                        Integer.parseInt(blockMatcher.group(3)),
                        Integer.parseInt(blockMatcher.group(4)),
                        world
                    ));
                } catch (NumberFormatException ex) {
                    throw new SalmonScriptException("block coordinates are outside the integer range at line " + sourceLine);
                }
                continue;
            }
            throw new SalmonScriptException("unrecognized instruction at line " + sourceLine);
        }

        return new SalmonScript(
            source,
            new SalmonScriptMetadata(metadata.get("name"), metadata.get("version"), metadata.get("author")),
            instructions
        );
    }

    private static boolean ignored(final String line) {
        final String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }
}
