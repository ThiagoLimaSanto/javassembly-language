#!/usr/bin/env python3
"""Measure isolated phases of the current HashMap rehash shape."""

import argparse
import csv
import subprocess
import sys
import time


PHASES = (
    ("ALLOC_DONE", "allocate_four_arrays"),
    ("SCAN_DONE", "scan_old_states"),
    ("REINSERT_DONE", "scan_and_reinsert_entries"),
    ("VERIFY_DONE", "verify_reinserted_entries"),
    ("FREE_DONE", "free_four_old_arrays"),
)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("executable", help="compiled HashMapRehashComponentsBench executable")
    parser.add_argument("--old-capacity", type=int, default=1_048_576)
    parser.add_argument("--active-count", type=int, default=786_432)
    parser.add_argument("--repeats", type=int, default=3)
    args = parser.parse_args()
    if args.old_capacity <= 0 or args.active_count < 0 or args.active_count > args.old_capacity:
        parser.error("require 0 <= --active-count <= --old-capacity and old capacity > 0")
    if args.repeats <= 0:
        parser.error("--repeats must be positive")

    writer = csv.writer(sys.stdout)
    writer.writerow(("old_capacity", "new_capacity", "active_entries", "repeat", "phase",
                     "elapsed_ns", "elapsed_ms", "timing_scope"))
    for repeat in range(1, args.repeats + 1):
        process = subprocess.Popen(
            [args.executable, str(args.old_capacity), str(args.active_count)],
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

            for marker, name in PHASES:
                start = time.perf_counter_ns()
                process.stdin.write("continue\n")
                process.stdin.flush()
                observed = process.stdout.readline().strip()
                elapsed = time.perf_counter_ns() - start
                if observed != marker:
                    stderr = process.stderr.read()
                    raise RuntimeError(f"Expected {marker}, got {observed!r}: {stderr}")
                writer.writerow((args.old_capacity, args.old_capacity * 2, args.active_count,
                                 repeat, name, elapsed, f"{elapsed / 1_000_000:.3f}",
                                 "phase plus scanner barrier and marker write"))
                sys.stdout.flush()

            process.stdin.write("finish\n")
            process.stdin.flush()
            result = process.stdout.readline().strip()
            return_code = process.wait()
            stderr = process.stderr.read()
            expected = (f"RESULT {args.active_count} {args.active_count} "
                        f"{args.active_count}")
            if return_code != 0 or result != expected:
                raise RuntimeError(
                    f"Unexpected rehash result: expected {expected!r}, got {result!r}; "
                    f"exit={return_code}; stderr={stderr}"
                )
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()


if __name__ == "__main__":
    main()
