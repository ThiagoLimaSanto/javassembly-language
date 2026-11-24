package test;

import compiler.backend.ir.IRInstruction;
import compiler.backend.ir.IROpCode;
import compiler.backend.ir.IRProgram;
import compiler.backend.ir.Operand;
import compiler.backend.optimization.IROptimizer;

import java.lang.reflect.Field;
import java.util.List;

/** Regression checks for generation-based CFG topology reuse. */
public final class IROptimizerTopologyGenerationTests {
    private IROptimizerTopologyGenerationTests() {}

    public static void main(String[] args) throws Exception {
        unreachableBlockRemovalInvalidates();
        constantBranchToJumpInvalidates();
        constantBranchToFallthroughInvalidates();
        redundantJumpRemovalInvalidates();
        nonStructuralInstructionRemovalReusesTopology();
        exceptionHandlerEdgesSurviveRefresh();
        optimizerInstanceCanProcessDifferentAndMutatedInputs();
        System.out.println("IROptimizer topology-generation checks passed.");
    }

    private static void unreachableBlockRemovalInvalidates() throws Exception {
        IRProgram input = program(
                jump("live"), label("dead"), print(0),
                label("live"), print(1), ret());
        IROptimizer optimizer = new IROptimizer();
        IRProgram result = optimizer.optimize(input);
        assertGenerationAdvanced(optimizer, "unreachable block removal");
        assertNoPrint(result, 0);
        assertPrint(result, 1);
    }

    private static void constantBranchToJumpInvalidates() throws Exception {
        IRProgram input = program(
                branch(IROpCode.JUMP_IF_TRUE, "taken", true),
                label("fallthrough"), print(0),
                label("taken"), print(1), ret());
        IROptimizer optimizer = new IROptimizer();
        IRProgram result = optimizer.optimize(input);
        assertGenerationAdvanced(optimizer, "constant conditional converted to jump");
        assertNoOpcode(result, IROpCode.JUMP_IF_TRUE);
        assertNoPrint(result, 0);
        assertPrint(result, 1);
    }

    private static void constantBranchToFallthroughInvalidates() throws Exception {
        IRProgram input = program(
                branch(IROpCode.JUMP_IF_FALSE, "target", true),
                label("fallthrough"), print(0),
                label("target"), print(1), ret());
        IROptimizer optimizer = new IROptimizer();
        IRProgram result = optimizer.optimize(input);
        assertGenerationAdvanced(optimizer, "constant conditional removed for fallthrough");
        assertNoOpcode(result, IROpCode.JUMP_IF_FALSE);
        assertPrint(result, 0);
        assertPrint(result, 1);
    }

    private static void redundantJumpRemovalInvalidates() throws Exception {
        IRProgram input = program(jump("next"), label("next"), print(4), ret());
        IROptimizer optimizer = new IROptimizer();
        IRProgram result = optimizer.optimize(input);
        assertGenerationAdvanced(optimizer, "redundant jump removal");
        assertNoOpcode(result, IROpCode.JUMP);
        assertPrint(result, 4);
    }

    private static void nonStructuralInstructionRemovalReusesTopology() throws Exception {
        Operand temp = Operand.temporary("unused", Operand.Type.I32);
        Operand condition = Operand.variable("condition", Operand.Type.BOOL);
        IRProgram input = program(
                label("loop"),
                new IRInstruction(IROpCode.ADD, temp,
                        Operand.variable("value", Operand.Type.I32), Operand.constant(1, Operand.Type.I32)),
                branch(IROpCode.JUMP_IF_FALSE, "loop", condition), ret());
        IROptimizer optimizer = new IROptimizer();
        IRProgram result = optimizer.optimize(input);
        if (generation(optimizer) != 1) {
            throw new AssertionError("Removing a non-terminator instruction must not invalidate CFG topology.");
        }
        assertNoDestination(result, temp);
    }

    private static void exceptionHandlerEdgesSurviveRefresh() throws Exception {
        IRProgram input = program(
                new IRInstruction(IROpCode.PUSH_EXCEPTION_HANDLER, Operand.label("catch"), null, null),
                ret(), label("catch"),
                new IRInstruction(IROpCode.POP_EXCEPTION_HANDLER_IF, Operand.label("catch"), null, null),
                print(9), ret());
        IROptimizer optimizer = new IROptimizer();
        IRProgram result = optimizer.optimize(input);
        if (generation(optimizer) != 1) {
            throw new AssertionError("Unchanged exception-handler edges should keep the topology generation.");
        }
        assertHasLabel(result, "catch");
        assertPrint(result, 9);
    }

    private static void optimizerInstanceCanProcessDifferentAndMutatedInputs() throws Exception {
        IROptimizer reused = new IROptimizer();
        reused.optimize(program(
                branch(IROpCode.JUMP_IF_TRUE, "first_target", true),
                label("first_fallthrough"), print(11),
                label("first_target"), print(12), ret()));

        IRProgram secondInput = program(
                jump("second_live"), label("second_dead"), print(21),
                label("second_live"), print(22), ret());
        assertSameIR(reused.optimize(secondInput), new IROptimizer().optimize(secondInput),
                "different input after a cached optimization");

        secondInput.add(label("OtherClass.method__void__ret_void"));
        secondInput.add(print(23));
        secondInput.add(ret());
        assertSameIR(reused.optimize(secondInput), new IROptimizer().optimize(secondInput),
                "mutated input reused with the same optimizer");
    }

    private static void assertGenerationAdvanced(IROptimizer optimizer, String operation) throws Exception {
        if (generation(optimizer) < 2) {
            throw new AssertionError("Expected topology invalidation after " + operation
                    + "; generation was " + generation(optimizer));
        }
    }

    private static long generation(IROptimizer optimizer) throws Exception {
        Field field = IROptimizer.class.getDeclaredField("controlFlowTopologyGeneration");
        field.setAccessible(true);
        return field.getLong(optimizer);
    }

    private static void assertNoPrint(IRProgram program, int value) {
        if (program.instructions().stream().filter(i -> i.opCode() == IROpCode.PRINT)
                .flatMap(i -> i.arguments().stream())
                .anyMatch(o -> o.kind() == Operand.Kind.CONSTANT && o.value().equals(value))) {
            throw new AssertionError("Unexpected print of " + value + ": " + program.instructions());
        }
    }

    private static void assertPrint(IRProgram program, int value) {
        if (program.instructions().stream().filter(i -> i.opCode() == IROpCode.PRINT)
                .flatMap(i -> i.arguments().stream())
                .noneMatch(o -> o.kind() == Operand.Kind.CONSTANT && o.value().equals(value))) {
            throw new AssertionError("Expected print of " + value + ": " + program.instructions());
        }
    }

    private static void assertNoOpcode(IRProgram program, IROpCode opcode) {
        if (program.instructions().stream().anyMatch(i -> i.opCode() == opcode)) {
            throw new AssertionError("Unexpected " + opcode + ": " + program.instructions());
        }
    }

    private static void assertNoDestination(IRProgram program, Operand destination) {
        if (program.instructions().stream().anyMatch(i -> destination.equals(i.destination()))) {
            throw new AssertionError("Expected unused temporary to be removed: " + program.instructions());
        }
    }

    private static void assertHasLabel(IRProgram program, String name) {
        if (program.instructions().stream().noneMatch(i -> i.opCode() == IROpCode.LABEL
                && i.destination() != null && name.equals(i.destination().value()))) {
            throw new AssertionError("Expected handler label " + name + " to remain reachable.");
        }
    }

    private static void assertSameIR(IRProgram actual, IRProgram expected, String context) {
        if (!actual.instructions().equals(expected.instructions())) {
            throw new AssertionError("IR changed when reusing optimizer for " + context
                    + ": actual=" + actual.instructions() + ", expected=" + expected.instructions());
        }
    }

    private static IRProgram program(IRInstruction... body) {
        IRProgram result = new IRProgram();
        result.add(label("Topology.main__void__ret_void"));
        for (IRInstruction instruction : body) result.add(instruction);
        return result;
    }

    private static IRInstruction label(String name) {
        return new IRInstruction(IROpCode.LABEL, Operand.label(name), null, null);
    }
    private static IRInstruction print(int value) {
        return new IRInstruction(IROpCode.PRINT, null, null, null,
                List.of(Operand.constant(value, Operand.Type.I32)));
    }
    private static IRInstruction jump(String target) {
        return new IRInstruction(IROpCode.JUMP, Operand.label(target), null, null);
    }
    private static IRInstruction branch(IROpCode opcode, String target, boolean condition) {
        return branch(opcode, target, Operand.constant(condition, Operand.Type.BOOL));
    }
    private static IRInstruction branch(IROpCode opcode, String target, Operand condition) {
        return new IRInstruction(opcode, Operand.label(target), condition, null);
    }
    private static IRInstruction ret() { return new IRInstruction(IROpCode.RETURN, null, null, null); }
}
