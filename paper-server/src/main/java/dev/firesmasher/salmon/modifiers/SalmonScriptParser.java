package dev.firesmasher.salmon.modifiers;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SalmonScriptParser {
    private static final Pattern METADATA_FIELD = Pattern.compile("^(name|version|author):\\s*(.+?)\\s*,?$");
    private static final Pattern LEGACY_BLOCK_INSTRUCTION = Pattern.compile(
        "^set\\s+block\\s+([a-z0-9_.:/-]+)\\s+at\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+in\\s+world\\s+(.+)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?");
    private static final int MAX_SOURCE_STATEMENTS = 4096;
    private static final int MAX_COMPILED_INSTRUCTIONS = 16384;

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
                    throw new SalmonScriptException("invalid version at line " + (lineIndex + 1)
                        + "; expected a version such as 1.0.0");
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

        final Parser parser = new Parser(contents, lineIndex);
        final List<Statement> statements = parser.parseBlock(Set.of());
        parser.expect(TokenType.EOF, "unexpected end of source");
        if (parser.statementCount > MAX_SOURCE_STATEMENTS) {
            throw new SalmonScriptException("script exceeds the maximum of " + MAX_SOURCE_STATEMENTS + " source statements");
        }
        final Compiler compiler = new Compiler();
        compiler.compile(statements);
        if (compiler.instructions.size() > MAX_COMPILED_INSTRUCTIONS) {
            throw new SalmonScriptException("script compiles to more than " + MAX_COMPILED_INSTRUCTIONS + " instructions");
        }
        return new SalmonScript(
            source,
            new SalmonScriptMetadata(metadata.get("name"), metadata.get("version"), metadata.get("author")),
            compiler.instructions,
            parser.functions
        );
    }

    private static boolean ignored(final String line) {
        final String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private sealed interface Statement permits Local, Assign, ExpressionStatement, IfStatement, WhileStatement,
        ForStatement, BreakStatement, ContinueStatement, CommandStatement, BlockStatement, FunctionDeclaration,
        WaitStatement, PrintStatement {
        int line();
    }

    private record Local(String name, SalmonScript.Expression value, int line) implements Statement {
    }

    private record Assign(String name, SalmonScript.Expression value, int line) implements Statement {
    }

    private record ExpressionStatement(SalmonScript.Expression expression, int line) implements Statement {
    }

    private record IfBranch(SalmonScript.Expression condition, List<Statement> body) {
    }

    private record IfStatement(List<IfBranch> branches, List<Statement> otherwise, int line) implements Statement {
    }

    private record WhileStatement(SalmonScript.Expression condition, List<Statement> body, int line) implements Statement {
    }

    private record ForStatement(
        String name, SalmonScript.Expression start, SalmonScript.Expression end, SalmonScript.Expression step,
        List<Statement> body, int line
    ) implements Statement {
    }

    private record BreakStatement(int line) implements Statement {
    }

    private record ContinueStatement(int line) implements Statement {
    }

    private record CommandStatement(SalmonScript.Expression command, int line) implements Statement {
    }

    private record BlockStatement(
        SalmonScript.Expression material, SalmonScript.Expression x, SalmonScript.Expression y, SalmonScript.Expression z,
        SalmonScript.Expression world, int line
    ) implements Statement {
    }

    private record FunctionDeclaration(String name, SalmonScript.Function function, int line) implements Statement {
    }

    private record WaitStatement(SalmonScript.Expression ticks, int line) implements Statement {
    }

    private record PrintStatement(SalmonScript.Expression expression, int line) implements Statement {
    }

    private enum TokenType {
        IDENTIFIER, NUMBER, STRING, SYMBOL, NEWLINE, EOF
    }

    private record Token(TokenType type, String value, int line, int offset, int endOffset) {
        boolean is(final String expected) {
            return this.value.equalsIgnoreCase(expected);
        }
    }

    private static final class Parser {
        private final String source;
        private final List<Token> tokens;
        private final Map<String, SalmonScript.Function> functions = new HashMap<>();
        private int cursor;
        private int statementCount;

        private Parser(final String source, final int firstBodyLine) throws SalmonScriptException {
            this.source = source;
            this.tokens = Lexer.lex(source, firstBodyLine);
        }

        private List<Statement> parseBlock(final Set<String> stops) throws SalmonScriptException {
            final List<Statement> statements = new ArrayList<>();
            this.skipSeparators();
            while (!this.peek(TokenType.EOF) && !stops.contains(this.current().value().toLowerCase(Locale.ROOT))) {
                final int before = this.cursor;
                statements.add(this.parseStatement());
                if (++this.statementCount > MAX_SOURCE_STATEMENTS) {
                    throw this.error(this.current(), "script exceeds the maximum of " + MAX_SOURCE_STATEMENTS + " source statements");
                }
                if (this.cursor == before) {
                    throw this.error(this.current(), "parser made no progress");
                }
                if (!this.peek(TokenType.NEWLINE) && !this.peekValue(";") && !this.peekValue("end")
                    && !this.peekValue("else") && !this.peekValue("elseif")) {
                    throw this.error(this.current(), "expected a new line or ';' after statement");
                }
                this.skipSeparators();
            }
            return statements;
        }

        private Statement parseStatement() throws SalmonScriptException {
            final Token first = this.current();
            if (first.is("local") || first.is("let")) {
                this.advance();
                final Token name = this.expect(TokenType.IDENTIFIER, "expected variable name");
                this.expectValue("=");
                return new Local(name.value(), this.expression(), first.line());
            }
            if (first.is("if")) {
                return this.parseIf();
            }
            if (first.is("while")) {
                return this.parseWhile();
            }
            if (first.is("for")) {
                return this.parseFor();
            }
            if (first.is("function")) {
                return this.parseFunction();
            }
            if (first.is("break")) {
                this.advance();
                return new BreakStatement(first.line());
            }
            if (first.is("continue")) {
                this.advance();
                return new ContinueStatement(first.line());
            }
            if (first.is("run") && this.peekNextValue("command")) {
                this.advance();
                this.advance();
                final Token commandStart = this.current();
                final int end = this.lineEndOffset(commandStart);
                final String command = this.source.substring(commandStart.offset(), end).trim();
                while (!this.peek(TokenType.NEWLINE) && !this.peek(TokenType.EOF) && !this.peekValue(";")) {
                    this.advance();
                }
                if (command.isEmpty()) {
                    throw this.error(first, "command is empty");
                }
                return new CommandStatement(new SalmonScript.Literal(command, first.line()), first.line());
            }
            if (first.is("set") && this.peekNextValue("block")) {
                return this.parseLegacySetBlock();
            }
            if (first.type() == TokenType.IDENTIFIER && this.isAssignmentAhead()) {
                this.advance();
                final Token operator = this.advance();
                if (operator.is("=")) {
                    return new Assign(first.value(), this.expression(), first.line());
                }
                this.expectValue("=");
                final SalmonScript.Expression oldValue = new SalmonScript.Variable(first.value(), first.line());
                final SalmonScript.Expression newValue = this.expression();
                return new Assign(first.value(),
                    new SalmonScript.Binary(operator.value(), oldValue, newValue, operator.line()), first.line());
            }
            final SalmonScript.Expression expression = this.expression();
            if (expression instanceof SalmonScript.Call call
                && call.name().equalsIgnoreCase("command") && call.arguments().size() == 1) {
                return new CommandStatement(call.arguments().get(0), first.line());
            }
            if (expression instanceof SalmonScript.Call call
                && call.name().equalsIgnoreCase("setBlock") && call.arguments().size() == 5) {
                return new BlockStatement(call.arguments().get(0), call.arguments().get(1), call.arguments().get(2),
                    call.arguments().get(3), call.arguments().get(4), first.line());
            }
            if (expression instanceof SalmonScript.Call call
                && call.name().equalsIgnoreCase("wait") && call.arguments().size() == 1) {
                return new WaitStatement(call.arguments().get(0), first.line());
            }
            if (expression instanceof SalmonScript.Call call && call.name().equalsIgnoreCase("print")) {
                return new PrintStatement(expression, first.line());
            }
            return new ExpressionStatement(expression, first.line());
        }

        private Statement parseIf() throws SalmonScriptException {
            final Token start = this.expectValue("if");
            final List<IfBranch> branches = new ArrayList<>();
            SalmonScript.Expression condition = this.expression();
            this.expectValue("then");
            branches.add(new IfBranch(condition, this.parseBlock(Set.of("elseif", "else", "end"))));
            while (this.matchValue("elseif")) {
                condition = this.expression();
                this.expectValue("then");
                branches.add(new IfBranch(condition, this.parseBlock(Set.of("elseif", "else", "end"))));
            }
            List<Statement> otherwise = List.of();
            if (this.matchValue("else")) {
                otherwise = this.parseBlock(Set.of("end"));
            }
            this.expectValue("end");
            return new IfStatement(List.copyOf(branches), otherwise, start.line());
        }

        private Statement parseWhile() throws SalmonScriptException {
            final Token start = this.expectValue("while");
            final SalmonScript.Expression condition = this.expression();
            this.expectValue("do");
            final List<Statement> body = this.parseBlock(Set.of("end"));
            this.expectValue("end");
            return new WhileStatement(condition, body, start.line());
        }

        private Statement parseFor() throws SalmonScriptException {
            final Token start = this.expectValue("for");
            final Token name = this.expect(TokenType.IDENTIFIER, "expected numeric loop variable");
            this.expectValue("=");
            final SalmonScript.Expression from = this.expression();
            this.expectValue(",");
            final SalmonScript.Expression to = this.expression();
            final SalmonScript.Expression step = this.matchValue(",")
                ? this.expression()
                : new SalmonScript.Literal(1.0d, start.line());
            this.expectValue("do");
            final List<Statement> body = this.parseBlock(Set.of("end"));
            this.expectValue("end");
            return new ForStatement(name.value(), from, to, step, body, start.line());
        }

        private Statement parseFunction() throws SalmonScriptException {
            final Token start = this.expectValue("function");
            final Token name = this.expect(TokenType.IDENTIFIER, "expected function name");
            this.expectValue("(");
            final List<String> parameters = new ArrayList<>();
            if (!this.peekValue(")")) {
                do {
                    final Token parameter = this.expect(TokenType.IDENTIFIER, "expected parameter name");
                    if (parameters.contains(parameter.value())) {
                        throw this.error(parameter, "duplicate function parameter '" + parameter.value() + "'");
                    }
                    parameters.add(parameter.value());
                } while (this.matchValue(","));
            }
            this.expectValue(")");
            this.skipSeparators();
            this.expectValue("return");
            final SalmonScript.Expression body = this.expression();
            this.skipSeparators();
            this.expectValue("end");
            final SalmonScript.Function function = new SalmonScript.Function(parameters, body, start.line());
            if (this.functions.putIfAbsent(name.value(), function) != null) {
                throw this.error(name, "duplicate function '" + name.value() + "'");
            }
            return new FunctionDeclaration(name.value(), function, start.line());
        }

        private Statement parseLegacySetBlock() throws SalmonScriptException {
            final Token start = this.advance();
            this.expectValue("block");
            final String lineText = this.source.substring(start.offset(), this.lineEndOffset(start)).trim();
            final Matcher matcher = LEGACY_BLOCK_INSTRUCTION.matcher(lineText);
            if (!matcher.matches()) {
                throw this.error(start, "invalid set block syntax; expected material, integer coordinates, and world name");
            }
            while (!this.peek(TokenType.NEWLINE) && !this.peek(TokenType.EOF) && !this.peekValue(";")) {
                this.advance();
            }
            final String worldName = matcher.group(5).trim();
            if (worldName.isEmpty()) {
                throw this.error(start, "world name is empty");
            }
            return new BlockStatement(
                new SalmonScript.Literal(matcher.group(1).toLowerCase(Locale.ROOT), start.line()),
                new SalmonScript.Literal(Double.parseDouble(matcher.group(2)), start.line()),
                new SalmonScript.Literal(Double.parseDouble(matcher.group(3)), start.line()),
                new SalmonScript.Literal(Double.parseDouble(matcher.group(4)), start.line()),
                new SalmonScript.Literal(worldName, start.line()),
                start.line()
            );
        }

        private SalmonScript.Expression expression() throws SalmonScriptException {
            return this.binary(1);
        }

        private SalmonScript.Expression binary(final int minimumPrecedence) throws SalmonScriptException {
            SalmonScript.Expression left = this.unary();
            while (true) {
                final Token operator = this.current();
                final int precedence = precedence(operator);
                if (precedence < minimumPrecedence) {
                    return left;
                }
                this.advance();
                final SalmonScript.Expression right = this.binary(precedence + 1);
                left = new SalmonScript.Binary(operator.value().toLowerCase(Locale.ROOT), left, right, operator.line());
            }
        }

        private SalmonScript.Expression unary() throws SalmonScriptException {
            final Token token = this.current();
            if (token.is("not")) {
                this.advance();
                return new SalmonScript.Unary("not", this.binary(3), token.line());
            }
            if (token.is("-") || token.is("#")) {
                this.advance();
                return new SalmonScript.Unary(token.value().toLowerCase(Locale.ROOT), this.unary(), token.line());
            }
            return this.primary();
        }

        private SalmonScript.Expression primary() throws SalmonScriptException {
            final Token token = this.current();
            if (this.matchValue("(")) {
                final SalmonScript.Expression nested = this.expression();
                this.expectValue(")");
                return nested;
            }
            if (token.type() == TokenType.NUMBER) {
                this.advance();
                try {
                    return new SalmonScript.Literal(Double.parseDouble(token.value()), token.line());
                } catch (NumberFormatException ex) {
                    throw this.error(token, "invalid number");
                }
            }
            if (token.type() == TokenType.STRING) {
                this.advance();
                return new SalmonScript.Literal(token.value(), token.line());
            }
            if (token.is("true") || token.is("false") || token.is("nil")) {
                this.advance();
                final Object value = token.is("nil") ? null : Boolean.valueOf(token.value());
                return new SalmonScript.Literal(value, token.line());
            }
            if (token.type() == TokenType.IDENTIFIER) {
                this.advance();
                String name = token.value();
                while (this.matchValue(".")) {
                    name += "." + this.expect(TokenType.IDENTIFIER, "expected name after '.'").value();
                }
                if (this.matchValue("(")) {
                    final List<SalmonScript.Expression> arguments = new ArrayList<>();
                    if (!this.peekValue(")")) {
                        do {
                            arguments.add(this.expression());
                        } while (this.matchValue(","));
                    }
                    this.expectValue(")");
                    return new SalmonScript.Call(name, arguments, token.line());
                }
                if (!name.equals(token.value())) {
                    throw this.error(token, "member access is only supported for function names");
                }
                return new SalmonScript.Variable(name, token.line());
            }
            throw this.error(token, "expected an expression");
        }

        private void skipSeparators() {
            while (this.peek(TokenType.NEWLINE) || this.peekValue(";")) {
                this.advance();
            }
        }

        private int lineEndOffset(final Token token) {
            int end = token.offset();
            while (end < this.source.length() && this.source.charAt(end) != '\n' && this.source.charAt(end) != '\r') {
                end++;
            }
            return end;
        }

        private boolean peekNextValue(final String value) {
            return this.cursor + 1 < this.tokens.size() && this.tokens.get(this.cursor + 1).is(value);
        }

        private boolean isAssignmentAhead() {
            if (this.peekNextValue("=")) {
                return true;
            }
            if (this.cursor + 2 >= this.tokens.size()) {
                return false;
            }
            final Token operator = this.tokens.get(this.cursor + 1);
            return Set.of("+", "-", "*", "/", "%", "..").contains(operator.value())
                && this.tokens.get(this.cursor + 2).is("=");
        }

        private boolean peek(final TokenType type) {
            return this.current().type() == type;
        }

        private boolean peekValue(final String value) {
            return this.current().is(value);
        }

        private boolean matchValue(final String value) {
            if (!this.peekValue(value)) {
                return false;
            }
            this.advance();
            return true;
        }

        private Token expectValue(final String value) throws SalmonScriptException {
            if (!this.peekValue(value)) {
                throw this.error(this.current(), "expected '" + value + "'");
            }
            return this.advance();
        }

        private Token expect(final TokenType type, final String message) throws SalmonScriptException {
            if (!this.peek(type)) {
                throw this.error(this.current(), message);
            }
            return this.advance();
        }

        private Token current() {
            return this.tokens.get(this.cursor);
        }

        private Token advance() {
            final Token token = this.current();
            if (token.type() != TokenType.EOF) {
                this.cursor++;
            }
            return token;
        }

        private SalmonScriptException error(final Token token, final String message) {
            return new SalmonScriptException(message + " at line " + token.line());
        }
    }

    private static int precedence(final Token token) {
        if (token.type() != TokenType.SYMBOL && token.type() != TokenType.IDENTIFIER) {
            return -1;
        }
        return switch (token.value().toLowerCase(Locale.ROOT)) {
            case "or" -> 1;
            case "and" -> 2;
            case "==", "~=", "<", "<=", ">", ">=" -> 3;
            case ".." -> 4;
            case "+", "-" -> 5;
            case "*", "/", "%" -> 6;
            default -> -1;
        };
    }

    private static final class Compiler {
        private final List<SalmonScript.Instruction> instructions = new ArrayList<>();
        private final ArrayDeque<Loop> loops = new ArrayDeque<>();
        private int nextLoopId;

        private void compile(final List<Statement> statements) throws SalmonScriptException {
            this.compileStatements(statements);
        }

        private void compileStatements(final List<Statement> statements) throws SalmonScriptException {
            for (Statement statement : statements) {
                if (statement instanceof Local local) {
                    this.instructions.add(new SalmonScript.SetVariable(local.name(), local.value(), true, local.line()));
                } else if (statement instanceof Assign assign) {
                    this.instructions.add(new SalmonScript.SetVariable(assign.name(), assign.value(), false, assign.line()));
                } else if (statement instanceof ExpressionStatement expression) {
                    this.instructions.add(new SalmonScript.Evaluate(expression.expression(), expression.line()));
                } else if (statement instanceof CommandStatement command) {
                    this.instructions.add(new SalmonScript.RunCommand(command.command(), command.line()));
                } else if (statement instanceof BlockStatement block) {
                    this.instructions.add(new SalmonScript.SetBlock(
                        block.material(), block.x(), block.y(), block.z(), block.world(), block.line()
                    ));
                } else if (statement instanceof WaitStatement wait) {
                    this.instructions.add(new SalmonScript.Wait(wait.ticks(), wait.line()));
                } else if (statement instanceof PrintStatement print) {
                    this.instructions.add(new SalmonScript.Evaluate(print.expression(), print.line()));
                } else if (statement instanceof IfStatement conditional) {
                    this.compileIf(conditional);
                } else if (statement instanceof WhileStatement loop) {
                    this.compileWhile(loop);
                } else if (statement instanceof ForStatement loop) {
                    this.compileFor(loop);
                } else if (statement instanceof BreakStatement control) {
                    this.addLoopJump(true, control.line());
                } else if (statement instanceof ContinueStatement control) {
                    this.addLoopJump(false, control.line());
                }
            }
        }

        private void compileIf(final IfStatement statement) throws SalmonScriptException {
            final List<Integer> endJumps = new ArrayList<>();
            for (IfBranch branch : statement.branches()) {
                final int branchTest = this.instructions.size();
                this.instructions.add(new SalmonScript.BranchFalse(branch.condition(), -1, statement.line()));
                this.compileStatements(branch.body());
                endJumps.add(this.instructions.size());
                this.instructions.add(new SalmonScript.Jump(-1, statement.line()));
                this.replaceBranchTarget(branchTest, this.instructions.size());
            }
            this.compileStatements(statement.otherwise());
            final int end = this.instructions.size();
            for (int jump : endJumps) {
                this.instructions.set(jump, new SalmonScript.Jump(end, statement.line()));
            }
        }

        private void compileWhile(final WhileStatement statement) throws SalmonScriptException {
            final int start = this.instructions.size();
            final int condition = this.instructions.size();
            this.instructions.add(new SalmonScript.BranchFalse(statement.condition(), -1, statement.line()));
            final Loop loop = new Loop(start);
            this.loops.push(loop);
            this.compileStatements(statement.body());
            this.instructions.add(new SalmonScript.Jump(start, statement.line()));
            final int end = this.instructions.size();
            this.replaceBranchTarget(condition, end);
            this.finishLoop(loop, end);
        }

        private void compileFor(final ForStatement statement) throws SalmonScriptException {
            final int id = this.nextLoopId++;
            final int start = this.instructions.size();
            final int startInstruction = this.instructions.size();
            this.instructions.add(new SalmonScript.ForStart(id, statement.name(), statement.start(), statement.end(),
                statement.step(), -1, statement.line()));
            final int condition = this.instructions.size();
            this.instructions.add(new SalmonScript.ForCheck(id, statement.name(), -1, statement.line()));
            final Loop loop = new Loop(-1);
            this.loops.push(loop);
            this.compileStatements(statement.body());
            final int increment = this.instructions.size();
            loop.continueTarget = increment;
            this.instructions.add(new SalmonScript.ForNext(id, statement.name(), condition, statement.line()));
            final int end = this.instructions.size();
            this.instructions.set(startInstruction, new SalmonScript.ForStart(id, statement.name(),
                statement.start(), statement.end(), statement.step(), end, statement.line()));
            this.instructions.set(condition, new SalmonScript.ForCheck(id, statement.name(), end, statement.line()));
            this.finishLoop(loop, end);
        }

        private void addLoopJump(final boolean isBreak, final int line) throws SalmonScriptException {
            if (this.loops.isEmpty()) {
                throw new SalmonScriptException((isBreak ? "break" : "continue") + " must be inside a loop at line " + line);
            }
            final Loop loop = this.loops.peek();
            final int index = this.instructions.size();
            this.instructions.add(new SalmonScript.Jump(-1, line));
            if (isBreak) {
                loop.breaks.add(index);
            } else if (loop.continueTarget >= 0) {
                this.instructions.set(index, new SalmonScript.Jump(loop.continueTarget, line));
            } else {
                loop.continues.add(index);
            }
        }

        private void finishLoop(final Loop loop, final int end) {
            this.loops.pop();
            for (int jump : loop.breaks) {
                this.instructions.set(jump, new SalmonScript.Jump(end, this.instructions.get(jump).line()));
            }
            final int continueTarget = loop.continueTarget >= 0 ? loop.continueTarget : end - 1;
            for (int jump : loop.continues) {
                this.instructions.set(jump, new SalmonScript.Jump(continueTarget, this.instructions.get(jump).line()));
            }
        }

        private void replaceBranchTarget(final int index, final int target) {
            final SalmonScript.BranchFalse branch = (SalmonScript.BranchFalse) this.instructions.get(index);
            this.instructions.set(index, new SalmonScript.BranchFalse(branch.condition(), target, branch.line()));
        }

        private static final class Loop {
            private final int conditionTarget;
            private final List<Integer> breaks = new ArrayList<>();
            private final List<Integer> continues = new ArrayList<>();
            private int continueTarget = -1;

            private Loop(final int conditionTarget) {
                this.conditionTarget = conditionTarget;
                if (conditionTarget >= 0) {
                    this.continueTarget = conditionTarget;
                }
            }
        }
    }

    private static final class Lexer {
        private final String source;
        private final List<Token> tokens = new ArrayList<>();
        private int cursor;
        private int line;

        private Lexer(final String source, final int line) {
            this.source = source;
            this.line = line;
        }

        private static List<Token> lex(final String source, final int firstBodyLine) throws SalmonScriptException {
            final Lexer lexer = new Lexer(source, 1);
            int newlines = 0;
            while (lexer.cursor < source.length() && newlines < firstBodyLine) {
                if (source.charAt(lexer.cursor++) == '\n') {
                    newlines++;
                }
            }
            lexer.line = firstBodyLine + 1;
            lexer.scan();
            return List.copyOf(lexer.tokens);
        }

        private void scan() throws SalmonScriptException {
            while (this.cursor < this.source.length()) {
                final char current = this.source.charAt(this.cursor);
                if (current == ' ' || current == '\t' || current == '\f') {
                    this.cursor++;
                    continue;
                }
                if (current == '\r' || current == '\n') {
                    final int start = this.cursor++;
                    if (current == '\r' && this.cursor < this.source.length() && this.source.charAt(this.cursor) == '\n') {
                        this.cursor++;
                    }
                    this.tokens.add(new Token(TokenType.NEWLINE, "\n", this.line++, start, this.cursor));
                    continue;
                }
                if (current == '#') {
                    while (this.cursor < this.source.length()
                        && this.source.charAt(this.cursor) != '\n' && this.source.charAt(this.cursor) != '\r') {
                        this.cursor++;
                    }
                    continue;
                }
                if (Character.isLetter(current) || current == '_') {
                    this.identifier();
                    continue;
                }
                if (Character.isDigit(current) || (current == '.' && this.hasNextDigit())) {
                    this.number();
                    continue;
                }
                if (current == '"' || current == '\'') {
                    this.string();
                    continue;
                }
                this.symbol();
            }
            this.tokens.add(new Token(TokenType.EOF, "<eof>", this.line, this.source.length(), this.source.length()));
        }

        private void identifier() {
            final int start = this.cursor++;
            while (this.cursor < this.source.length()) {
                final char value = this.source.charAt(this.cursor);
                if (!Character.isLetterOrDigit(value) && value != '_') {
                    break;
                }
                this.cursor++;
            }
            this.tokens.add(new Token(TokenType.IDENTIFIER, this.source.substring(start, this.cursor), this.line, start, this.cursor));
        }

        private void number() {
            final int start = this.cursor++;
            while (this.cursor < this.source.length()) {
                final char value = this.source.charAt(this.cursor);
                if (!Character.isDigit(value) && value != '.') {
                    break;
                }
                if (value == '.' && this.cursor + 1 < this.source.length() && this.source.charAt(this.cursor + 1) == '.') {
                    break;
                }
                this.cursor++;
            }
            this.tokens.add(new Token(TokenType.NUMBER, this.source.substring(start, this.cursor), this.line, start, this.cursor));
        }

        private void string() throws SalmonScriptException {
            final int start = this.cursor;
            final char quote = this.source.charAt(this.cursor++);
            final StringBuilder value = new StringBuilder();
            while (this.cursor < this.source.length()) {
                final char current = this.source.charAt(this.cursor++);
                if (current == quote) {
                    this.tokens.add(new Token(TokenType.STRING, value.toString(), this.line, start, this.cursor));
                    return;
                }
                if (current == '\n' || current == '\r') {
                    throw new SalmonScriptException("string literal cannot span multiple lines at line " + this.line);
                }
                if (current == '\\') {
                    if (this.cursor >= this.source.length()) {
                        break;
                    }
                    final char escaped = this.source.charAt(this.cursor++);
                    value.append(switch (escaped) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case '\\' -> '\\';
                        case '"' -> '"';
                        case '\'' -> '\'';
                        default -> throw new SalmonScriptException("unsupported string escape at line " + this.line);
                    });
                } else {
                    value.append(current);
                }
            }
            throw new SalmonScriptException("unterminated string literal at line " + this.line);
        }

        private void symbol() throws SalmonScriptException {
            final int start = this.cursor;
            final String two = this.cursor + 1 < this.source.length()
                ? this.source.substring(this.cursor, this.cursor + 2) : "";
            if (Set.of("==", "~=", "<=", ">=", "..").contains(two)) {
                this.cursor += 2;
                this.tokens.add(new Token(TokenType.SYMBOL, two, this.line, start, this.cursor));
                return;
            }
            final char symbol = this.source.charAt(this.cursor++);
            if ("+-*/%#=<>(),;:.".indexOf(symbol) < 0) {
                throw new SalmonScriptException("unexpected character '" + symbol + "' at line " + this.line);
            }
            this.tokens.add(new Token(TokenType.SYMBOL, String.valueOf(symbol), this.line, start, this.cursor));
        }

        private boolean hasNextDigit() {
            return this.cursor + 1 < this.source.length() && Character.isDigit(this.source.charAt(this.cursor + 1));
        }
    }
}
