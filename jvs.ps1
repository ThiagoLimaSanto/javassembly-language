$ErrorActionPreference = "Stop"

$arguments = @($args)
if ($arguments.Count -lt 2) {
    [Console]::Error.WriteLine("Uso: jvs -w|-l arquivo.jvs [argumentos...]")
    exit 2
}

$target = $arguments[0]
$source = $arguments[1]
$programArguments = @()
if ($arguments.Count -gt 2) {
    $programArguments = $arguments[2..($arguments.Count - 1)]
}

if ($target -ne "-w" -and $target -ne "-l") {
    [Console]::Error.WriteLine("Destino inválido '$target'. Use -l (Linux) ou -w (Windows).")
    exit 2
}
if (-not $source.EndsWith(".jvs", [StringComparison]::OrdinalIgnoreCase)) {
    [Console]::Error.WriteLine("Informe o arquivo-fonte com extensão .jvs antes dos argumentos do programa.")
    exit 2
}

$jvsHome = $PSScriptRoot
if (-not (Test-Path (Join-Path $jvsHome "javassembly.jar")) -and $env:JAVASSEMBLY_HOME) {
    $jvsHome = $env:JAVASSEMBLY_HOME
}

$jarPath = Join-Path $jvsHome "javassembly.jar"
$libraryPath = Join-Path $jvsHome "lib"
if (-not (Test-Path $jarPath)) {
    [Console]::Error.WriteLine("Erro: javassembly.jar não encontrado. Instale o pacote de uma Release.")
    exit 1
}

$javaArguments = @(
    "-Djavassembly.libraryRoot=$libraryPath",
    "-jar",
    $jarPath,
    "exec",
    $target,
    $source
) + $programArguments

& java @javaArguments
exit $LASTEXITCODE
