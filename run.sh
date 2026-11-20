#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
target="-l"
source_file="projects/exemplo/src/Teste.jvs"

if (($# == 1)); then
    source_file="$1"
elif (($# == 2)); then
    target="$1"
    source_file="$2"
elif (($# > 2)); then
    printf 'Uso: run.sh [-l|-w] [arquivo.jvs]\n' >&2
    exit 2
fi

"$script_dir/jvsc" "$target" "$source_file"
exec "$script_dir/jvs" "$target" "$source_file"
