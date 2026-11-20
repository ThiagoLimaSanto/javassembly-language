package compiler.backend.optimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import compiler.backend.ir.IRInstruction;
import compiler.backend.ir.IROpCode;
import compiler.backend.ir.IRProgram;
import compiler.backend.ir.Operand;

public class IROptimizer {
    private static final int MAX_OPTIMIZATION_ROUNDS = 8;

    private static final class BasicBlock {
        private final int id;
        private final List<IRInstruction> instructions = new ArrayList<>();
        private final Set<BasicBlock> predecessors = new LinkedHashSet<>();
        private final Set<BasicBlock> successors = new LinkedHashSet<>();
        private Map<Operand, Operand> inConstants;
        private Map<Operand, Operand> outConstants;

        private BasicBlock(int id) {
            this.id = id;
        }

        private IRInstruction last() {
            return instructions.get(instructions.size() - 1);
        }

        private IRInstruction first() {
            return instructions.get(0);
        }

        private boolean isFunctionEntry() {
            return instructions.size() > 0 && isFunctionLabel(first());
        }
    }

    private record ExpressionKey(
            IROpCode opcode,
            Operand left,
            Operand right,
            Operand.Type resultType) {
    }

    public IRProgram optimize(IRProgram input) {
        IRProgram current = input;
        for (int round = 0; round < MAX_OPTIMIZATION_ROUNDS; round++) {
            IRProgram next = removeUnreachable(current);
            next = propagateConstants(next);
            next = propagateStaticConstants(next);
            next = simplifyConstantBranches(next);
            next = removeRedundantJumps(next);
            next = removeUnreachable(next);
            next = propagateCopies(next);
            next = simplifyIntegerIdentity(next);
            next = eliminateCommonSubexpressions(next);
            next = eliminateDeadStores(next);
            next = eliminateDeadTemporaries(next);
            if (next.instructions().equals(current.instructions())) {
                return next;
            }
            current = next;
        }
        return current;
    }

    private List<BasicBlock> buildControlFlowGraph(IRProgram program) {
        List<BasicBlock> blocks = new ArrayList<>();
        BasicBlock current = null;

        for (IRInstruction instruction : program.instructions()) {
            boolean startsBlock = instruction.opCode() == IROpCode.LABEL
                    || (current != null && isTerminator(current.last().opCode()));
            if (current == null || (startsBlock && !current.instructions.isEmpty())) {
                current = new BasicBlock(blocks.size());
                blocks.add(current);
            }
            current.instructions.add(instruction);
        }

        Map<String, BasicBlock> labels = new HashMap<>();
        for (BasicBlock block : blocks) {
            IRInstruction first = block.first();
            if (first.opCode() == IROpCode.LABEL && first.destination() != null) {
                labels.put(first.destination().value().toString(), block);
            }
        }

        for (int index = 0; index < blocks.size(); index++) {
            BasicBlock block = blocks.get(index);
            for (IRInstruction instruction : block.instructions) {
                if (instruction.opCode() == IROpCode.PUSH_EXCEPTION_HANDLER) {
                    addJumpEdge(block, labels, instruction.destination());
                }
            }
            IRInstruction last = block.last();
            switch (last.opCode()) {
                case JUMP -> addJumpEdge(block, labels, last.destination());
                case JUMP_IF_FALSE, JUMP_IF_TRUE -> {
                    addJumpEdge(block, labels, last.destination());
                    addFallthroughEdge(blocks, index, block);
                }
                case THROW -> {
                    if (last.destination() != null) addJumpEdge(block, labels, last.destination());
                }
                case CHECK_EXCEPTION -> {
                    if (last.destination() != null) addJumpEdge(block, labels, last.destination());
                    addFallthroughEdge(blocks, index, block);
                }
                case RETURN -> {

                }
                default -> addFallthroughEdge(blocks, index, block);
            }
        }
        return blocks;
    }

    private void addJumpEdge(
            BasicBlock source,
            Map<String, BasicBlock> labels,
            Operand target) {
        if (target == null)
            return;
        BasicBlock destination = labels.get(target.value().toString());
        if (destination != null) {
            source.successors.add(destination);
            destination.predecessors.add(source);
        }
    }

    private void addFallthroughEdge(List<BasicBlock> blocks, int index, BasicBlock source) {
        if (index + 1 >= blocks.size())
            return;
        BasicBlock next = blocks.get(index + 1);
        if (next.isFunctionEntry())
            return;
        source.successors.add(next);
        next.predecessors.add(source);
    }

    private IRProgram removeUnreachable(IRProgram input) {
        List<BasicBlock> blocks = buildControlFlowGraph(input);
        if (blocks.isEmpty())
            return input;

        Set<BasicBlock> reachable = new HashSet<>();
        Deque<BasicBlock> worklist = new ArrayDeque<>();
        for (BasicBlock block : blocks) {
            if (block.isFunctionEntry()) {
                reachable.add(block);
                worklist.add(block);
            }
        }
        if (worklist.isEmpty()) {
            reachable.add(blocks.get(0));
            worklist.add(blocks.get(0));
        }

        while (!worklist.isEmpty()) {
            BasicBlock block = worklist.removeFirst();
            for (BasicBlock successor : block.successors) {
                if (reachable.add(successor))
                    worklist.addLast(successor);
            }
        }

        IRProgram output = new IRProgram();
        for (BasicBlock block : blocks) {
            if (reachable.contains(block)) {
                block.instructions.forEach(output::add);
            }
        }
        return output;
    }

    private IRProgram propagateConstants(IRProgram input) {
        List<BasicBlock> blocks = buildControlFlowGraph(input);
        if (blocks.isEmpty())
            return input;
        Set<BasicBlock> reachable = reachableBlocks(blocks);

        for (BasicBlock block : blocks) {
            if (block.isFunctionEntry()) {
                block.inConstants = new HashMap<>();
            }
        }

        boolean changed;
        int rounds = 0;
        do {
            changed = false;
            for (BasicBlock block : blocks) {
                if (!reachable.contains(block))
                    continue;
                Map<Operand, Operand> incoming = mergeConstantInputs(block, reachable);
                if (incoming == null)
                    continue;
                Map<Operand, Operand> outgoing = transferConstants(block, incoming);
                if (!incoming.equals(block.inConstants) || !outgoing.equals(block.outConstants)) {
                    block.inConstants = incoming;
                    block.outConstants = outgoing;
                    changed = true;
                }
            }
            rounds++;
        } while (changed && rounds <= blocks.size() * 4 + 8);

        IRProgram output = new IRProgram();
        for (BasicBlock block : blocks) {
            if (!reachable.contains(block))
                continue;
            Map<Operand, Operand> constants = block.inConstants == null
                    ? new HashMap<>()
                    : new HashMap<>(block.inConstants);
            for (IRInstruction instruction : block.instructions) {
                IRInstruction rewritten = rewriteOperands(instruction, constants);
                rewritten = foldConstants(rewritten);
                output.add(rewritten);
                updateConstants(rewritten, constants);
            }
        }
        return output;
    }

    private Set<BasicBlock> reachableBlocks(List<BasicBlock> blocks) {
        Set<BasicBlock> reachable = new HashSet<>();
        Deque<BasicBlock> worklist = new ArrayDeque<>();
        for (BasicBlock block : blocks) {
            if (block.isFunctionEntry()) {
                reachable.add(block);
                worklist.add(block);
            }
        }
        if (worklist.isEmpty() && !blocks.isEmpty()) {
            reachable.add(blocks.get(0));
            worklist.add(blocks.get(0));
        }
        while (!worklist.isEmpty()) {
            for (BasicBlock successor : worklist.removeFirst().successors) {
                if (reachable.add(successor))
                    worklist.addLast(successor);
            }
        }
        return reachable;
    }

    private Map<Operand, Operand> mergeConstantInputs(
            BasicBlock block,
            Set<BasicBlock> reachable) {
        Map<Operand, Operand> merged = null;
        if (block.isFunctionEntry())
            merged = new HashMap<>();
        for (BasicBlock predecessor : block.predecessors) {
            if (!reachable.contains(predecessor) || predecessor.outConstants == null)
                continue;
            if (merged == null) {
                merged = new HashMap<>(predecessor.outConstants);
            } else {
                merged.entrySet().removeIf(
                        entry -> !Objects.equals(predecessor.outConstants.get(entry.getKey()), entry.getValue()));
            }
        }
        return merged;
    }

    private Map<Operand, Operand> transferConstants(
            BasicBlock block,
            Map<Operand, Operand> input) {
        Map<Operand, Operand> constants = new HashMap<>(input);
        for (IRInstruction instruction : block.instructions) {
            updateConstants(rewriteOperands(instruction, constants), constants);
        }
        return constants;
    }

    private void updateConstants(IRInstruction instruction, Map<Operand, Operand> constants) {
        Operand destination = instruction.destination();
        if (destination != null) {
            if (instruction.opCode() == IROpCode.ASSIGN) {
                Operand value = instruction.left();
                if (value != null && value.kind() == Operand.Kind.CONSTANT) {
                    constants.put(destination, value);
                } else {
                    constants.remove(destination);
                }
            } else {
                constants.remove(destination);
            }
        }
        if (isFreeOperation(instruction.opCode())) {
            Operand freedReference = instruction.left();
            if (isMutableReferenceSlot(freedReference)) {
                constants.put(freedReference, Operand.constant(null, Operand.Type.REF));
            }
        }
        if (instruction.opCode() == IROpCode.CALL || instruction.opCode() == IROpCode.CALL_VIRTUAL)
            constants.clear();
        if (isExceptionBarrier(instruction.opCode())) constants.clear();
    }

    private IRProgram propagateStaticConstants(IRProgram input) {
        IRProgram output = new IRProgram();
        for (BasicBlock block : buildControlFlowGraph(input)) {
            Map<String, Operand> values = new HashMap<>();
            for (IRInstruction instruction : block.instructions) {
                if (instruction.opCode() == IROpCode.LOAD_STATIC) {
                    String label = staticFieldLabel(instruction.left());
                    Operand knownValue = values.get(label);
                    if (knownValue != null) {
                        output.add(new IRInstruction(
                                IROpCode.ASSIGN,
                                instruction.destination(),
                                knownValue,
                                null));
                    } else {
                        output.add(instruction);
                    }
                    continue;
                }

                output.add(instruction);
                if (instruction.opCode() == IROpCode.STORE_STATIC) {
                    String label = staticFieldLabel(instruction.left());
                    Operand value = instruction.arguments().size() == 1
                            ? instruction.arguments().get(0) : null;
                    if (label != null && value != null && isConstant(value, value.type())) {
                        values.put(label, value);
                    } else if (label != null) {
                        values.remove(label);
                    }
                } else if (instruction.opCode() == IROpCode.CALL
                        || instruction.opCode() == IROpCode.CALL_VIRTUAL) {
                    // Any called method can write a static field, including through an interface.
                    values.clear();
                }
            }
        }
        return output;
    }

    private String staticFieldLabel(Operand operand) {
        if (operand == null || operand.kind() != Operand.Kind.LABEL) return null;
        return operand.value().toString();
    }

    private IRInstruction rewriteOperands(
            IRInstruction instruction,
            Map<Operand, Operand> constants) {
        List<Operand> arguments = instruction.arguments().stream()
                .map(argument -> resolveConstant(argument, constants))
                .toList();
        return new IRInstruction(
                instruction.opCode(),
                instruction.destination(),
                resolveConstant(instruction.left(), constants),
                resolveConstant(instruction.right(), constants),
                arguments);
    }

    private Operand resolveConstant(Operand operand, Map<Operand, Operand> constants) {
        if (operand == null)
            return null;
        return constants.getOrDefault(operand, operand);
    }

    private IRProgram simplifyConstantBranches(IRProgram input) {
        IRProgram output = new IRProgram();
        for (IRInstruction instruction : input.instructions()) {
            boolean conditional = instruction.opCode() == IROpCode.JUMP_IF_FALSE
                    || instruction.opCode() == IROpCode.JUMP_IF_TRUE;
            Operand condition = instruction.left();
            boolean booleanConstant = condition != null
                    && condition.kind() == Operand.Kind.CONSTANT
                    && condition.type() == Operand.Type.BOOL
                    && condition.value() instanceof Boolean;
            if (!conditional || !booleanConstant) {
                output.add(instruction);
                continue;
            }
            boolean value = (Boolean) condition.value();
            boolean shouldJump = instruction.opCode() == IROpCode.JUMP_IF_TRUE ? value : !value;
            if (shouldJump) {
                output.add(new IRInstruction(IROpCode.JUMP, instruction.destination(), null, null));
            }
        }
        return output;
    }

    private IRProgram removeRedundantJumps(IRProgram input) {
        List<IRInstruction> instructions = input.instructions();
        IRProgram output = new IRProgram();
        for (int index = 0; index < instructions.size(); index++) {
            IRInstruction instruction = instructions.get(index);
            if (instruction.opCode() == IROpCode.JUMP
                    && instruction.destination() != null
                    && index + 1 < instructions.size()) {
                String target = instruction.destination().value().toString();
                int next = index + 1;
                while (next < instructions.size()
                        && instructions.get(next).opCode() == IROpCode.LABEL
                        && instructions.get(next).destination() != null) {
                    if (isFunctionLabel(instructions.get(next))) break;
                    if (target.equals(instructions.get(next).destination().value().toString())) {
                        instruction = null;
                        break;
                    }
                    next++;
                }
            }
            if (instruction != null) output.add(instruction);
        }
        return output;
    }

    private IRProgram propagateCopies(IRProgram input) {
        IRProgram output = new IRProgram();
        for (BasicBlock block : buildControlFlowGraph(input)) {
            Map<Operand, Operand> copies = new HashMap<>();
            for (IRInstruction instruction : block.instructions) {
                IRInstruction rewritten = rewriteCopies(instruction, copies);
                output.add(rewritten);
                if (isOptimizationBarrier(rewritten.opCode()))
                    copies.clear();

                Operand destination = rewritten.destination();
                if (destination == null)
                    continue;
                removeCopiesFor(destination, copies);
                if (rewritten.opCode() == IROpCode.ASSIGN
                        && rewritten.left() != null
                        && destination.type() != Operand.Type.REF
                        && (rewritten.left().kind() == Operand.Kind.VARIABLE
                                || rewritten.left().kind() == Operand.Kind.TEMPORARY)
                        && !destination.equals(rewritten.left())) {
                    copies.put(destination, rewritten.left());
                }
            }
        }
        return output;
    }

    private IRInstruction rewriteCopies(IRInstruction instruction, Map<Operand, Operand> copies) {
        List<Operand> arguments = instruction.arguments().stream()
                .map(argument -> resolveCopy(argument, copies))
                .toList();
        return new IRInstruction(instruction.opCode(), instruction.destination(),
                resolveCopy(instruction.left(), copies), resolveCopy(instruction.right(), copies), arguments);
    }

    private Operand resolveCopy(Operand operand, Map<Operand, Operand> copies) {
        if (operand == null)
            return null;
        Operand current = operand;
        Set<Operand> visited = new HashSet<>();
        while (copies.containsKey(current) && visited.add(current)) {
            current = copies.get(current);
        }
        return current;
    }

    private void removeCopiesFor(Operand destination, Map<Operand, Operand> copies) {
        copies.remove(destination);
        copies.entrySet().removeIf(entry -> resolveCopy(entry.getValue(), copies).equals(destination));
    }

    private IRProgram eliminateCommonSubexpressions(IRProgram input) {
        IRProgram output = new IRProgram();
        for (BasicBlock block : buildControlFlowGraph(input)) {
            Map<ExpressionKey, Operand> available = new HashMap<>();
            for (IRInstruction instruction : block.instructions) {
                Operand destination = instruction.destination();
                if (isOptimizationBarrier(instruction.opCode())) {
                    available.clear();
                }
                if (destination != null && destination.kind() == Operand.Kind.VARIABLE) {
                    available.keySet().removeIf(key -> key.left().equals(destination)
                            || (key.right() != null && key.right().equals(destination)));
                }

                ExpressionKey key = expressionKey(instruction);
                if (key == null || destination == null || destination.kind() != Operand.Kind.TEMPORARY) {
                    output.add(instruction);
                    continue;
                }
                Operand previous = available.get(key);
                if (previous == null) {
                    available.put(key, destination);
                    output.add(instruction);
                } else {
                    output.add(new IRInstruction(IROpCode.ASSIGN, destination, previous, null));
                }
            }
        }
        return output;
    }

    private ExpressionKey expressionKey(IRInstruction instruction) {
        if (!isCseCandidate(instruction.opCode()) || instruction.left() == null)
            return null;
        Operand left = instruction.left();
        Operand right = instruction.right();
        if (isCommutative(instruction.opCode()) && right != null
                && left.toString().compareTo(right.toString()) > 0) {
            Operand swap = left;
            left = right;
            right = swap;
        }
        return new ExpressionKey(instruction.opCode(), left, right,
                instruction.destination() == null ? null : instruction.destination().type());
    }

    private boolean isCseCandidate(IROpCode opcode) {
        return switch (opcode) {
            case CAST_I32_TO_I64, CAST_I32_TO_F64, CAST_I64_TO_F64,
                    ADD, SUB, MUL, NEG, LT, GT, LE, GE, EQ, NE, LOAD_STATIC ->
                true;
            default -> false;
        };
    }

    private boolean isCommutative(IROpCode opcode) {
        return opcode == IROpCode.ADD || opcode == IROpCode.MUL
                || opcode == IROpCode.EQ || opcode == IROpCode.NE;
    }

    private IRProgram eliminateDeadStores(IRProgram input) {
        List<BasicBlock> blocks = buildControlFlowGraph(input);
        if (blocks.isEmpty())
            return input;
        Set<BasicBlock> reachable = reachableBlocks(blocks);
        Set<Operand> allVariables = new HashSet<>();
        for (BasicBlock block : blocks) {
            for (IRInstruction instruction : block.instructions) {
                collectVariable(instruction.destination(), allVariables);
                collectVariable(instruction.left(), allVariables);
                collectVariable(instruction.right(), allVariables);
                for (Operand argument : instruction.arguments())
                    collectVariable(argument, allVariables);
            }
        }

        Map<BasicBlock, Set<Operand>> uses = new HashMap<>();
        Map<BasicBlock, Set<Operand>> definitions = new HashMap<>();
        Map<BasicBlock, Set<Operand>> liveIn = new HashMap<>();
        Map<BasicBlock, Set<Operand>> liveOut = new HashMap<>();
        for (BasicBlock block : blocks) {
            Set<Operand> blockUses = new HashSet<>();
            Set<Operand> blockDefinitions = new HashSet<>();
            for (IRInstruction instruction : block.instructions) {
                addVariableUse(instruction.left(), blockUses, blockDefinitions);
                addVariableUse(instruction.right(), blockUses, blockDefinitions);
                for (Operand argument : instruction.arguments()) {
                    addVariableUse(argument, blockUses, blockDefinitions);
                }
                if (isFreeOperation(instruction.opCode())
                        && instruction.left() != null
                        && instruction.left().kind() == Operand.Kind.VARIABLE) {
                    blockDefinitions.add(instruction.left());
                }
                Operand destination = instruction.destination();
                if (destination != null && destination.kind() == Operand.Kind.VARIABLE) {
                    blockDefinitions.add(destination);
                }
            }
            uses.put(block, blockUses);
            definitions.put(block, blockDefinitions);
            liveIn.put(block, new HashSet<>());
            liveOut.put(block, new HashSet<>());
        }

        boolean changed;
        do {
            changed = false;
            for (int index = blocks.size() - 1; index >= 0; index--) {
                BasicBlock block = blocks.get(index);
                if (!reachable.contains(block))
                    continue;
                Set<Operand> out = new HashSet<>();
                for (BasicBlock successor : block.successors) {
                    if (reachable.contains(successor))
                        out.addAll(liveIn.get(successor));
                }
                Set<Operand> in = new HashSet<>(out);
                in.removeAll(definitions.get(block));
                in.addAll(uses.get(block));
                if (!out.equals(liveOut.get(block)) || !in.equals(liveIn.get(block))) {
                    liveOut.put(block, out);
                    liveIn.put(block, in);
                    changed = true;
                }
            }
        } while (changed);

        IRProgram output = new IRProgram();
        for (BasicBlock block : blocks) {
            if (!reachable.contains(block))
                continue;
            Set<Operand> live = new HashSet<>(liveOut.get(block));
            List<IRInstruction> kept = new ArrayList<>();
            for (int index = block.instructions.size() - 1; index >= 0; index--) {
                IRInstruction instruction = block.instructions.get(index);
                Operand destination = instruction.destination();
                if (instruction.opCode() == IROpCode.CALL
                        || instruction.opCode() == IROpCode.CALL_VIRTUAL) {

                    live.addAll(allVariables);
                }
                if (isFreeOperation(instruction.opCode())
                        && instruction.left() != null
                        && instruction.left().kind() == Operand.Kind.VARIABLE) {
                    live.remove(instruction.left());
                }
                boolean removableStore = destination != null
                        && destination.kind() == Operand.Kind.VARIABLE
                        && instruction.opCode() == IROpCode.ASSIGN
                        && !live.contains(destination);
                if (!removableStore) {
                    kept.add(instruction);
                    if (destination != null && destination.kind() == Operand.Kind.VARIABLE) {
                        live.remove(destination);
                    }
                    addVariableUse(instruction.left(), live, Set.of());
                    addVariableUse(instruction.right(), live, Set.of());
                    for (Operand argument : instruction.arguments()) {
                        addVariableUse(argument, live, Set.of());
                    }
                }
            }
            for (int index = kept.size() - 1; index >= 0; index--)
                output.add(kept.get(index));
        }
        return output;
    }

    private void collectVariable(Operand operand, Set<Operand> variables) {
        if (operand != null && operand.kind() == Operand.Kind.VARIABLE)
            variables.add(operand);
    }

    private void addVariableUse(
            Operand operand,
            Set<Operand> uses,
            Set<Operand> definitions) {
        if (operand != null && operand.kind() == Operand.Kind.VARIABLE
                && !definitions.contains(operand)) {
            uses.add(operand);
        }
    }

    private IRProgram eliminateDeadTemporaries(IRProgram input) {
        List<IRInstruction> instructions = input.instructions();
        boolean changed;
        do {
            Map<Operand, Integer> uses = new HashMap<>();
            for (IRInstruction instruction : instructions) {
                countTemporaryUse(instruction.left(), uses);
                countTemporaryUse(instruction.right(), uses);
                for (Operand argument : instruction.arguments())
                    countTemporaryUse(argument, uses);
            }
            List<IRInstruction> kept = new ArrayList<>();
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
        instructions.forEach(output::add);
        return output;
    }

    private void countTemporaryUse(Operand operand, Map<Operand, Integer> uses) {
        if (operand != null && operand.kind() == Operand.Kind.TEMPORARY) {
            uses.merge(operand, 1, Integer::sum);
        }
    }

    private IRProgram simplifyIntegerIdentity(IRProgram input) {
        IRProgram output = new IRProgram();
        for (IRInstruction instruction : input.instructions()) {
            if (instruction.opCode() == IROpCode.ASSIGN
                    && instruction.destination() != null
                    && instruction.destination().equals(instruction.left())) {
                continue;
            }
            output.add(simplifyIdentity(instruction));
        }
        return output;
    }

    private IRInstruction simplifyIdentity(IRInstruction instruction) {
        Operand destination = instruction.destination();
        if (destination == null || !isIntegerType(destination.type()))
            return instruction;
        Operand left = instruction.left();
        Operand right = instruction.right();
        if (instruction.opCode() == IROpCode.ADD) {
            if (isIntegerConstant(left, destination.type(), 0L))
                return assign(destination, right);
            if (isIntegerConstant(right, destination.type(), 0L))
                return assign(destination, left);
        }
        if (instruction.opCode() == IROpCode.SUB
                && isIntegerConstant(right, destination.type(), 0L)) {
            return assign(destination, left);
        }
        if (instruction.opCode() == IROpCode.MUL) {
            if (isIntegerConstant(left, destination.type(), 0L)
                    || isIntegerConstant(right, destination.type(), 0L)) {
                return assign(destination, Operand.constant(
                        destination.type() == Operand.Type.I32 ? 0 : 0L, destination.type()));
            }
            if (isIntegerConstant(left, destination.type(), 1L))
                return assign(destination, right);
            if (isIntegerConstant(right, destination.type(), 1L))
                return assign(destination, left);
        }
        if (instruction.opCode() == IROpCode.DIV
                && isIntegerConstant(right, destination.type(), 1L)) {
            return assign(destination, left);
        }
        if (instruction.opCode() == IROpCode.MOD
                && isIntegerConstant(right, destination.type(), 1L)) {
            return assign(destination, Operand.constant(
                    destination.type() == Operand.Type.I32 ? 0 : 0L, destination.type()));
        }
        return instruction;
    }

    private IRInstruction assign(Operand destination, Operand source) {
        return new IRInstruction(IROpCode.ASSIGN, destination, source, null);
    }

    private IRInstruction foldConstants(IRInstruction instruction) {
        IROpCode opcode = instruction.opCode();
        Operand destination = instruction.destination();
        Operand left = instruction.left();
        Operand right = instruction.right();
        if (destination == null || left == null)
            return instruction;

        if (opcode == IROpCode.CAST_I32_TO_I64 && isConstant(left, Operand.Type.I32)) {
            return assign(destination, Operand.constant(((Integer) left.value()).longValue(), Operand.Type.I64));
        }
        if (opcode == IROpCode.CAST_I32_TO_F64 && isConstant(left, Operand.Type.I32)) {
            return assign(destination, Operand.constant(((Integer) left.value()).doubleValue(), Operand.Type.F64));
        }
        if (opcode == IROpCode.CAST_I64_TO_F64 && isConstant(left, Operand.Type.I64)) {
            return assign(destination, Operand.constant(((Long) left.value()).doubleValue(), Operand.Type.F64));
        }
        if (opcode == IROpCode.NEG) {
            if (isConstant(left, Operand.Type.I32)) {
                return assign(destination, Operand.constant(-((Integer) left.value()), Operand.Type.I32));
            }
            if (isConstant(left, Operand.Type.I64)) {
                return assign(destination, Operand.constant(-((Long) left.value()), Operand.Type.I64));
            }
            if (isConstant(left, Operand.Type.F64)) {
                return assign(destination, Operand.constant(-((Double) left.value()), Operand.Type.F64));
            }
            return instruction;
        }
        if (right == null)
            return instruction;

        Operand.Type type = left.type();
        if (type != right.type())
            return instruction;
        if (isIntegerType(type) && isConstant(left, type) && isConstant(right, type)) {
            if (isArithmetic(opcode)) {
                long a = integerValue(left);
                long b = integerValue(right);
                if ((opcode == IROpCode.DIV || opcode == IROpCode.MOD) && b == 0)
                    return instruction;
                Object result = foldInteger(opcode, a, b, type);
                if (result != null)
                    return assign(destination, Operand.constant(result, type));
            }
            Boolean comparison = foldIntegerComparison(opcode, integerValue(left), integerValue(right));
            if (comparison != null && destination.type() == Operand.Type.BOOL) {
                return assign(destination, Operand.constant(comparison, Operand.Type.BOOL));
            }
        }
        if (type == Operand.Type.F64
                && isConstant(left, type) && isConstant(right, type)) {
            double a = (Double) left.value();
            double b = (Double) right.value();
            if (isArithmetic(opcode)) {
                Object result = switch (opcode) {
                    case ADD -> a + b;
                    case SUB -> a - b;
                    case MUL -> a * b;
                    case DIV -> a / b;
                    default -> null;
                };
                if (result != null)
                    return assign(destination, Operand.constant(result, Operand.Type.F64));
            }
            Boolean comparison = foldDoubleComparison(opcode, a, b);
            if (comparison != null && destination.type() == Operand.Type.BOOL) {
                return assign(destination, Operand.constant(comparison, Operand.Type.BOOL));
            }
        }
        if (type == Operand.Type.BOOL && isConstant(left, type) && isConstant(right, type)
                && destination.type() == Operand.Type.BOOL) {
            boolean a = (Boolean) left.value();
            boolean b = (Boolean) right.value();
            if (opcode == IROpCode.EQ)
                return assign(destination, Operand.constant(a == b, Operand.Type.BOOL));
            if (opcode == IROpCode.NE)
                return assign(destination, Operand.constant(a != b, Operand.Type.BOOL));
        }
        if (type == Operand.Type.REF && destination.type() == Operand.Type.BOOL
                && (opcode == IROpCode.EQ || opcode == IROpCode.NE)
                && isNullReference(left) && isNullReference(right)) {
            return assign(destination, Operand.constant(opcode == IROpCode.EQ, Operand.Type.BOOL));
        }
        return instruction;
    }

    private Object foldInteger(IROpCode opcode, long a, long b, Operand.Type type) {
        if (type == Operand.Type.I32) {
            int left = (int) a;
            int right = (int) b;
            return switch (opcode) {
                case ADD -> left + right;
                case SUB -> left - right;
                case MUL -> left * right;
                case DIV -> left / right;
                case MOD -> left % right;
                default -> null;
            };
        }
        return switch (opcode) {
            case ADD -> a + b;
            case SUB -> a - b;
            case MUL -> a * b;
            case DIV -> a / b;
            case MOD -> a % b;
            default -> null;
        };
    }

    private Boolean foldIntegerComparison(IROpCode opcode, long left, long right) {
        return switch (opcode) {
            case LT -> left < right;
            case GT -> left > right;
            case LE -> left <= right;
            case GE -> left >= right;
            case EQ -> left == right;
            case NE -> left != right;
            default -> null;
        };
    }

    private Boolean foldDoubleComparison(IROpCode opcode, double left, double right) {
        return switch (opcode) {
            case LT -> left < right;
            case GT -> left > right;
            case LE -> left <= right;
            case GE -> left >= right;
            case EQ -> left == right;
            case NE -> left != right;
            default -> null;
        };
    }

    private boolean isArithmetic(IROpCode opcode) {
        return opcode == IROpCode.ADD || opcode == IROpCode.SUB || opcode == IROpCode.MUL
                || opcode == IROpCode.DIV || opcode == IROpCode.MOD;
    }

    private boolean isIntegerType(Operand.Type type) {
        return type == Operand.Type.I32 || type == Operand.Type.I64;
    }

    private long integerValue(Operand operand) {
        return operand.type() == Operand.Type.I32
                ? ((Integer) operand.value()).longValue()
                : (Long) operand.value();
    }

    private boolean isIntegerConstant(Operand operand, Operand.Type type, long value) {
        return isConstant(operand, type) && integerValue(operand) == value;
    }

    private boolean isConstant(Operand operand, Operand.Type type) {
        if (operand == null || operand.kind() != Operand.Kind.CONSTANT || operand.type() != type) {
            return false;
        }
        return switch (type) {
            case I32 -> operand.value() instanceof Integer;
            case I64 -> operand.value() instanceof Long;
            case F64 -> operand.value() instanceof Double;
            case BOOL -> operand.value() instanceof Boolean;
            case REF -> operand.value() == null || operand.value() instanceof String;
        };
    }

    private boolean isPure(IROpCode opcode) {
        return switch (opcode) {
            case ASSIGN, CAST_I32_TO_I64, CAST_I32_TO_F64, CAST_I64_TO_F64,
                    ADD, SUB, MUL, NEG, LT, GT, LE, GE, EQ, NE ->
                true;
            default -> false;
        };
    }

    private boolean isOptimizationBarrier(IROpCode opcode) {
        return switch (opcode) {
            case CALL, CALL_VIRTUAL, ALLOC_OBJECT, FREE_OBJECT, ALLOC_ARRAY, FREE_ARRAY,
                    STORE_FIELD, STORE_STATIC, STORE_ARRAY,
                    SOURCE_LOCATION,
                    THROW, CHECK_EXCEPTION, GET_EXCEPTION, CLEAR_EXCEPTION, CATCH_MATCH,
                    PUSH_EXCEPTION_HANDLER, POP_EXCEPTION_HANDLER_IF,
                    EXCEPTION_GET_MESSAGE, EXCEPTION_GET_TYPE -> true;
            default -> false;
        };
    }

    private boolean isExceptionBarrier(IROpCode opcode) {
        return opcode == IROpCode.THROW
                || opcode == IROpCode.CHECK_EXCEPTION
                || opcode == IROpCode.GET_EXCEPTION
                || opcode == IROpCode.CLEAR_EXCEPTION
                || opcode == IROpCode.CATCH_MATCH
                || opcode == IROpCode.PUSH_EXCEPTION_HANDLER
                || opcode == IROpCode.POP_EXCEPTION_HANDLER_IF
                || opcode == IROpCode.EXCEPTION_GET_MESSAGE
                || opcode == IROpCode.EXCEPTION_GET_TYPE;
    }

    private boolean isFreeOperation(IROpCode opcode) {
        return opcode == IROpCode.FREE_OBJECT || opcode == IROpCode.FREE_ARRAY;
    }

    private boolean isMutableReferenceSlot(Operand operand) {
        return operand != null
                && operand.type() == Operand.Type.REF
                && (operand.kind() == Operand.Kind.VARIABLE
                        || operand.kind() == Operand.Kind.TEMPORARY);
    }

    private boolean isNullReference(Operand operand) {
        return operand != null
                && operand.kind() == Operand.Kind.CONSTANT
                && operand.type() == Operand.Type.REF
                && operand.value() == null;
    }

    private boolean isTerminator(IROpCode opcode) {
        return opcode == IROpCode.JUMP || opcode == IROpCode.JUMP_IF_FALSE
                || opcode == IROpCode.JUMP_IF_TRUE || opcode == IROpCode.RETURN
                || opcode == IROpCode.THROW || opcode == IROpCode.CHECK_EXCEPTION;
    }

    private static boolean isFunctionLabel(IRInstruction instruction) {
        return instruction.opCode() == IROpCode.LABEL
                && instruction.destination() != null
                && instruction.destination().kind() == Operand.Kind.LABEL
                && instruction.destination().value().toString().contains(".");
    }
}
