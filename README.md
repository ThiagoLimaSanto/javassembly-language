# JavaAssembly

JavaAssembly compila arquivos `.jvs` para executáveis Linux x86-64. Os comandos `jvsc`
e `jvs` permitem compilar e executar programas a partir de qualquer pasta.

## Requisitos

- Linux x86-64
- JDK com `javac`
- NASM
- GNU `ld` (fornecido pelo pacote Binutils)

## Instalação global

Esta opção instala os comandos para todos os usuários. Na pasta do JavaAssembly, execute:

```bash
sudo bash install.sh --system
```

O compilador é instalado em `/opt/javassembly`; os comandos ficam em `/usr/local/bin`.
Abra um terminal novo após a instalação. Não é necessário adicionar caminhos ao perfil
do shell.

## Instalação somente para o usuário atual

```bash
bash install.sh
source ~/.profile
```

Se o terminal interativo não carregar `~/.profile` e disser que `jvsc` não foi encontrado,
adicione `export PATH="$HOME/.local/bin:$PATH"` ao `~/.bashrc` e execute `source ~/.bashrc`.

## Compilar e executar

Passe o caminho do arquivo `.jvs`; ele pode estar fora da pasta onde o compilador foi
instalado:

```bash
jvsc /home/usuario/Downloads/teste/Teste.jvs
jvs /home/usuario/Downloads/teste/Teste.jvs
```

`jvsc` compila e cria o assembly, o arquivo objeto e o executável. `jvs` executa o
programa compilado. Também aceita um caminho relativo à pasta atual:

```bash
jvsc exemplos/Teste.jvs
jvs exemplos/Teste.jvs
```

É possível passar argumentos ao programa:

```bash
jvs /home/usuario/Downloads/teste/Teste.jvs argumento1 argumento2
```

## Pasta de saída

Em um projeto com uma pasta `src/`, os arquivos gerados ficam em `out/` na raiz do
projeto:

```text
MeuProjeto/
├── src/
│   └── Main.jvs
└── out/
    ├── Main.asm
    ├── Main.o
    └── Main
```

Para um arquivo `.jvs` fora de uma pasta `src/`, `out/` é criada ao lado do arquivo.
Por exemplo, `Downloads/teste/Teste.jvs` gera os artefatos em
`Downloads/teste/out/`. Imports da biblioteca padrão, como `jvs.io.Scanner`, são
resolvidos a partir da instalação do compilador.

## Comandos

```bash
jvsc --help
jvs --help
```
