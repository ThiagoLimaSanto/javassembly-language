#!/usr/bin/env bash
set -euo pipefail

source_file="${1:-projects/exemplo/Teste.jvs}"
source_path="$(realpath "$source_file")"
project_dir="$(dirname "$source_path")"
program_name="$(basename "${source_file%.jvs}")"
project_out="$project_dir/out"
asm_file="$project_out/$program_name.asm"
object_file="$project_out/$program_name.o"
executable="$project_out/$program_name"

mkdir -p out "$project_out"
mapfile -t java_sources < <(find src -name '*.java' -print)

javac -d out "${java_sources[@]}"
java -cp out src.Main "$source_path"
nasm -f elf64 "$asm_file" -o "$object_file"
ld -o "$executable" "$object_file"
"$executable"
