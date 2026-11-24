package test;

import compiler.backend.ir.IRInstruction;
import compiler.backend.ir.IROpCode;
import compiler.backend.ir.IRProgram;
import compiler.backend.ir.Operand;
import compiler.backend.optimization.IROptimizer;

import java.lang.reflect.Method;
import java.util.List;

public final class IROptimizerConstantPropagationTests {
    private IROptimizerConstantPropagationTests() {}

    public static void main(String[] args) throws Exception {
        localAndCrossBlockConstants();
        predecessorIntersection();
        loopsAndRedefinitions();
        callBarrierAndDivisionByZero();
        overflowAndReferenceFacts();
        deterministicAgainstFullOptimizer();
        System.out.println("IROptimizer constant-propagation checks passed.");
    }

    private static void localAndCrossBlockConstants() throws Exception {
        Operand value = variable("value");
        Operand result = temporary("result");
        IRProgram local = program(
                assign(value, constant(4)),
                new IRInstruction(IROpCode.ADD, result, value, constant(3)), print(result), ret());
        assertPrintedConstant(run(local), 7);

        IRProgram acrossBlocks = program(
                assign(value, constant(9)), jump("next"),
                label("next"), print(value), ret());
        assertPrintedConstant(run(acrossBlocks), 9);
    }

    private static void predecessorIntersection() throws Exception {
        Operand condition = Operand.variable("condition", Operand.Type.BOOL);
        Operand value = variable("value");
        IRProgram same = program(
                branch("right", condition), assign(value, constant(6)), jump("join"),
                label("right"), assign(value, constant(6)),
                label("join"), print(value), ret());
        assertPrintedConstant(run(same), 6);

        IRProgram conflict = program(
                branch("right", condition), assign(value, constant(6)), jump("join"),
                label("right"), assign(value, constant(7)),
                label("join"), print(value), ret());
        assertPrintedOperand(run(conflict), value);
    }

    private static void loopsAndRedefinitions() throws Exception {
        Operand value = variable("value");
        Operand condition = Operand.variable("condition", Operand.Type.BOOL);
        IRProgram loop = program(
                assign(value, constant(0)), label("loop"), print(value),
                new IRInstruction(IROpCode.ADD, value, value, constant(1)),
                branch("loop", condition), ret());
        assertPrintedOperand(run(loop), value);

        IRProgram redefined = program(
                assign(value, constant(3)), assign(value, variable("input")), print(value), ret());
        assertPrintedOperand(run(redefined), value);
    }

    private static void callBarrierAndDivisionByZero() throws Exception {
        Operand value = variable("value");
        Operand result = temporary("result");
        IRProgram call = program(
                assign(value, constant(10)),
                new IRInstruction(IROpCode.CALL, null, Operand.label("mutate"), null, List.of()),
                print(value), ret());
        assertPrintedOperand(run(call), value);

        IRProgram division = program(new IRInstruction(IROpCode.DIV, result, constant(10), constant(0)),
                print(result), ret());
        IRProgram optimizedDivision = run(division);
        if (optimizedDivision.instructions().stream().noneMatch(i -> i.opCode() == IROpCode.DIV)) {
            throw new AssertionError("Division by zero was folded away.");
        }
    }

    private static void overflowAndReferenceFacts() throws Exception {
        Operand overflow = temporary("overflow");
        IRProgram arithmetic = program(new IRInstruction(IROpCode.ADD, overflow,
                constant(Integer.MAX_VALUE), constant(1)), print(overflow), ret());
        assertPrintedConstant(run(arithmetic), Integer.MIN_VALUE);

        Operand reference = Operand.variable("reference", Operand.Type.REF);
        IRProgram freedNull = program(assign(reference, Operand.constant(null, Operand.Type.REF)),
                new IRInstruction(IROpCode.FREE_OBJECT, null, reference, null),
                print(reference), ret());
        assertPrintedNullReference(run(freedNull));
    }

    private static void deterministicAgainstFullOptimizer() throws Exception {
        IRProgram input = program(assign(variable("n"), constant(2)),
                new IRInstruction(IROpCode.MUL, temporary("product"), variable("n"), constant(8)),
                print(temporary("product")), ret());
        IRProgram first = new IROptimizer().optimize(input);
        IRProgram second = new IROptimizer().optimize(input);
        if (!first.instructions().equals(second.instructions())) {
            throw new AssertionError("Full optimize() produced different IR for identical input.");
        }
    }

    private static IRProgram run(IRProgram input) throws Exception {
        Method method = IROptimizer.class.getDeclaredMethod("propagateConstants", IRProgram.class);
        method.setAccessible(true);
        return (IRProgram) method.invoke(new IROptimizer(), input);
    }

    private static void assertPrintedConstant(IRProgram program, Object expected) {
        boolean found = program.instructions().stream().filter(i -> i.opCode() == IROpCode.PRINT)
                .flatMap(i -> i.arguments().stream())
                .anyMatch(o -> o.kind() == Operand.Kind.CONSTANT && expected.equals(o.value()));
        if (!found) throw new AssertionError("Expected printed constant " + expected + ": " + program.instructions());
    }

    private static void assertPrintedOperand(IRProgram program, Operand expected) {
        boolean found = program.instructions().stream().filter(i -> i.opCode() == IROpCode.PRINT)
                .flatMap(i -> i.arguments().stream()).anyMatch(expected::equals);
        if (!found) throw new AssertionError("Expected unknown operand " + expected + ": " + program.instructions());
    }

    private static void assertPrintedNullReference(IRProgram program) {
        boolean found = program.instructions().stream().filter(i -> i.opCode() == IROpCode.PRINT)
                .flatMap(i -> i.arguments().stream())
                .anyMatch(o -> o.kind() == Operand.Kind.CONSTANT && o.type() == Operand.Type.REF && o.value() == null);
        if (!found) throw new AssertionError("Free should make a mutable reference slot null.");
    }

    private static IRProgram program(IRInstruction... instructions) {
        IRProgram result = new IRProgram();
        result.add(label("ConstTest.main__void__ret_void"));
        for (IRInstruction instruction : instructions) result.add(instruction);
        return result;
    }

    private static Operand variable(String name) { return Operand.variable(name, Operand.Type.I32); }
    private static Operand temporary(String name) { return Operand.temporary(name, Operand.Type.I32); }
    private static Operand constant(int value) { return Operand.constant(value, Operand.Type.I32); }
    private static IRInstruction label(String name) { return new IRInstruction(IROpCode.LABEL, Operand.label(name), null, null); }
    private static IRInstruction assign(Operand dest, Operand source) { return new IRInstruction(IROpCode.ASSIGN, dest, source, null); }
    private static IRInstruction print(Operand operand) { return new IRInstruction(IROpCode.PRINT, null, null, null, List.of(operand)); }
    private static IRInstruction jump(String target) { return new IRInstruction(IROpCode.JUMP, Operand.label(target), null, null); }
    private static IRInstruction branch(String target, Operand condition) { return new IRInstruction(IROpCode.JUMP_IF_FALSE, Operand.label(target), condition, null); }
    private static IRInstruction ret() { return new IRInstruction(IROpCode.RETURN, null, null, null); }
}
