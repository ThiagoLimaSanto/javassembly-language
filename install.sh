#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

compiler_jar="$repo_root/javassembly.jar"
if [[ ! -f "$compiler_jar" ]]; then
    if [[ ! -f "$repo_root/compiler/Main.java" ]] || ! command -v javac >/dev/null 2>&1 || ! command -v jar >/dev/null 2>&1; then
        printf 'Erro: javassembly.jar não está no pacote. Baixe uma Release ou instale um JDK para compilar do código-fonte.\n' >&2
        exit 1
    fi
    build_dir="$repo_root/build/jar-install"
    mkdir -p "$build_dir/classes"
    javac -d "$build_dir/classes" -sourcepath "$repo_root" "$repo_root/compiler/Main.java"
    jar --create --file "$build_dir/javassembly.jar" --main-class compiler.Main -C "$build_dir/classes" .
    compiler_jar="$build_dir/javassembly.jar"
fi

if [[ "${1:-}" == "--system" ]]; then
    if ((EUID != 0)); then
        printf 'Instalação global requer privilégios administrativos. Use: sudo bash install.sh --system\n' >&2
        exit 1
    fi

    install_root="/opt/javassembly"
    bin_dir="/usr/local/bin"
    mkdir -p "$install_root/lib" "$bin_dir"
    install -m 644 "$compiler_jar" "$install_root/javassembly.jar"
    cp -R "$repo_root/lib/." "$install_root/lib/"
    install -m 755 "$repo_root/jvsc" "$install_root/jvsc"
    install -m 755 "$repo_root/jvs" "$install_root/jvs"

    for command_name in jvsc jvs; do
        command_link="$bin_dir/$command_name"
        command_target="$install_root/$command_name"
        if [[ -e "$command_link" || -L "$command_link" ]]; then
            if [[ "$(readlink -f -- "$command_link")" != "$command_target" ]]; then
                printf "Erro: '%s' já existe e não pertence a esta instalação.\n" "$command_link" >&2
                exit 1
            fi
        else
            ln -s "$command_target" "$command_link"
        fi
    done

    printf 'JavaAssembly instalado globalmente em: %s\n' "$install_root"
    printf 'Comandos disponíveis em: %s\n' "$bin_dir"
    printf 'Abra um novo terminal e use jvsc e jvs de qualquer pasta.\n'
    exit 0
fi

if [[ -n "${1:-}" ]]; then
    printf 'Uso: bash install.sh [--system]\n' >&2
    exit 2
fi

install_root="${HOME}/.local/lib/javassembly"
bin_dir="${HOME}/.local/bin"

mkdir -p "$install_root/lib" "$bin_dir"
install -m 644 "$compiler_jar" "$install_root/javassembly.jar"
cp -R "$repo_root/lib/." "$install_root/lib/"
install -m 755 "$repo_root/jvsc" "$bin_dir/jvsc"
install -m 755 "$repo_root/jvs" "$bin_dir/jvs"

profile_file="${HOME}/.profile"
path_line='export PATH="$HOME/.local/bin:$PATH"'
if [[ -f "$profile_file" ]] && ! grep -Fq "$path_line" "$profile_file"; then
    printf '\n# JavaAssembly command-line tools\n%s\n' "$path_line" >> "$profile_file"
elif [[ ! -f "$profile_file" ]]; then
    printf '# JavaAssembly command-line tools\n%s\n' "$path_line" > "$profile_file"
fi

printf 'JavaAssembly instalado em: %s\n' "$install_root"
printf 'Comandos instalados em: %s\n' "$bin_dir"
printf 'Feche e abra o terminal, ou execute: source ~/.profile\n'
printf 'Depois compile com: jvsc -l /caminho/para/Arquivo.jvs\n'
