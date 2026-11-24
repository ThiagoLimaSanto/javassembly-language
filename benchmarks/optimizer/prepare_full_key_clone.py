#!/usr/bin/env python3
"""Create a benchmark-only, optionally instrumented full-key optimizer clone."""
from pathlib import Path
import subprocess
import sys

source = Path(sys.argv[1])
target = Path(sys.argv[2])
profile = len(sys.argv) > 3 and sys.argv[3] == "profile"
intermediate = target.with_name("ExperimentIROptimizer.java")
subprocess.run([
    sys.executable,
    str(source.parents[3] / "benchmarks/optimizer/prepare_topology_clone.py"),
    str(source), str(intermediate), "profile" if profile else "time",
], check=True)
text = intermediate.read_text().replace("ExperimentIROptimizer", "FullKeyIROptimizer")
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(text)
intermediate.unlink(missing_ok=True)
