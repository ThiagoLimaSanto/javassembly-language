#!/usr/bin/env bash
set -euo pipefail

source_file="${1:-projects/exemplo/src/Teste.jvs}"
source_path="$(realpath "$source_file")"
project_source_dir="$(dirname "$source_path")"
while [[ "$(basename "$project_source_dir")" != "src" && "$project_source_dir" != "/" ]]; do
    project_source_dir="$(dirname "$project_source_dir")"
done
if [[ "$(basename "$project_source_dir")" != "src" ]]; then
    printf 'Erro: o arquivo-fonte deve estar dentro de projects/nomeProjeto/src.\n' >&2
    exit 1
fi
project_dir="$(dirname "$project_source_dir")"
program_name="$(basename "${source_file%.jvs}")"
project_out="$project_dir/out"
compiler_classes="build/compiler"
asm_file="$project_out/$program_name.asm"
object_file="$project_out/$program_name.o"
executable="$project_out/$program_name"

mkdir -p "$compiler_classes" "$project_out"
mapfile -t java_sources < <(find src -name '*.java' -print)

javac -d "$compiler_classes" "${java_sources[@]}"
java -cp "$compiler_classes" src.Main "$source_path"
nasm -f elf64 "$asm_file" -o "$object_file"
ld -o "$executable" "$object_file"
"$executable"
