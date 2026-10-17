package dev.firesmasher.salmon.modifiers;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record SalmonScript(
    Path source,
    SalmonScriptMetadata metadata,
    List<Instruction> instructions,
    Map<String, Function> functions
) {
    public SalmonScript {
        instructions = List.copyOf(instructions);
        functions = Map.copyOf(functions);
    }

    public record Function(List<String> parameters, Expression body, int line) {
        public Function {
            parameters = List.copyOf(parameters);
        }
    }

    public sealed interface Expression permits Literal, Variable, Unary, Binary, Call {
        int line();
    }

    public record Literal(Object value, int line) implements Expression {
    }

    public record Variable(String name, int line) implements Expression {
    }

    public record Unary(String operator, Expression value, int line) implements Expression {
    }

    public record Binary(String operator, Expression left, Expression right, int line) implements Expression {
    }

    public record Call(String name, List<Expression> arguments, int line) implements Expression {
        public Call {
            arguments = List.copyOf(arguments);
        }
    }

    public sealed interface Instruction permits SetVariable, Evaluate, BranchFalse, Jump, ForStart, ForCheck, ForNext,
        RunCommand, SetBlock, Wait {
        int line();
    }

    public record SetVariable(String name, Expression value, boolean local, int line) implements Instruction {
    }

    public record Evaluate(Expression expression, int line) implements Instruction {
    }

    public record BranchFalse(Expression condition, int target, int line) implements Instruction {
    }

    public record Jump(int target, int line) implements Instruction {
    }

    public record ForStart(int id, String name, Expression start, Expression end, Expression step, int exitTarget, int line)
        implements Instruction {
    }

    public record ForCheck(int id, String name, int exitTarget, int line) implements Instruction {
    }

    public record ForNext(int id, String name, int target, int line) implements Instruction {
    }

    public record RunCommand(Expression command, int line) implements Instruction {
    }

    public record SetBlock(Expression material, Expression x, Expression y, Expression z, Expression world, int line)
        implements Instruction {
    }

    public record Wait(Expression ticks, int line) implements Instruction {
    }
}
