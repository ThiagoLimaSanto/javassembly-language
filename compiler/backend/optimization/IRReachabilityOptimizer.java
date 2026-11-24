package compiler.backend.optimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import compiler.ast.records.ProgramRecord;
import compiler.backend.ir.IRInstruction;
import compiler.backend.ir.IROpCode;
import compiler.backend.ir.IRProgram;
import compiler.backend.ir.Operand;
import compiler.backend.memory.VirtualMethodTable;

/** Removes function bodies that cannot be reached from the program entry point. */
public final class IRReachabilityOptimizer {
    private static final String STATIC_INITIALIZER = "__JVS.static_init__void__ret_void";

    private record FunctionBody(String label, List<IRInstruction> instructions) {
    }

    public IRProgram optimize(IRProgram input, ProgramRecord sourceProgram) {
        Map<String, FunctionBody> functions = splitFunctions(input.instructions());
        String entry = findEntry(functions.keySet());
        if (entry == null || !functions.containsKey(STATIC_INITIALIZER)) return input;

        VirtualMethodTable virtualTable = new VirtualMethodTable(sourceProgram);
        Map<String, List<String>> virtualTables = virtualTable.tables();
        Map<String, String> bridges = virtualTable.bridgeTargets();

        Set<String> reachable = new LinkedHashSet<>();
        ArrayDeque<String> work = new ArrayDeque<>();
        work.add(STATIC_INITIALIZER);
        work.add(entry);

        while (!work.isEmpty()) {
            String label = work.removeFirst();
            if (!reachable.add(label)) continue;
            FunctionBody body = functions.get(label);
            if (body == null) continue;
            for (IRInstruction instruction : body.instructions()) {
                if (instruction.opCode() == IROpCode.CALL) {
                    enqueueLabel(instruction.left(), functions, work);
                } else if (instruction.opCode() == IROpCode.CALL_VIRTUAL
                        && instruction.right() != null
                        && instruction.right().value() instanceof Integer slot
                        && slot >= 0) {
                    for (List<String> table : virtualTables.values()) {
                        if (slot >= table.size()) continue;
                        String target = table.get(slot);
                        if (target == null || target.equals("0")) continue;
                        String bridgeTarget = bridges.get(target);
                        enqueueLabel(Operand.label(bridgeTarget == null ? target : bridgeTarget),
                                functions, work);
                    }
                }
            }
        }

        IRProgram result = new IRProgram();
        boolean keepCurrentFunction = true;
        for (IRInstruction instruction : input.instructions()) {
            if (isFunctionLabel(instruction)) {
                keepCurrentFunction = reachable.contains(instruction.destination().value().toString());
            }
            if (keepCurrentFunction) result.add(instruction);
        }
        return result;
    }

    private Map<String, FunctionBody> splitFunctions(List<IRInstruction> instructions) {
        Map<String, FunctionBody> functions = new HashMap<>();
        String currentLabel = null;
        List<IRInstruction> current = null;
        for (IRInstruction instruction : instructions) {
            if (isFunctionLabel(instruction)) {
                if (currentLabel != null) {
                    functions.put(currentLabel, new FunctionBody(currentLabel, List.copyOf(current)));
                }
                currentLabel = instruction.destination().value().toString();
                current = new ArrayList<>();
            }
            if (current != null) current.add(instruction);
        }
        if (currentLabel != null) {
            functions.put(currentLabel, new FunctionBody(currentLabel, List.copyOf(current)));
        }
        return functions;
    }

    private String findEntry(Set<String> labels) {
        String withArguments = null;
        String withoutArguments = null;
        for (String label : labels) {
            if (label.endsWith(".main__String_array__ret_void")) withArguments = label;
            else if (label.endsWith(".main__void__ret_void")) withoutArguments = label;
        }
        return withArguments != null ? withArguments : withoutArguments;
    }

    private void enqueueLabel(
            Operand labelOperand,
            Map<String, FunctionBody> functions,
            ArrayDeque<String> work) {
        if (labelOperand == null || labelOperand.kind() != Operand.Kind.LABEL) return;
        String label = labelOperand.value().toString();
        if (functions.containsKey(label)) work.addLast(label);
    }

    private boolean isFunctionLabel(IRInstruction instruction) {
        return instruction.opCode() == IROpCode.LABEL
                && instruction.destination() != null
                && instruction.destination().kind() == Operand.Kind.LABEL
                && instruction.destination().value().toString().contains(".");
    }
}
