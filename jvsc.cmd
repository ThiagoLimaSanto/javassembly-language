@echo off
setlocal EnableExtensions

set "JVS_HOME=%~dp0"
if not exist "%JVS_HOME%compiler" if defined JAVASSEMBLY_HOME set "JVS_HOME=%JAVASSEMBLY_HOME%\"
if not exist "%JVS_HOME%compiler" (
    echo Erro: nao encontrei os arquivos do JavaAssembly. 1>&2
    exit /b 1
)

set "COMMAND=build"
if /i "%~1"=="--run" (
    set "COMMAND=run"
    shift
)
if /i "%~1"=="--exec" (
    set "COMMAND=exec"
    shift
)

if "%~3" NEQ "" goto :usage
set "TARGET=%~1"
set "SOURCE=%~2"
if /i not "%TARGET%"=="-w" if /i not "%TARGET%"=="-l" goto :usage
if /i not "%SOURCE:~-4%"==".jvs" goto :usage
if not exist "%SOURCE%" (
    echo Erro: arquivo-fonte nao encontrado: "%SOURCE%" 1>&2
    exit /b 1
)

set "OUTPUT_DIR=%JVS_HOME%build\compiler"
set "SOURCES_FILE=%TEMP%\javassembly-sources-%RANDOM%.txt"
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
for /r "%JVS_HOME%compiler" %%S in (*.java) do echo "%%~fS">>"%SOURCES_FILE%"
javac -d "%OUTPUT_DIR%" @"%SOURCES_FILE%"
set "JAVAC_STATUS=%ERRORLEVEL%"
del /q "%SOURCES_FILE%" >nul 2>&1
if not "%JAVAC_STATUS%"=="0" exit /b %JAVAC_STATUS%

java -Djavassembly.libraryRoot="%JVS_HOME%lib" -cp "%OUTPUT_DIR%" compiler.Main %COMMAND% "%TARGET%" "%SOURCE%"
exit /b %ERRORLEVEL%

:usage
echo Uso: jvsc -w^|-l arquivo.jvs 1>&2
echo      jvs -w^|-l arquivo.jvs 1>&2
exit /b 2
