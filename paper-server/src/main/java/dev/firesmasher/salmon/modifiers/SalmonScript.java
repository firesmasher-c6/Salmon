package dev.firesmasher.salmon.modifiers;

import java.nio.file.Path;
import java.util.List;

public record SalmonScript(Path source, SalmonScriptMetadata metadata, List<Instruction> instructions) {
    public SalmonScript {
        instructions = List.copyOf(instructions);
    }

    public sealed interface Instruction permits RunCommand, SetBlock {
        int line();
    }

    public record RunCommand(int line, String command) implements Instruction {
    }

    public record SetBlock(int line, String material, int x, int y, int z, String world) implements Instruction {
    }
}
