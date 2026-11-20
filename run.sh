#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source_file="${1:-projects/exemplo/src/Teste.jvs}"
"$script_dir/jvsc" "$source_file"
exec "$script_dir/jvs" "$source_file"
