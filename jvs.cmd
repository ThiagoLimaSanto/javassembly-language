@echo off
setlocal

set "JVS_HOME=%~dp0"
if not exist "%JVS_HOME%jvs.ps1" if defined JAVASSEMBLY_HOME set "JVS_HOME=%JAVASSEMBLY_HOME%\"
if not exist "%JVS_HOME%jvs.ps1" (
    echo Erro: nao encontrei jvs.ps1. 1>&2
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%JVS_HOME%jvs.ps1" %*
exit /b %ERRORLEVEL%
