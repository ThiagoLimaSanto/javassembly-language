# JavaAssembly

JavaAssembly compila arquivos `.jvs` para programas Linux x86-64 ou Windows x86-64.
O frontend, a IR e as otimizações são compartilhados; o destino é escolhido na compilação.

## Requisitos

O pacote de uma Release precisa de Java Runtime (JRE) 17 ou superior e do NASM.
Para gerar um executável, também precisa do linker do sistema de destino. O JDK só é
necessário para compilar o JavaAssembly a partir do código-fonte.

## Tutorial: instalar os requisitos

### Ubuntu ou Debian: compilar para Linux

Abra o terminal e instale o Java Runtime, o NASM e o GNU linker:

```bash
sudo apt update
sudo apt install openjdk-17-jre-headless nasm binutils
```

Confirme que os comandos estão disponíveis:

```bash
java -version
nasm -v
ld --version
```

Se você for instalar diretamente do repositório de código-fonte, instale o JDK no
lugar do JRE (`openjdk-17-jdk`). O pacote baixado em **Releases** já inclui o JAR.

Com esses requisitos, o Linux pode gerar executáveis Linux usando `jvsc -l`.

### Ubuntu ou Debian: gerar executáveis Windows

Para compilar um `.exe` a partir do Linux, instale o linker cruzado e as bibliotecas
de importação do MinGW-w64:

```bash
sudo apt install binutils-mingw-w64-x86-64 mingw-w64-x86-64-dev
```

Confirme o linker:

```bash
x86_64-w64-mingw32-ld --version
```

Depois use `jvsc -w`. O arquivo `.exe` gerado pode ser executado no Windows; no Linux,
é necessário ter Wine para executá-lo com `jvs -w`. Os pacotes correspondem ao
[OpenJDK 17 do Ubuntu](https://packages.ubuntu.com/openjdk-17-jdk), ao
[NASM](https://packages.ubuntu.com/nasm) e ao [linker MinGW-w64 x86-64](https://packages.ubuntu.com/binutils-mingw-w64-x86-64).

### Windows: compilar para Windows

1. Instale Java 17 ou superior. O pacote de uma Release precisa apenas do Java Runtime;
   para instalar a partir do código-fonte, instale um JDK. Com WinGet, por exemplo:

   ```powershell
   winget install --id Microsoft.OpenJDK.25 --exact
   ```

2. Instale o NASM. Com WinGet:

   ```powershell
   winget install --id NASM.NASM --exact
   ```

   Se o comando não encontrar o pacote, baixe o instalador na página oficial do
   [NASM](https://www.nasm.us/).

3. Instale o [Visual Studio Build Tools](https://learn.microsoft.com/cpp/overview/acquire-msvc?view=msvc-170).
   No instalador, selecione **Desenvolvimento para desktop com C++**, as ferramentas
   MSVC para x64/x86 e um Windows SDK. Esse conjunto fornece `link.exe` e `kernel32.lib`.

4. Abra o **Developer PowerShell** ou o **Developer Command Prompt** do Visual Studio.
   Esse terminal configura o caminho do linker e das bibliotecas do Windows SDK.

5. Verifique as ferramentas:

   ```powershell
   java -version
   nasm -v
   where.exe link
   ```

Agora instale o JavaAssembly conforme a seção [Instalação no Windows](#instalação-no-windows)
e compile com `jvsc -w caminho\para\Arquivo.jvs`.

Para gerar executáveis Linux, use Linux ou WSL e siga as instruções de Ubuntu/Debian.

Os comandos do JDK estão documentados pela Microsoft na página de instalação do
[Microsoft Build of OpenJDK](https://learn.microsoft.com/java/openjdk/install). O
passo do MSVC segue a documentação de instalação dos
[Build Tools C++](https://learn.microsoft.com/cpp/overview/acquire-msvc?view=msvc-170).

## Instalação no Linux

Baixe e extraia o arquivo ZIP mais recente na página **Releases** do GitHub. O pacote
inclui `javassembly.jar`, as bibliotecas da linguagem e os lançadores. Não é necessário
ter `javac` para instalar uma Release.

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

Baixe e extraia o ZIP da Release. Na pasta extraída, execute no PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

O instalador copia o JAR e a biblioteca padrão, e adiciona JavaAssembly ao `PATH` do
usuário. Abra um terminal novo para usar `jvsc` e `jvs`. Não é necessário instalar o
JDK ao usar uma Release; o Java Runtime, NASM e ferramentas de linkedição continuam
necessários.

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
