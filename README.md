# JavaAssembly

JavaAssembly compila arquivos `.jvs` para programas Linux x86-64 ou Windows x86-64.
O frontend, a IR e as otimizações são compartilhados; o destino é escolhido na compilação.

## Requisitos

Para usar o pacote publicado em uma Release, instale:

- Java Runtime (JRE) 17 ou superior;
- NASM;
- o linker correspondente ao sistema para o qual você vai gerar o executável.

O ZIP da Release já contém `javassembly.jar` e as bibliotecas da linguagem. Portanto,
`javac` e o JDK não são necessários para instalar ou usar esse pacote. O JDK é necessário
somente se você for compilar o JavaAssembly a partir do código-fonte.

## Baixar o JavaAssembly

Abra [Releases](https://github.com/ThiagoLimaSanto/javassembly-language/releases/latest)
e baixe o arquivo `javassembly-vX.Y.Z.zip`. Esse é o pacote pronto para instalar nos dois
sistemas. Os arquivos **Source code** contêm apenas o código-fonte do repositório.

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

Se for compilar o JavaAssembly a partir do código-fonte, instale também o JDK:

```bash
sudo apt install openjdk-17-jdk
```

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

1. Instale o Java Runtime 17 ou superior. Com WinGet:

   ```powershell
   winget install --id EclipseAdoptium.Temurin.17.JRE --exact
   ```

   Para compilar o JavaAssembly a partir do código-fonte, instale o JDK no lugar do JRE.
   Consulte as [instruções oficiais do Eclipse Temurin](https://adoptium.net/installation/).

2. Instale o NASM. Com WinGet:

   ```powershell
   winget install --id NASM.NASM --exact
   ```

   Se o comando não encontrar o pacote, baixe o instalador na página oficial do
   [NASM](https://www.nasm.us/).

3. Instale o [MSYS2](https://www.msys2.org/). Abra o terminal **MSYS2 UCRT64** pelo menu
   Iniciar e instale o linker MinGW-w64 e as bibliotecas de importação do Windows:

   ```bash
   pacman -Syu
   ```

   Se o terminal fechar ao atualizar, abra novamente o **MSYS2 UCRT64** e repita
   `pacman -Syu`. Quando a atualização terminar, instale os pacotes necessários:

   ```bash
   pacman -S --needed mingw-w64-ucrt-x86_64-binutils mingw-w64-ucrt-x86_64-crt
   ```

   Adicione `C:\msys64\ucrt64\bin` ao `Path` do usuário no Windows. Se instalou o MSYS2
   em outro local, use o caminho correspondente à pasta `ucrt64\bin`. O compilador procura
   `libkernel32.a` junto da instalação do `ld.exe` e adiciona o diretório automaticamente.
   Para uma instalação MinGW fora dos layouts padrão, configure `JAVASSEMBLY_WINDOWS_LIB_DIR`
   com o diretório que contém `libkernel32.a`.

4. Abra um terminal novo e confirme as ferramentas:

   ```powershell
   java -version
   nasm -v
   where.exe ld.exe
   ```

   O primeiro resultado de `where.exe ld.exe` deve apontar para `ucrt64\bin\ld.exe`.
   O backend usa esse linker diretamente; não precisa instalar GCC, `link.exe` ou o
   Windows SDK.

Agora instale o pacote da Release conforme a seção [Instalação no Windows](#instalação-no-windows)
e compile com `jvsc -w caminho\para\Arquivo.jvs`.

Para gerar executáveis Linux, use Linux ou WSL e siga as instruções de Ubuntu/Debian.

Os comandos do JDK estão documentados pela Microsoft na página de instalação do
[Microsoft Build of OpenJDK](https://learn.microsoft.com/java/openjdk/install). Os pacotes
MinGW-w64 usados acima são mantidos pelo [MSYS2](https://www.msys2.org/).

## Instalação no Linux

Baixe o `javassembly-vX.Y.Z.zip` pela seção [Releases](#baixar-o-javassembly) e extraia-o.
No terminal, entre na pasta extraída e execute o instalador.

Instalação global para todos os usuários:

```bash
unzip javassembly-vX.Y.Z.zip -d javassembly
cd javassembly
sudo bash install.sh --system
```

Instalação somente para o usuário atual:

```bash
unzip javassembly-vX.Y.Z.zip -d javassembly
cd javassembly
bash install.sh
source ~/.profile
```

## Instalação no Windows

Baixe `javassembly-vX.Y.Z.zip` pela seção [Releases](#baixar-o-javassembly), extraia o
arquivo e abra o PowerShell na pasta extraída. Execute:

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
