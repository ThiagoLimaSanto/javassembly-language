package src.backend.optimization;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import src.backend.ir.IRInstruction;
import src.backend.ir.IROpCode;
import src.backend.ir.IRProgram;
import src.backend.ir.Operand;

public class IROptimizer {
    public IRProgram optimize(IRProgram input) {
        IRProgram folded = new IRProgram();

        for (IRInstruction instruction : input.instructions()) {
            IRInstruction simplified = simplifyIntegerIdentity(instruction);
            folded.add(foldConstants(simplified));
        }

        IRProgram propagated = propagateConstants(folded);
        IRProgram branches = simplifyConstantBranches(propagated);
        IRProgram reachable = removeUnreachableInstructions(branches);
        return eliminateDeadTemporaries(reachable);
    }

    private IRProgram removeUnreachableInstructions(IRProgram input) {
        Set<String> branchTargets = new HashSet<>();

        for (IRInstruction instruction : input.instructions()) {
            if (instruction.opCode() == IROpCode.JUMP
                    || instruction.opCode() == IROpCode.JUMP_IF_FALSE
                    || instruction.opCode() == IROpCode.JUMP_IF_TRUE) {
                branchTargets.add(instruction.destination().value().toString());
            }
        }

        IRProgram output = new IRProgram();
        boolean reachable = true;

        for (IRInstruction instruction : input.instructions()) {
            if (!reachable) {
                boolean functionEntry = isFunctionLabel(instruction);
                boolean branchTarget = instruction.opCode() == IROpCode.LABEL
                        && branchTargets.contains(
                                instruction.destination().value().toString());

                if (!functionEntry && !branchTarget) {
                    continue;
                }
                reachable = true;
            }

            output.add(instruction);

            if (instruction.opCode() == IROpCode.JUMP
                    || instruction.opCode() == IROpCode.RETURN) {
                reachable = false;
            }
        }

        return output;
    }

    private boolean isFunctionLabel(IRInstruction instruction) {
        return instruction.opCode() == IROpCode.LABEL
                && instruction.destination() != null
                && instruction.destination().kind() == Operand.Kind.LABEL
                && instruction.destination().value().toString().contains(".");
    }

    private IRProgram simplifyConstantBranches(IRProgram input) {
        IRProgram output = new IRProgram();

        for (IRInstruction instruction : input.instructions()) {
            boolean conditionalJump = instruction.opCode() == IROpCode.JUMP_IF_FALSE
                    || instruction.opCode() == IROpCode.JUMP_IF_TRUE;

            Operand condition = instruction.left();
            boolean booleanConstant = condition != null
                    && condition.kind() == Operand.Kind.CONSTANT
                    && condition.type() == Operand.Type.BOOL
                    && condition.value() instanceof Boolean;

            if (!conditionalJump || !booleanConstant) {
                output.add(instruction);
                continue;
            }

            boolean value = (Boolean) condition.value();
            boolean shouldJump = instruction.opCode() == IROpCode.JUMP_IF_TRUE
                    ? value
                    : !value;

            if (shouldJump) {
                output.add(new IRInstruction(
                        IROpCode.JUMP,
                        instruction.destination(),
                        null,
                        null));
            }
        }

        return output;
    }

    private IRProgram eliminateDeadTemporaries(IRProgram input) {
        List<IRInstruction> instructions = input.instructions();
        boolean changed;

        do {
            Map<Operand, Integer> uses = new HashMap<>();

            for (IRInstruction instruction : instructions) {
                countTemporaryUse(instruction.left(), uses);
                countTemporaryUse(instruction.right(), uses);

                for (Operand argument : instruction.arguments()) {
                    countTemporaryUse(argument, uses);
                }
            }

            List<IRInstruction> kept = new java.util.ArrayList<>();
            changed = false;

            for (IRInstruction instruction : instructions) {
                Operand destination = instruction.destination();

                boolean unusedTemporary = destination != null
                        && destination.kind() == Operand.Kind.TEMPORARY
                        && uses.getOrDefault(destination, 0) == 0;

                if (unusedTemporary && isPure(instruction.opCode())) {
                    changed = true;
                } else {
                    kept.add(instruction);
                }
            }

            instructions = kept;
        } while (changed);

        IRProgram output = new IRProgram();
        for (IRInstruction instruction : instructions) {
            output.add(instruction);
        }
        return output;
    }

    private void countTemporaryUse(
            Operand operand,
            Map<Operand, Integer> uses) {
        if (operand != null && operand.kind() == Operand.Kind.TEMPORARY) {
            uses.merge(operand, 1, Integer::sum);
        }
    }

    private boolean isPure(IROpCode opCode) {
        return switch (opCode) {
            case ASSIGN, ADD, SUB, MUL, LT, GT, LE, GE, EQ, NE -> true;
            default -> false;
        };
    }

    private IRInstruction simplifyIntegerIdentity(IRInstruction instruction) {
        if (instruction.destination() == null
                || instruction.destination().type() != Operand.Type.I32) {
            return instruction;
        }

        Operand left = instruction.left();
        Operand right = instruction.right();

        if (instruction.opCode() == IROpCode.ADD) {
            if (isI32Constant(left) && (Integer) left.value() == 0) {
                return assign(instruction.destination(), right);
            }
            if (isI32Constant(right) && (Integer) right.value() == 0) {
                return assign(instruction.destination(), left);
            }
        }

        if (instruction.opCode() == IROpCode.SUB
                && isI32Constant(right)
                && (Integer) right.value() == 0) {
            return assign(instruction.destination(), left);
        }

        if (instruction.opCode() == IROpCode.MUL) {
            if ((isI32Constant(left) && (Integer) left.value() == 0)
                    || (isI32Constant(right) && (Integer) right.value() == 0)) {
                return assign(
                        instruction.destination(),
                        Operand.constant(0, Operand.Type.I32));
            }
            if (isI32Constant(left) && (Integer) left.value() == 1) {
                return assign(instruction.destination(), right);
            }
            if (isI32Constant(right) && (Integer) right.value() == 1) {
                return assign(instruction.destination(), left);
            }
        }

        return instruction;
    }

    private IRInstruction assign(Operand destination, Operand source) {
        return new IRInstruction(
                IROpCode.ASSIGN,
                destination,
                source,
                null);
    }

    private IRProgram propagateConstants(IRProgram input) {
        Map<Operand, Operand> constants = new HashMap<>();
        IRProgram output = new IRProgram();

        for (IRInstruction instruction : input.instructions()) {
            if (instruction.opCode() == IROpCode.LABEL) {
                constants.clear();
            }

            List<Operand> arguments = instruction.arguments().stream()
                    .map(argument -> resolve(argument, constants))
                    .toList();

            IRInstruction updated = new IRInstruction(
                    instruction.opCode(),
                    instruction.destination(),
                    resolve(instruction.left(), constants),
                    resolve(instruction.right(), constants),
                    arguments);

            updated = foldConstants(updated);
            output.add(updated);

            if (updated.opCode() == IROpCode.ASSIGN
                    && updated.destination() != null) {
                Operand value = updated.left();

                if (value != null && value.kind() == Operand.Kind.CONSTANT) {
                    constants.put(updated.destination(), value);
                } else {
                    constants.remove(updated.destination());
                }
            } else if (updated.destination() != null) {
                constants.remove(updated.destination());
            }

            switch (updated.opCode()) {
                case JUMP, JUMP_IF_FALSE, JUMP_IF_TRUE, CALL, RETURN ->
                    constants.clear();
                default -> {
                }
            }
        }

        return output;
    }

    private Operand resolve(
            Operand operand,
            Map<Operand, Operand> constants) {
        if (operand == null) {
            return null;
        }
        return constants.getOrDefault(operand, operand);
    }

    private IRInstruction foldConstants(IRInstruction instruction) {
        if (instruction.destination() == null
                || !isI32Constant(instruction.left())
                || !isI32Constant(instruction.right())) {
            return instruction;
        }

        int left = (Integer) instruction.left().value();
        int right = (Integer) instruction.right().value();

        Object value;
        Operand.Type resultType;

        switch (instruction.opCode()) {
            case ADD, SUB, MUL -> {
                if (instruction.destination().type() != Operand.Type.I32) {
                    return instruction;
                }

                value = switch (instruction.opCode()) {
                    case ADD -> left + right;
                    case SUB -> left - right;
                    case MUL -> left * right;
                    default -> throw new IllegalStateException();
                };
                resultType = Operand.Type.I32;
            }

            case LT, GT, LE, GE, EQ, NE -> {
                if (instruction.destination().type() != Operand.Type.BOOL) {
                    return instruction;
                }

                value = switch (instruction.opCode()) {
                    case LT -> left < right;
                    case GT -> left > right;
                    case LE -> left <= right;
                    case GE -> left >= right;
                    case EQ -> left == right;
                    case NE -> left != right;
                    default -> throw new IllegalStateException();
                };
                resultType = Operand.Type.BOOL;
            }

            default -> {
                return instruction;
            }
        }

        return new IRInstruction(
                IROpCode.ASSIGN,
                instruction.destination(),
                Operand.constant(value, resultType),
                null);
    }

    private boolean isI32Constant(Operand operand) {
        return operand != null
                && operand.kind() == Operand.Kind.CONSTANT
                && operand.type() == Operand.Type.I32
                && operand.value() instanceof Integer;
    }
}
