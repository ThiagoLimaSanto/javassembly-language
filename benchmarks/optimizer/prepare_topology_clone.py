#!/usr/bin/env python3
"""Create a temporary benchmark-only optimizer clone with reusable CFG topology."""
from pathlib import Path
import re
import sys

source = Path(sys.argv[1]).read_text()
target = Path(sys.argv[2])
profile = len(sys.argv) > 3 and sys.argv[3] == "profile"
source = source.replace("public class IROptimizer {", "public class ExperimentIROptimizer {", 1)
source = source.replace(
    "public class ExperimentIROptimizer {",
    """public class ExperimentIROptimizer {
    private String experimentTopologyKey;
    private List<BasicBlock> experimentTopology;
    private int experimentBuilds;
    private int experimentReuses;
    private long profileKeyNanos, profileCompareNanos, profileRefreshNanos, profileBuildNanos;
    private long profileInitialBuildNanos, profileRebuildNanos;
    private int profileCfgCalls, profileInitialBuildCalls, profileRebuildCalls;
    private int profileKeyChecks, profileCompareChecks;
    private int profileRefreshCalls;
    private long profileRefreshBlocks, profileRefreshInstructions;
    private int profileRounds;
    private final java.util.Map<String, Long> profilePassNanos = new java.util.LinkedHashMap<>();
    private IRProgram profilePass(String name, IRProgram input, java.util.function.Function<IRProgram, IRProgram> pass) {
        long start = System.nanoTime();
        try { return pass.apply(input); }
        finally { profilePassNanos.merge(name, System.nanoTime() - start, Long::sum); }
    }
""",
    1,
)
if profile:
    optimize_start = source.index("    public IRProgram optimize(IRProgram input) {")
    optimize_end = source.index("\n    private List<BasicBlock> buildControlFlowGraph", optimize_start)
    optimize = source[optimize_start:optimize_end]
    optimize = optimize.replace("        for (int round = 0; round < MAX_OPTIMIZATION_ROUNDS; round++) {",
                                "        for (int round = 0; round < MAX_OPTIMIZATION_ROUNDS; round++) {\n            profileRounds++;")
    for pass_name in ["removeUnreachable", "propagateConstants", "propagateStaticConstants",
                      "simplifyConstantBranches", "removeRedundantJumps", "propagateCopies",
                      "simplifyIntegerIdentity", "eliminateCommonSubexpressions",
                      "eliminateDeadStores", "eliminateDeadTemporaries"]:
        optimize = optimize.replace(f"= {pass_name}(current);",
                                    f"= profilePass(\"{pass_name}\", current, this::{pass_name});")
        optimize = optimize.replace(f"= {pass_name}(next);",
                                    f"= profilePass(\"{pass_name}\", next, this::{pass_name});")
    source = source[:optimize_start] + optimize + source[optimize_end:]
start = source.index("    private List<BasicBlock> buildControlFlowGraph(IRProgram program) {")
end = source.index("    private void addJumpEdge(", start)
method = r'''    private List<BasicBlock> buildControlFlowGraph(IRProgram program) {
        List<IRInstruction> instructions = program.instructions();
        StringBuilder keyBuilder = new StringBuilder(instructions.size() * 4);
        for (int index = 0; index < instructions.size(); index++) {
            IRInstruction instruction = instructions.get(index);
            boolean boundary = instruction.opCode() == IROpCode.LABEL
                    || (index > 0 && isTerminator(instructions.get(index - 1).opCode()));
            if (boundary || isTerminator(instruction.opCode())
                    || instruction.opCode() == IROpCode.PUSH_EXCEPTION_HANDLER) {
                keyBuilder.append(index).append(':').append(instruction.opCode()).append(':');
                if (instruction.destination() != null) keyBuilder.append(instruction.destination().value());
                keyBuilder.append(';');
            }
        }
        String key = keyBuilder.toString();
        if (experimentTopology != null && key.equals(experimentTopologyKey)) {
            for (BasicBlock block : experimentTopology) {
                block.instructions.clear();
                block.inConstants = null;
                block.outConstants = null;
            }
            int blockIndex = -1;
            BasicBlock current = null;
            for (IRInstruction instruction : instructions) {
                boolean startsBlock = instruction.opCode() == IROpCode.LABEL
                        || (current != null && isTerminator(current.last().opCode()));
                if (current == null || (startsBlock && !current.instructions.isEmpty())) {
                    current = experimentTopology.get(++blockIndex);
                }
                current.instructions.add(instruction);
            }
            experimentReuses++;
            return experimentTopology;
        }

        List<BasicBlock> blocks = new ArrayList<>();
        BasicBlock current = null;
        for (IRInstruction instruction : instructions) {
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
            if (first.opCode() == IROpCode.LABEL && first.destination() != null)
                labels.put(first.destination().value().toString(), block);
        }
        for (int index = 0; index < blocks.size(); index++) {
            BasicBlock block = blocks.get(index);
            for (IRInstruction instruction : block.instructions) {
                if (instruction.opCode() == IROpCode.PUSH_EXCEPTION_HANDLER)
                    addJumpEdge(block, labels, instruction.destination());
            }
            IRInstruction last = block.last();
            switch (last.opCode()) {
                case JUMP -> addJumpEdge(block, labels, last.destination());
                case JUMP_IF_FALSE, JUMP_IF_TRUE -> {
                    addJumpEdge(block, labels, last.destination());
                    addFallthroughEdge(blocks, index, block);
                }
                case THROW -> { if (last.destination() != null) addJumpEdge(block, labels, last.destination()); }
                case CHECK_EXCEPTION -> {
                    if (last.destination() != null) addJumpEdge(block, labels, last.destination());
                    addFallthroughEdge(blocks, index, block);
                }
                case RETURN -> { }
                default -> addFallthroughEdge(blocks, index, block);
            }
        }
        experimentTopologyKey = key;
        experimentTopology = blocks;
        experimentBuilds++;
        return blocks;
    }

'''
method = re.sub(r"(?m)^\+", "", method)
if profile:
    method = method.replace(
        "        StringBuilder keyBuilder = new StringBuilder(instructions.size() * 4);",
        "        long profileStart = System.nanoTime();\n        profileKeyChecks++;\n"
        "        StringBuilder keyBuilder = new StringBuilder(instructions.size() * 4);", 1)
    method = method.replace(
        "        String key = keyBuilder.toString();\n        if (experimentTopology != null && key.equals(experimentTopologyKey)) {",
        "        String key = keyBuilder.toString();\n"
        "        profileKeyNanos += System.nanoTime() - profileStart;\n        profileStart = System.nanoTime();\n"
        "        profileCompareChecks++;\n"
        "        boolean topologyHit = experimentTopology != null && key.equals(experimentTopologyKey);\n"
        "        profileCompareNanos += System.nanoTime() - profileStart;\n        if (topologyHit) {", 1)
    method = method.replace(
        "            for (BasicBlock block : experimentTopology) {",
        "            profileRefreshCalls++;\n"
        "            profileRefreshBlocks += experimentTopology.size();\n"
        "            profileRefreshInstructions += instructions.size();\n"
        "            profileStart = System.nanoTime();\n            for (BasicBlock block : experimentTopology) {", 1)
    method = method.replace(
        "            experimentReuses++;\n            return experimentTopology;",
        "            if (blockIndex + 1 != experimentTopology.size())\n"
        "                throw new IllegalStateException(\"Cached CFG block count mismatch\");\n"
        "            profileRefreshNanos += System.nanoTime() - profileStart;\n"
        "            experimentReuses++;\n            return experimentTopology;", 1)
    method = method.replace(
        "        List<BasicBlock> blocks = new ArrayList<>();",
        "        boolean initialTopologyBuild = experimentTopology == null;\n"
        "        profileStart = System.nanoTime();\n        profileCfgCalls++;\n"
        "        List<BasicBlock> blocks = new ArrayList<>();", 1)
    method = method.replace(
        "        experimentTopologyKey = key;",
        "        long topologyBuildElapsed = System.nanoTime() - profileStart;\n"
        "        profileBuildNanos += topologyBuildElapsed;\n"
        "        if (initialTopologyBuild) { profileInitialBuildCalls++; profileInitialBuildNanos += topologyBuildElapsed; }\n"
        "        else { profileRebuildCalls++; profileRebuildNanos += topologyBuildElapsed; }\n"
        "        experimentTopologyKey = key;", 1)
else:
    method = method.replace("            experimentReuses++;\n", "")
    method = method.replace("        experimentBuilds++;\n", "")
source = source[:start] + method + source[end:]
if not profile:
    source = source.replace("    private int experimentBuilds;\n    private int experimentReuses;\n", "")
    source = re.sub(r"    private long profileKeyNanos.*?\n    }\n", "", source, count=1, flags=re.S)
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(source)
