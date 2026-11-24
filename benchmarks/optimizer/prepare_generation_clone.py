#!/usr/bin/env python3
"""Create an isolated optimizer clone using explicit topology generations."""
from pathlib import Path
import re
import subprocess
import sys

source_path = Path(sys.argv[1])
target_path = Path(sys.argv[2])
profile = len(sys.argv) > 3 and sys.argv[3] == "profile"
intermediate = target_path.with_name("GenerationBaseIROptimizer.java")
subprocess.run([
    sys.executable,
    str(source_path.parents[3] / "benchmarks/optimizer/prepare_topology_clone.py"),
    str(source_path), str(intermediate), "profile" if profile else "time",
], check=True)

source = intermediate.read_text()
source = source.replace("ExperimentIROptimizer", "GenerationIROptimizer")
source = source.replace(
    "    private String experimentTopologyKey;\n",
    "    private String experimentTopologyKey;\n"
    "    private long experimentGeneration;\n"
    "    private long experimentBuiltGeneration = -1;\n"
    "    private int experimentInvalidations;\n", 1)
if profile:
    source = source.replace(
        "    private int profileRounds;\n",
        "    private int profileRounds;\n"
        "    private long profileGenerationCheckNanos, profileInvalidationNanos;\n"
        "    private int profileGenerationChecks;\n", 1)

method_start = source.index("    private List<BasicBlock> buildControlFlowGraph(IRProgram program) {")
method_end = source.index("    private void addJumpEdge(", method_start)
method = source[method_start:method_end]
check_start = method.index("        List<IRInstruction> instructions = program.instructions();")
if profile:
    check_end = method.index("        if (topologyHit) {", check_start) + len("        if (topologyHit) {")
    new_check = """        List<IRInstruction> instructions = program.instructions();
        long profileStart = System.nanoTime();
        boolean topologyHit = experimentTopology != null
                && experimentBuiltGeneration == experimentGeneration;
        profileGenerationChecks++;
        profileGenerationCheckNanos += System.nanoTime() - profileStart;
        if (topologyHit) {""".replace("\n+", "\n")
else:
    check_end = method.index("        if (experimentTopology != null && key.equals(experimentTopologyKey)) {", check_start) + len("        if (experimentTopology != null && key.equals(experimentTopologyKey)) {")
    new_check = """        List<IRInstruction> instructions = program.instructions();
        boolean topologyHit = experimentTopology != null
                && experimentBuiltGeneration == experimentGeneration;
        if (topologyHit) {""".replace("\n+", "\n")
method = method[:check_start] + new_check + method[check_end:]
method = method.replace("        experimentTopologyKey = key;\n", "", 1)
method = method.replace(
    "        experimentTopology = blocks;\n",
    "        experimentTopology = blocks;\n"
    "        experimentBuiltGeneration = experimentGeneration;\n", 1)
source = source[:method_start] + method + source[method_end:]

if profile:
    invalidate = """    private void experimentInvalidateTopology() {
        long start = System.nanoTime();
        experimentGeneration++;
        experimentInvalidations++;
        profileInvalidationNanos += System.nanoTime() - start;
    }

"""
else:
    invalidate = """    private void experimentInvalidateTopology() {
        experimentGeneration++;
        experimentInvalidations++;
    }

"""
anchor = "    private void addJumpEdge("
source = source.replace(anchor, invalidate + anchor, 1)

# Structural invalidations are attached only to transformations which can alter
# labels, block boundaries, terminators, or handler/control-flow destinations.
remove_start = source.index("    private IRProgram removeUnreachable(IRProgram input) {")
remove_end = source.index("\n    private IRProgram propagateConstants", remove_start)
part = source[remove_start:remove_end]
part = part.replace("        IRProgram output = new IRProgram();\n        for (BasicBlock block : blocks) {",
                    "        IRProgram output = new IRProgram();\n        boolean topologyChanged = false;\n        for (BasicBlock block : blocks) {", 1)
part = part.replace("            if (reachable.contains(block)) {\n                block.instructions.forEach(output::add);\n            }",
                    "            if (reachable.contains(block)) {\n                block.instructions.forEach(output::add);\n            } else {\n                topologyChanged = true;\n            }", 1)
part = part.replace("        return output;", "        if (topologyChanged) experimentInvalidateTopology();\n        return output;", 1)
source = source[:remove_start] + part + source[remove_end:]

branch_start = source.index("    private IRProgram simplifyConstantBranches(IRProgram input) {")
branch_end = source.index("\n    private IRProgram removeRedundantJumps", branch_start)
part = source[branch_start:branch_end]
part = part.replace("        IRProgram output = new IRProgram();", "        IRProgram output = new IRProgram();\n        boolean topologyChanged = false;", 1)
part = part.replace("            boolean value = (Boolean) condition.value();", "            topologyChanged = true;\n            boolean value = (Boolean) condition.value();", 1)
part = part.replace("        return output;", "        if (topologyChanged) experimentInvalidateTopology();\n        return output;", 1)
source = source[:branch_start] + part + source[branch_end:]

jump_start = source.index("    private IRProgram removeRedundantJumps(IRProgram input) {")
jump_end = source.index("\n    private IRProgram propagateCopies", jump_start)
part = source[jump_start:jump_end]
part = part.replace("        IRProgram output = new IRProgram();", "        IRProgram output = new IRProgram();\n        boolean topologyChanged = false;", 1)
part = part.replace("                        instruction = null;", "                        instruction = null;\n                        topologyChanged = true;", 1)
part = part.replace("        return output;", "        if (topologyChanged) experimentInvalidateTopology();\n        return output;", 1)
source = source[:jump_start] + part + source[jump_end:]

if profile:
    source = source.replace(
        "    private long profileKeyNanos, profileCompareNanos, profileRefreshNanos, profileBuildNanos;",
        "    private long profileKeyNanos, profileCompareNanos, profileRefreshNanos, profileBuildNanos;", 1)
else:
    # Keep generation state but remove all profile-only instrumentation/helpers.
    source = re.sub(r"    private long profileKeyNanos.*?\n    }\n", "", source, count=1, flags=re.S)
    source = source.replace("    private int experimentInvalidations;\n", "")
    source = source.replace("        experimentInvalidations++;\n", "")

# Each optimizer invocation gets a fresh topology in both variants; reuse is
# measured only among the CFG requests inside the exact optimize() cycle.
optimize_start = source.index("    public IRProgram optimize(IRProgram input) {")
body_start = source.index("\n", optimize_start) + 1
source = source[:body_start] + "        experimentTopology = null;\n" + source[body_start:]

target_path.parent.mkdir(parents=True, exist_ok=True)
target_path.write_text(source)
intermediate.unlink(missing_ok=True)
