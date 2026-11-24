#!/usr/bin/env python3
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text().replace(
    "public class IROptimizer {", "public class ReferenceProfileOptimizer {", 1)
source = source.replace(
    "public class ReferenceProfileOptimizer {",
    "public class ReferenceProfileOptimizer {\n"
    "    private int profileCfgCalls;\n    private long profileCfgNanos;\n"
    "    private int profileRounds;\n"
    "    private final java.util.Map<String, Long> profilePassNanos = new java.util.LinkedHashMap<>();\n"
    "    private IRProgram profilePass(String name, IRProgram input, java.util.function.Function<IRProgram, IRProgram> pass) {\n"
    "        long start = System.nanoTime();\n"
    "        try { return pass.apply(input); }\n"
    "        finally { profilePassNanos.merge(name, System.nanoTime() - start, Long::sum); }\n"
    "    }\n", 1)

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
method = source[start:end]
method = method.replace(
    "    private List<BasicBlock> buildControlFlowGraph(IRProgram program) {",
    "    private List<BasicBlock> buildControlFlowGraph(IRProgram program) {\n"
    "        long profileStart = System.nanoTime();\n        profileCfgCalls++;", 1)
method = method.replace("        return blocks;", "        profileCfgNanos += System.nanoTime() - profileStart;\n        return blocks;", 1)
source = source[:start] + method + source[end:]
target = Path(sys.argv[2]); target.parent.mkdir(parents=True, exist_ok=True); target.write_text(source)
