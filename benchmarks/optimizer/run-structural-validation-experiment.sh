#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
mode="${1:-time}"
if [[ "$mode" != time && "$mode" != profile ]]; then
  echo "Uso: $0 [time|profile] [tamanho ...]" >&2
  exit 2
fi
shift || true
sizes=("$@")
if ((${#sizes[@]} == 0)); then sizes=(250 500); fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/src/compiler/backend/optimization" "$tmp/classes"
profile_kind="$mode"
candidate="${BENCH_CANDIDATE:-clone}"
if [[ "$candidate" == production ]]; then
  if [[ "$mode" != time ]]; then
    echo "profile mode requires temporary instrumented clones" >&2
    exit 2
  fi
  python3 "$root/benchmarks/optimizer/prepare_full_key_clone.py" \
    "$root/benchmarks/optimizer/reference/IROptimizer.pre-generation.java" \
    "$tmp/src/compiler/backend/optimization/FullKeyIROptimizer.java" time
  candidate_class=compiler.backend.optimization.IROptimizer
else
  python3 "$root/benchmarks/optimizer/prepare_full_key_clone.py" \
    "$root/benchmarks/optimizer/reference/IROptimizer.pre-generation.java" \
    "$tmp/src/compiler/backend/optimization/FullKeyIROptimizer.java" "$profile_kind"
  python3 "$root/benchmarks/optimizer/prepare_generation_clone.py" \
    "$root/benchmarks/optimizer/reference/IROptimizer.pre-generation.java" \
    "$tmp/src/compiler/backend/optimization/GenerationIROptimizer.java" "$profile_kind"
  candidate_class=compiler.backend.optimization.GenerationIROptimizer
fi
find "$root/compiler" -type f -name '*.java' -print0 \
  | xargs -0 javac -encoding UTF-8 --release 17 -d "$tmp/classes"
javac -encoding UTF-8 --release 17 -cp "$tmp/classes" -d "$tmp/classes" \
  "$tmp/src/compiler/backend/optimization/FullKeyIROptimizer.java" \
  "$root/benchmarks/optimizer/IROptimizerBenchmark.java" \
  "$root/benchmarks/optimizer/StructuralValidationHarness.java"
if [[ "$candidate" != production ]]; then
  javac -encoding UTF-8 --release 17 -cp "$tmp/classes" -d "$tmp/classes" \
    "$tmp/src/compiler/backend/optimization/GenerationIROptimizer.java"
fi
read -r -a java_opts <<< "${JAVA_OPTS:-}"
java_opts+=("-Dbenchmark.fullClass=compiler.backend.optimization.FullKeyIROptimizer"
            "-Dbenchmark.generationClass=$candidate_class")
java "${java_opts[@]}" -cp "$tmp/classes" benchmark.StructuralValidationHarness "$mode" "${sizes[@]}"
