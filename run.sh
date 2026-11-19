#!/usr/bin/env bash
set -euo pipefail

source_file="${1:-src/source/backend/ir_smoke_test.jvs}"
source_base="${source_file%.jvs}"
program_name="$(basename "$source_base")"
asm_file="${source_base}.asm"
object_dir="out/objectSource"
executable_dir="out/executable"

mkdir -p out "$object_dir" "$executable_dir"
mapfile -t java_sources < <(find src -name '*.java' -print)

javac -d out "${java_sources[@]}"
java -cp out src.Main "$source_file"
nasm -f elf64 "$asm_file" -o "$object_dir/$program_name.o"
ld -o "$executable_dir/$program_name" "$object_dir/$program_name.o"
"$executable_dir/$program_name"
