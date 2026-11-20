# JavaAssembly

JavaAssembly compila arquivos `.jvs` para programas Linux x86-64 ou Windows x86-64.
O frontend, a IR e as otimizações são compartilhados; o destino é escolhido na compilação.

## Requisitos

Em qualquer sistema, instale um JDK com `javac` e o NASM.

Para compilar para Linux, também é necessário GNU `ld`.

Para compilar para Windows, é necessário `link.exe` com o Windows SDK e `kernel32.lib`
no Windows. No Linux, instale o linker cruzado MinGW-w64 x86-64 e sua biblioteca
`kernel32` (`x86_64-w64-mingw32-ld`).

## Instalação no Linux

Instalação global para todos os usuários:

```bash
sudo bash install.sh --system
```

Instalação somente para o usuário atual:

```bash
bash install.sh
source ~/.profile
```

## Instalação no Windows

Na pasta do JavaAssembly, execute no PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

O instalador adiciona JavaAssembly ao `PATH` do usuário. Abra um terminal novo para usar
`jvsc` e `jvs`.

## Compilar e executar

Informe o destino explicitamente: `-l` para Linux ou `-w` para Windows.

```bash
jvsc -l /caminho/para/Teste.jvs
jvs -l /caminho/para/Teste.jvs
```

Para gerar um `.exe`:

```bash
jvsc -w /caminho/para/Teste.jvs
```

No Windows, use os mesmos comandos no terminal:

```bat
jvsc -w C:\Projetos\Teste\src\Teste.jvs
jvs -w C:\Projetos\Teste\src\Teste.jvs
```

`jvsc` compila e informa o caminho do executável. `jvs` executa o programa. No Linux,
`jvs -w` usa Wine se estiver instalado; sem Wine, execute o `.exe` no Windows.

## Arquivos gerados

Para projetos com uma pasta `src/`, os arquivos ficam na raiz do projeto. A saída Linux
mantém `out/`; a saída Windows usa `out/windows-x64/` para não sobrescrever a saída Linux:

```text
MeuProjeto/
├── src/
│   └── Teste.jvs
└── out/
    ├── Teste.asm
    ├── Teste.o
    ├── Teste
    └── windows-x64/
        ├── Teste.asm
        ├── Teste.obj
        └── Teste.exe
```

Para um arquivo fora de `src/`, a pasta `out/` é criada ao lado dele. Imports da
biblioteca padrão, como `jvs.io.Scanner`, são resolvidos a partir da instalação do
compilador.

## Limitação atual do destino Windows

O método `main(String[] args)` recebe um array vazio no Windows; os argumentos passados
na linha de comando ainda não são convertidos para esse array.
