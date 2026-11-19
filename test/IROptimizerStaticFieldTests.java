package test;

import java.util.List;

import compiler.backend.ir.IRInstruction;
import compiler.backend.ir.IROpCode;
import compiler.backend.ir.IRProgram;
import compiler.backend.ir.Operand;
import compiler.backend.optimization.IROptimizer;

public final class IROptimizerStaticFieldTests {
    private IROptimizerStaticFieldTests() {
    }

    public static void main(String[] args) {
        IRProgram input = new IRProgram();
        Operand field = Operand.label("__static_Counter_value");
        Operand firstLoad = Operand.temporary("t1", Operand.Type.I32);
        Operand repeatedLoad = Operand.temporary("t2", Operand.Type.I32);
        Operand afterStore = Operand.temporary("t3", Operand.Type.I32);
        Operand afterVirtualCall = Operand.temporary("t4", Operand.Type.I32);

        input.add(new IRInstruction(IROpCode.LABEL,
                Operand.label("OptimizerStaticTest.main__void__ret_void"), null, null));
        input.add(new IRInstruction(IROpCode.LOAD_STATIC, firstLoad, field, null));
        input.add(new IRInstruction(IROpCode.PRINT, null, null, null, List.of(firstLoad)));
        input.add(new IRInstruction(IROpCode.LOAD_STATIC, repeatedLoad, field, null));
        input.add(new IRInstruction(IROpCode.PRINT, null, null, null, List.of(repeatedLoad)));
        input.add(new IRInstruction(IROpCode.STORE_STATIC, null, field, null,
                List.of(Operand.constant(11, Operand.Type.I32))));
        input.add(new IRInstruction(IROpCode.LOAD_STATIC, afterStore, field, null));
        input.add(new IRInstruction(IROpCode.PRINT, null, null, null, List.of(afterStore)));
        input.add(new IRInstruction(IROpCode.CALL_VIRTUAL, null,
                Operand.label("__vtable_slot_0"), null, List.of()));
        input.add(new IRInstruction(IROpCode.LOAD_STATIC, afterVirtualCall, field, null));
        input.add(new IRInstruction(IROpCode.PRINT, null, null, null, List.of(afterVirtualCall)));
        input.add(new IRInstruction(IROpCode.RETURN, null, null, null));

        IRProgram optimized = new IROptimizer().optimize(input);
        long staticLoads = optimized.instructions().stream()
                .filter(instruction -> instruction.opCode() == IROpCode.LOAD_STATIC)
                .count();
        if (staticLoads != 2) {
            throw new AssertionError("Expected 2 remaining static loads, found " + staticLoads);
        }
        boolean foldedLoad = optimized.instructions().stream()
                .noneMatch(instruction -> instruction.opCode() == IROpCode.LOAD_STATIC
                        && afterStore.equals(instruction.destination()))
                && optimized.instructions().stream().anyMatch(instruction ->
                        instruction.opCode() == IROpCode.PRINT
                                && instruction.arguments().stream().anyMatch(argument ->
                                        argument.kind() == Operand.Kind.CONSTANT
                                                && argument.value().equals(11)));
        if (!foldedLoad) {
            throw new AssertionError("A static load after a constant store was not folded.");
        }
        System.out.println("Static field optimizer checks passed.");
    }
}
