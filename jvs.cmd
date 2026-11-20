@echo off
setlocal

set "JVS_HOME=%~dp0"
if not exist "%JVS_HOME%jvsc.cmd" if defined JAVASSEMBLY_HOME set "JVS_HOME=%JAVASSEMBLY_HOME%\"
if not exist "%JVS_HOME%jvsc.cmd" (
    echo Erro: nao encontrei jvsc.cmd. 1>&2
    exit /b 1
)

call "%JVS_HOME%jvsc.cmd" --exec %*
exit /b %ERRORLEVEL%
