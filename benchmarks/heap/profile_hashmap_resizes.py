#!/usr/bin/env python3
"""Measure one HashMap.put at each known resize threshold and a neighboring control put."""

import argparse
import csv
import subprocess
import sys
import time


def measure(executable, existing_entries):
    process = subprocess.Popen(
        [executable, str(existing_entries)],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1,
    )
    try:
        ready = process.stdout.readline().strip()
        if ready != "READY":
            stderr = process.stderr.read()
            raise RuntimeError(f"Program did not reach READY (got {ready!r}): {stderr}")

        start = time.perf_counter_ns()
        process.stdin.write("start\n")
        process.stdin.flush()
        done = process.stdout.readline().strip()
        elapsed = time.perf_counter_ns() - start
        if done != "DONE":
            stderr = process.stderr.read()
            raise RuntimeError(f"Program did not reach DONE (got {done!r}): {stderr}")

        process.stdin.write("finish\n")
        process.stdin.flush()
        result = process.stdout.readline().strip()
        return_code = process.wait()
        stderr = process.stderr.read()
        expected_size = existing_entries + 1
        if return_code != 0 or result != f"RESULT {expected_size}":
            raise RuntimeError(
                f"Unexpected benchmark result for existing={existing_entries}: "
                f"exit={return_code}, result={result!r}, stderr={stderr}"
            )
        return elapsed
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("executable", help="compiled HashMapResizeBench executable")
    parser.add_argument("--max-inserts", type=int, default=1_000_000)
    parser.add_argument("--repeats", type=int, default=3)
    args = parser.parse_args()
    if args.max_inserts <= 0 or args.repeats <= 0:
        parser.error("--max-inserts and --repeats must be positive")

    thresholds = []
    threshold = 12  # Initial capacity 16 with the HashMap's 75% load threshold.
    while threshold <= args.max_inserts:
        thresholds.append(threshold)
        threshold *= 2

    writer = csv.writer(sys.stdout)
    writer.writerow(("threshold", "capacity_after_resize", "existing_entries", "kind", "repeat", "elapsed_ns", "elapsed_ms"))
    for threshold in thresholds:
        capacity = (threshold * 2 * 4) // 3
        for repeat in range(1, args.repeats + 1):
            order = (("control", threshold - 1), ("resize", threshold))
            if repeat % 2 == 0:
                order = tuple(reversed(order))
            for kind, existing_entries in order:
                elapsed = measure(args.executable, existing_entries)
                writer.writerow((threshold, capacity if kind == "resize" else "", existing_entries,
                                 kind, repeat, elapsed, f"{elapsed / 1_000_000:.3f}"))
                sys.stdout.flush()


if __name__ == "__main__":
    main()
