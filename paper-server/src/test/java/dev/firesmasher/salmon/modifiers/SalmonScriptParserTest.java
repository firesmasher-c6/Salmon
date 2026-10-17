package dev.firesmasher.salmon.modifiers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class SalmonScriptParserTest {
    @Test
    void parsesFunctionsExpressionsBranchesLoopsAndWorldActions() throws Exception {
        final SalmonScript script = SalmonScriptParser.parse(Path.of("test.slms"), """
            meta(
                name: "LanguageTest",
                version: 1.0.0,
                author: "Salmon"
            )

            function double(value)
                return value * 2
            end

            local total = 0
            for i = 1, 4 do
                if i == 2 then
                    continue
                elseif i > 3 then
                    break
                else
                    total += double(i)
                end
            end

            while total < 10 do
                total = total + 1
            end
            print("total=" .. tostring(total))
            run command say Script loaded
            wait(2)
            setBlock("minecraft:stone", 0, 64, 0, "world")
            set block minecraft:dirt at 1 64 0 in world world
            """);

        assertEquals(1, script.functions().size());
        assertTrue(script.instructions().stream().anyMatch(SalmonScript.BranchFalse.class::isInstance));
        assertTrue(script.instructions().stream().anyMatch(SalmonScript.ForStart.class::isInstance));
        assertTrue(script.instructions().stream().anyMatch(SalmonScript.ForNext.class::isInstance));
        assertTrue(script.instructions().stream().anyMatch(SalmonScript.Wait.class::isInstance));
        assertEquals(2, script.instructions().stream().filter(SalmonScript.SetBlock.class::isInstance).count());
        assertEquals(1, script.instructions().stream().filter(SalmonScript.RunCommand.class::isInstance).count());
    }

    @Test
    void rejectsBreakOutsideLoopsAndUnknownSyntax() {
        assertThrows(SalmonScriptException.class, () -> SalmonScriptParser.parse(Path.of("bad.slms"), """
            meta(
                name: "Bad",
                version: 1.0.0,
                author: "Salmon"
            )
            break
            """));
        assertThrows(SalmonScriptException.class, () -> SalmonScriptParser.parse(Path.of("bad.slms"), """
            meta(
                name: "Bad",
                version: 1.0.0,
                author: "Salmon"
            )
            print(1 + )
            """));
    }

    @Test
    void recordsBranchTargetsAndLoopTransfers() throws Exception {
        final SalmonScript script = SalmonScriptParser.parse(Path.of("flow.slms"), """
            meta(
                name: "Flow",
                version: 1.0.0,
                author: "Salmon"
            )
            local value = 1
            value *= 2
            if value == 1 then
                value = 2
            else
                value = 3
            end
            while value > 0 do
                value = value - 1
                if value == 1 then
                    continue
                end
                break
            end
            """);

        for (int index = 0; index < script.instructions().size(); index++) {
            final SalmonScript.Instruction instruction = script.instructions().get(index);
            if (instruction instanceof SalmonScript.BranchFalse branch) {
                assertTrue(branch.target() >= 0 && branch.target() <= script.instructions().size());
            }
            if (instruction instanceof SalmonScript.Jump jump) {
                assertTrue(jump.target() >= 0 && jump.target() <= script.instructions().size());
            }
        }
        assertFalse(script.instructions().isEmpty());
        assertInstanceOf(SalmonScript.SetVariable.class, script.instructions().getFirst());
    }

    @Test
    void parsesRepositoryScriptExamples() throws Exception {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.isDirectory(directory.resolve("salmon-scripts-examples"))) {
            directory = directory.getParent();
        }
        assertTrue(directory != null, "salmon-scripts-examples directory was not found");

        final Path examples = directory.resolve("salmon-scripts-examples");
        final List<Path> scripts;
        try (Stream<Path> paths = Files.list(examples)) {
            scripts = paths.filter(path -> path.getFileName().toString().endsWith(".slms")).sorted().toList();
        }
        assertEquals(4, scripts.size());
        for (Path script : scripts) {
            SalmonScriptParser.parse(script, Files.readString(script));
        }
    }
}
