package test;

import compiler.backend.ir.IRInstruction;
import compiler.backend.ir.IROpCode;
import compiler.backend.ir.IRProgram;
import compiler.backend.ir.Operand;
import compiler.backend.optimization.IROptimizer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IROptimizerCopyPropagationTests {
    private IROptimizerCopyPropagationTests() {}

    public static void main(String[] args) throws Exception {
        simpleAndRedundantChains();
        longChain();
        reassignmentAndCycles();
        branchesLoopsAndPredecessors();
        callBarrier();
        semanticEquivalence();
        System.out.println("IROptimizer copy-propagation checks passed.");
    }

    private static void simpleAndRedundantChains() throws Exception {
        Operand source = variable("source");
        Operand a = variable("a");
        Operand b = variable("b");
        IRProgram result = run(program(
                assign(a, source), assign(b, a), print(b), ret()));
        assertPrintOperand(result, source);
    }

    private static void longChain() throws Exception {
        int length = 10_000;
        IRProgram input = new IRProgram();
        input.add(label("CopyTest.main__void__ret_void"));
        input.add(assign(variable("v0"), variable("seed")));
        for (int i = 1; i < length; i++) {
            input.add(assign(variable("v" + i), variable("v" + (i - 1))));
        }
        input.add(print(variable("v" + (length - 1))));
        input.add(ret());
        assertPrintOperand(run(input), variable("seed"));
    }

    private static void reassignmentAndCycles() throws Exception {
        Operand x = variable("x");
        Operand a = variable("a");
        Operand b = variable("b");
        Operand constant = Operand.constant(7, Operand.Type.I32);
        IRProgram reassigned = run(program(
                assign(a, x), assign(b, a), assign(x, constant), print(b), ret()));
        assertPrintOperand(reassigned, b);

        IRProgram cycle = run(program(
                assign(a, b), assign(b, a), print(b), ret()));
        if (cycle.instructions().stream().anyMatch(i -> i.opCode() == IROpCode.ASSIGN
                && i.destination().equals(a) && i.left().equals(a))) {
            throw new AssertionError("Copy cycle created a self-referential alias.");
        }
    }

    private static void branchesLoopsAndPredecessors() throws Exception {
        Operand source = variable("source");
        Operand local = variable("local");
        Operand condition = Operand.variable("condition", Operand.Type.BOOL);
        IRProgram input = program(
                assign(local, source),
                new IRInstruction(IROpCode.JUMP_IF_FALSE, Operand.label("other"), condition, null),
                print(local),
                label("other"),
                print(local),
                label("loop"),
                assign(local, source),
                new IRInstruction(IROpCode.JUMP_IF_TRUE, Operand.label("loop"), condition, null),
                label("join"), print(local), ret());
        IRProgram result = run(input);
        long printsUsingLocal = result.instructions().stream()
                .filter(i -> i.opCode() == IROpCode.PRINT && i.arguments().contains(local)).count();
        if (printsUsingLocal < 2) {
            throw new AssertionError("Copy information crossed a basic-block boundary.");
        }

        IRProgram multiPred = program(
                assign(variable("left"), source),
                new IRInstruction(IROpCode.JUMP, Operand.label("join"), null, null),
                label("right"), assign(variable("rightValue"), source),
                new IRInstruction(IROpCode.JUMP, Operand.label("join"), null, null),
                label("join"), print(variable("left")), ret());
        IRProgram merged = run(multiPred);
        assertPrintOperand(merged, variable("left"));
    }

    private static void callBarrier() throws Exception {
        Operand source = variable("source");
        Operand alias = variable("alias");
        IRProgram result = run(program(
                assign(alias, source),
                new IRInstruction(IROpCode.CALL, null, Operand.label("mutate"), null, List.of()),
                print(alias), ret()));
        assertPrintOperand(result, alias);
    }

    private static void semanticEquivalence() throws Exception {
        Operand x = variable("x");
        Operand a = variable("a");
        Operand b = variable("b");
        IRProgram input = program(
                assign(a, x), assign(b, a),
                new IRInstruction(IROpCode.ADD, variable("sum"), b,
                        Operand.constant(3, Operand.Type.I32)),
                print(variable("sum")), ret());
        for (int value : new int[]{-10, 0, 1, 42, Integer.MAX_VALUE}) {
            Map<String, Object> initial = Map.of("x", value);
            List<Object> before = interpret(input, initial);
            List<Object> after = interpret(run(input), initial);
            if (!before.equals(after)) throw new AssertionError("IR output changed: " + before + " vs " + after);
        }
    }

    private static List<Object> interpret(IRProgram program, Map<String, Object> initial) {
        Map<Operand, Object> values = new HashMap<>();
        initial.forEach((name, value) -> values.put(variable(name), value));
        List<Object> printed = new ArrayList<>();
        for (IRInstruction instruction : program.instructions()) {
            if (instruction.opCode() == IROpCode.ASSIGN) {
                values.put(instruction.destination(), value(instruction.left(), values));
            } else if (instruction.opCode() == IROpCode.ADD) {
                values.put(instruction.destination(), (Integer) value(instruction.left(), values)
                        + (Integer) value(instruction.right(), values));
            } else if (instruction.opCode() == IROpCode.PRINT) {
                printed.add(value(instruction.arguments().get(0), values));
            }
        }
        return printed;
    }

    private static Object value(Operand operand, Map<Operand, Object> values) {
        if (operand.kind() == Operand.Kind.CONSTANT) return operand.value();
        return values.get(operand);
    }

    private static IRProgram run(IRProgram input) throws Exception {
        Method method = IROptimizer.class.getDeclaredMethod("propagateCopies", IRProgram.class);
        method.setAccessible(true);
        return (IRProgram) method.invoke(new IROptimizer(), input);
    }

    private static void assertPrintOperand(IRProgram program, Operand expected) {
        for (IRInstruction instruction : program.instructions()) {
            if (instruction.opCode() == IROpCode.PRINT && instruction.arguments().contains(expected)) return;
        }
        throw new AssertionError("Expected print to use " + expected + "; got " + program.instructions());
    }

    private static IRProgram program(IRInstruction... instructions) {
        IRProgram program = new IRProgram();
        program.add(label("CopyTest.main__void__ret_void"));
        for (IRInstruction instruction : instructions) program.add(instruction);
        return program;
    }

    private static Operand variable(String name) { return Operand.variable(name, Operand.Type.I32); }
    private static IRInstruction label(String name) { return new IRInstruction(IROpCode.LABEL, Operand.label(name), null, null); }
    private static IRInstruction assign(Operand dest, Operand src) { return new IRInstruction(IROpCode.ASSIGN, dest, src, null); }
    private static IRInstruction print(Operand value) { return new IRInstruction(IROpCode.PRINT, null, null, null, List.of(value)); }
    private static IRInstruction ret() { return new IRInstruction(IROpCode.RETURN, null, null, null); }
}
