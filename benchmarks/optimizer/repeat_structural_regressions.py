#!/usr/bin/env python3
"""Run isolated, alternating time comparisons for loops/copies regressions."""
import argparse
import csv
import io
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "benchmarks/optimizer/run-structural-validation-experiment.sh"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--samples", type=int, default=31)
    parser.add_argument("--heap", default="3g", help="equal -Xms/-Xmx size for each JVM")
    parser.add_argument("--output", type=Path,
                        default=ROOT / "benchmarks/optimizer/structural-regressions-isolated.csv")
    args = parser.parse_args()
    if args.repeats < 1 or args.samples < 1:
        parser.error("--repeats and --samples must be positive")

    output_rows = []
    header = None
    for scenario in ("loops", "copies"):
        for size in (250, 500):
            for repeat in range(1, args.repeats + 1):
                env = os.environ.copy()
                env["BENCH_SCENARIOS"] = scenario
                env["BENCH_SAMPLES"] = str(args.samples)
                env["JAVA_OPTS"] = f"-Xms{args.heap} -Xmx{args.heap}"
                process = subprocess.run(
                    [str(RUNNER), "time", str(size)],
                    cwd=ROOT,
                    env=env,
                    text=True,
                    capture_output=True,
                    check=True,
                )
                rows = list(csv.DictReader(io.StringIO(process.stdout)))
                if len(rows) != 1 or rows[0]["scenario"] != scenario or rows[0]["size"] != str(size):
                    raise RuntimeError(f"Unexpected benchmark output for {scenario}/{size}")
                row = rows[0]
                if row["final_ir_equal"] != "true":
                    raise AssertionError(f"IR mismatch in {scenario}/{size}, repeat {repeat}")
                if header is None:
                    header = ["replicate", *row.keys()]
                output_rows.append([str(repeat), *(row[key] for key in row)])

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(header)
        writer.writerows(output_rows)
    print(f"Wrote {len(output_rows)} rows to {args.output}")


if __name__ == "__main__":
    main()
