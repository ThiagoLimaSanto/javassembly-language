package test;

import compiler.backend.ir.IRInstruction;
import compiler.backend.ir.IROpCode;
import compiler.backend.ir.IRProgram;
import compiler.backend.ir.Operand;
import compiler.backend.optimization.IROptimizer;

import java.lang.reflect.Method;
import java.util.List;

public final class IROptimizerDeadStoreTests {
    private IROptimizerDeadStoreTests() {}

    public static void main(String[] args) throws Exception {
        removesOnlyDeadLocalAssignments();
        preservesBranchAndLoopValues();
        callsKeepPotentiallyObservedVariables();
        heapStoresRemainObservable();
        System.out.println("IROptimizer dead-store checks passed.");
    }

    private static void removesOnlyDeadLocalAssignments() throws Exception {
        Operand dead = variable("dead");
        Operand live = variable("live");
        IRProgram result = run(program(
                assign(dead, constant(1)), assign(live, constant(2)), print(live), ret()));
        if (result.instructions().stream().anyMatch(i -> dead.equals(i.destination()))) {
            throw new AssertionError("Unused local assignment was not removed.");
        }
        if (result.instructions().stream().noneMatch(i -> live.equals(i.destination()))) {
            throw new AssertionError("Assignment feeding a print was removed.");
        }

        IRProgram before = program(assign(live, constant(9)), print(live), ret());
        IRProgram after = run(before);
        if (!interpret(before).equals(interpret(after))) {
            throw new AssertionError("Dead-store elimination changed observable output.");
        }
    }

    private static void preservesBranchAndLoopValues() throws Exception {
        Operand value = variable("value");
        Operand condition = Operand.variable("condition", Operand.Type.BOOL);
        IRProgram branches = program(
                assign(value, constant(1)),
                new IRInstruction(IROpCode.JUMP_IF_FALSE, Operand.label("alternate"), condition, null),
                print(value), new IRInstruction(IROpCode.JUMP, Operand.label("join"), null, null),
                label("alternate"), assign(value, constant(2)),
                label("join"), print(value), ret());
        IRProgram branchResult = run(branches);
        if (branchResult.instructions().stream().filter(i -> i.opCode() == IROpCode.PRINT).count() != 2) {
            throw new AssertionError("Branch uses were not retained.");
        }

        IRProgram loop = program(
                assign(value, constant(0)), label("loop"), print(value),
                assign(value, constant(1)),
                new IRInstruction(IROpCode.JUMP_IF_TRUE, Operand.label("loop"), condition, null), ret());
        IRProgram loopResult = run(loop);
        if (loopResult.instructions().stream().noneMatch(i -> i.opCode() == IROpCode.ASSIGN
                && value.equals(i.destination()))) {
            throw new AssertionError("Loop-carried assignment was removed.");
        }
    }

    private static void callsKeepPotentiallyObservedVariables() throws Exception {
        Operand value = variable("value");
        IRProgram input = program(assign(value, constant(3)),
                new IRInstruction(IROpCode.CALL, null, Operand.label("unknown"), null, List.of()), ret());
        IRProgram result = run(input);
        if (result.instructions().stream().noneMatch(i -> value.equals(i.destination()))) {
            throw new AssertionError("Assignment visible to an unknown call was removed.");
        }
    }

    private static void heapStoresRemainObservable() throws Exception {
        Operand receiver = Operand.variable("receiver", Operand.Type.REF);
        Operand array = Operand.variable("array", Operand.Type.REF);
        IRProgram input = program(
                new IRInstruction(IROpCode.STORE_FIELD, null, receiver, null, List.of(constant(7))),
                new IRInstruction(IROpCode.STORE_ARRAY, null, array, constant(0), List.of(constant(8))),
                ret());
        IRProgram result = run(input);
        long stores = result.instructions().stream().filter(i -> i.opCode() == IROpCode.STORE_FIELD
                || i.opCode() == IROpCode.STORE_ARRAY).count();
        if (stores != 2) throw new AssertionError("Field/array stores were incorrectly eliminated.");
    }

    private static IRProgram run(IRProgram input) throws Exception {
        Method method = IROptimizer.class.getDeclaredMethod("eliminateDeadStores", IRProgram.class);
        method.setAccessible(true);
        return (IRProgram) method.invoke(new IROptimizer(), input);
    }

    private static List<Object> interpret(IRProgram program) {
        java.util.Map<Operand, Object> values = new java.util.HashMap<>();
        java.util.ArrayList<Object> output = new java.util.ArrayList<>();
        for (IRInstruction instruction : program.instructions()) {
            if (instruction.opCode() == IROpCode.ASSIGN) {
                values.put(instruction.destination(), instruction.left().value());
            } else if (instruction.opCode() == IROpCode.PRINT) {
                Operand operand = instruction.arguments().get(0);
                output.add(operand.kind() == Operand.Kind.CONSTANT ? operand.value() : values.get(operand));
            }
        }
        return output;
    }

    private static IRProgram program(IRInstruction... instructions) {
        IRProgram result = new IRProgram();
        result.add(label("DeadStoreTest.main__void__ret_void"));
        for (IRInstruction instruction : instructions) result.add(instruction);
        return result;
    }

    private static Operand variable(String name) { return Operand.variable(name, Operand.Type.I32); }
    private static Operand constant(int value) { return Operand.constant(value, Operand.Type.I32); }
    private static IRInstruction assign(Operand destination, Operand source) { return new IRInstruction(IROpCode.ASSIGN, destination, source, null); }
    private static IRInstruction print(Operand value) { return new IRInstruction(IROpCode.PRINT, null, null, null, List.of(value)); }
    private static IRInstruction label(String name) { return new IRInstruction(IROpCode.LABEL, Operand.label(name), null, null); }
    private static IRInstruction ret() { return new IRInstruction(IROpCode.RETURN, null, null, null); }
}
