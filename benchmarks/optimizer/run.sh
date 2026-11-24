#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/classes"
find "$root/compiler" -type f -name '*.java' -print0 \
  | xargs -0 javac -encoding UTF-8 --release 17 -d "$tmp/classes"
javac -encoding UTF-8 --release 17 -cp "$tmp/classes" -d "$tmp/classes" \
  "$root/benchmarks/optimizer/IROptimizerBenchmark.java"
java ${JAVA_OPTS:-} -cp "$tmp/classes" benchmark.IROptimizerBenchmark "$@"
