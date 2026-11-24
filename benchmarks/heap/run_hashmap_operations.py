#!/usr/bin/env python3
"""Measure isolated HashMap operation loops in a prebuilt JVS executable."""

import argparse
import csv
import subprocess
import sys
import time


OPERATIONS = ((1, "insert"), (2, "lookup"), (3, "remove"))


def run_one(executable, size, operation):
    process = subprocess.Popen(
        [executable, str(size), str(operation)],
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
        end_marker = process.stdout.readline().strip()
        elapsed = time.perf_counter_ns() - start
        if end_marker != "DONE":
            stderr = process.stderr.read()
            raise RuntimeError(f"Program did not reach DONE (got {end_marker!r}): {stderr}")

        process.stdin.write("finish\n")
        process.stdin.flush()
        result_line = process.stdout.readline().strip()
        return_code = process.wait()
        stderr = process.stderr.read()
        if return_code != 0:
            raise RuntimeError(f"Program exited with {return_code}: {stderr}")
        if not result_line.startswith("RESULT "):
            raise RuntimeError(f"Unexpected result line {result_line!r}: {stderr}")
        result = result_line.removeprefix("RESULT ")
        expected_size = 0 if operation == 3 else size
        expected_observed = 0 if operation == 1 else size - 1
        if result != f"{expected_size} {expected_observed}":
            raise RuntimeError(
                f"Incorrect {operation=} result: expected "
                f"{expected_size} {expected_observed}, got {result}"
            )
        return elapsed, result
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("executable", help="path to the compiled HashMapOperationBench executable")
    parser.add_argument("--size", type=int, default=1_000_000)
    parser.add_argument("--repeats", type=int, default=3)
    args = parser.parse_args()
    if args.size <= 0 or args.repeats <= 0:
        parser.error("--size and --repeats must be positive")

    writer = csv.writer(sys.stdout)
    writer.writerow(("size", "operation", "repeat", "elapsed_ns", "elapsed_ms", "result"))
    for operation, name in OPERATIONS:
        for repeat in range(1, args.repeats + 1):
            elapsed, result = run_one(args.executable, args.size, operation)
            writer.writerow((args.size, name, repeat, elapsed, f"{elapsed / 1_000_000:.3f}", result))
            sys.stdout.flush()


if __name__ == "__main__":
    main()
