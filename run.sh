#!/usr/bin/env bash
set -euo pipefail

source_file="${1:-src/source/backend/ir_smoke_test.jvs}"
source_base="${source_file%.jvs}"
program_name="$(basename "$source_base")"
asm_file="${source_base}.asm"

mkdir -p out
mapfile -t java_sources < <(find src -name '*.java' -print)

javac -d out "${java_sources[@]}"
java -cp out src.Main "$source_file"
nasm -f elf64 "$asm_file" -o "out/$program_name.o"
gcc -no-pie "out/$program_name.o" -o "out/$program_name"
"out/$program_name"