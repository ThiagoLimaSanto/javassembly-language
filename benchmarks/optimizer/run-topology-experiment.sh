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
if [[ "$mode" == profile ]]; then
  python3 "$root/benchmarks/optimizer/prepare_reference_profile.py" \
    "$root/compiler/backend/optimization/IROptimizer.java" \
    "$tmp/src/compiler/backend/optimization/ReferenceProfileOptimizer.java"
  profile_kind=profile
else
  profile_kind=time
fi
python3 "$root/benchmarks/optimizer/prepare_topology_clone.py" \
  "$root/compiler/backend/optimization/IROptimizer.java" \
  "$tmp/src/compiler/backend/optimization/ExperimentIROptimizer.java" "$profile_kind"
find "$root/compiler" -type f -name '*.java' -print0 \
  | xargs -0 javac -encoding UTF-8 --release 17 -d "$tmp/classes"
extra_sources=("$root/benchmarks/optimizer/IROptimizerBenchmark.java"
               "$root/benchmarks/optimizer/TopologyHarness.java"
               "$tmp/src/compiler/backend/optimization/ExperimentIROptimizer.java")
if [[ "$mode" == profile ]]; then
  extra_sources+=("$tmp/src/compiler/backend/optimization/ReferenceProfileOptimizer.java")
fi
javac -encoding UTF-8 --release 17 -cp "$tmp/classes" -d "$tmp/classes" "${extra_sources[@]}"
read -r -a java_opts <<< "${JAVA_OPTS:-}"
java "${java_opts[@]}" -cp "$tmp/classes" benchmark.TopologyHarness "$mode" "${sizes[@]}"
