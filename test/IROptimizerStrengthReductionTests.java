package test;

import java.util.List;

import compiler.backend.ir.IRInstruction;
import compiler.backend.ir.IROpCode;
import compiler.backend.ir.IRProgram;
import compiler.backend.ir.Operand;
import compiler.backend.optimization.IROptimizer;

public final class IROptimizerStrengthReductionTests {
    private IROptimizerStrengthReductionTests() {
    }

    public static void main(String[] args) {
        Operand integer = Operand.variable("integer", Operand.Type.I32);
        Operand integerForNegation = Operand.variable("integerForNegation", Operand.Type.I32);
        Operand integerForZeroSubtraction = Operand.variable("integerForZeroSubtraction", Operand.Type.I32);
        Operand floating = Operand.variable("floating", Operand.Type.F64);
        Operand timesTwo = Operand.temporary("timesTwo", Operand.Type.I32);
        Operand timesNegativeOne = Operand.temporary("timesNegativeOne", Operand.Type.I32);
        Operand zeroMinusValue = Operand.temporary("zeroMinusValue", Operand.Type.I32);
        Operand sameMinusSame = Operand.temporary("sameMinusSame", Operand.Type.I32);
        Operand doubleTimesOne = Operand.temporary("doubleTimesOne", Operand.Type.F64);
        Operand doubleDividedByOne = Operand.temporary("doubleDividedByOne", Operand.Type.F64);
        Operand reflexiveInteger = Operand.temporary("reflexiveInteger", Operand.Type.BOOL);
        Operand reflexiveDouble = Operand.temporary("reflexiveDouble", Operand.Type.BOOL);

        IRProgram input = new IRProgram();
        input.add(new IRInstruction(IROpCode.LABEL,
                Operand.label("IROptimizerStrengthReduction.main__void__ret_void"), null, null));
        input.add(new IRInstruction(IROpCode.MUL, timesTwo, integer,
                Operand.constant(2, Operand.Type.I32)));
        input.add(print(timesTwo));
        input.add(new IRInstruction(IROpCode.MUL, timesNegativeOne, integerForNegation,
                Operand.constant(-1, Operand.Type.I32)));
        input.add(print(timesNegativeOne));
        input.add(new IRInstruction(IROpCode.SUB, zeroMinusValue,
                Operand.constant(0, Operand.Type.I32), integerForZeroSubtraction));
        input.add(print(zeroMinusValue));
        input.add(new IRInstruction(IROpCode.SUB, sameMinusSame, integer, integer));
        input.add(print(sameMinusSame));
        input.add(new IRInstruction(IROpCode.MUL, doubleTimesOne, floating,
                Operand.constant(1.0d, Operand.Type.F64)));
        input.add(print(doubleTimesOne));
        input.add(new IRInstruction(IROpCode.DIV, doubleDividedByOne, floating,
                Operand.constant(1.0d, Operand.Type.F64)));
        input.add(print(doubleDividedByOne));
        input.add(new IRInstruction(IROpCode.EQ, reflexiveInteger, integer, integer));
        input.add(print(reflexiveInteger));
        input.add(new IRInstruction(IROpCode.EQ, reflexiveDouble, floating, floating));
        input.add(print(reflexiveDouble));
        input.add(new IRInstruction(IROpCode.RETURN, null, null, null));

        IRProgram optimized = new IROptimizer().optimize(input);
        assertOpcode(optimized, timesTwo, IROpCode.ADD);
        assertOpcode(optimized, timesNegativeOne, IROpCode.NEG);
        assertOpcode(optimized, zeroMinusValue, IROpCode.NEG);
        assertPrintedConstant(optimized, 0);
        assertPrintedOperandCount(optimized, floating, 2);
        if (optimized.instructions().stream().anyMatch(instruction ->
                instruction.opCode() == IROpCode.MUL || instruction.opCode() == IROpCode.DIV)) {
            throw new AssertionError("Identity operations should not remain in optimized IR.");
        }
        assertPrintedConstant(optimized, true);
        assertOpcode(optimized, reflexiveDouble, IROpCode.EQ);
        System.out.println("IROptimizer strength-reduction checks passed.");
    }

    private static IRInstruction print(Operand value) {
        return new IRInstruction(IROpCode.PRINT, null, null, null, List.of(value));
    }

    private static IRInstruction instructionFor(IRProgram program, Operand destination) {
        return program.instructions().stream()
                .filter(instruction -> destination.equals(instruction.destination()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing optimized result for " + destination
                                + "; optimized IR: " + program.instructions()));
    }

    private static void assertOpcode(IRProgram program, Operand destination, IROpCode expected) {
        IROpCode actual = instructionFor(program, destination).opCode();
        if (actual != expected) {
            throw new AssertionError("Expected " + destination + " to use " + expected + ", got " + actual
                    + "; optimized IR: " + program.instructions());
        }
    }

    private static void assertPrintedConstant(IRProgram program, Object expected) {
        boolean found = program.instructions().stream()
                .filter(instruction -> instruction.opCode() == IROpCode.PRINT)
                .flatMap(instruction -> instruction.arguments().stream())
                .anyMatch(argument -> argument.kind() == Operand.Kind.CONSTANT
                        && expected.equals(argument.value()));
        if (!found) {
            throw new AssertionError("Expected optimized IR to print constant " + expected
                    + "; optimized IR: " + program.instructions());
        }
    }

    private static void assertPrintedOperandCount(IRProgram program, Operand expected, int count) {
        long actual = program.instructions().stream()
                .filter(instruction -> instruction.opCode() == IROpCode.PRINT)
                .flatMap(instruction -> instruction.arguments().stream())
                .filter(expected::equals)
                .count();
        if (actual != count) {
            throw new AssertionError("Expected operand " + expected + " to be printed " + count
                    + " times, got " + actual + "; optimized IR: " + program.instructions());
        }
    }
}
