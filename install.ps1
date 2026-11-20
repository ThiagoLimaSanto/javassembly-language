$ErrorActionPreference = "Stop"

$sourceRoot = $PSScriptRoot
$installRoot = Join-Path $env:LOCALAPPDATA "Programs\JavaAssembly"
$jarSource = Join-Path $sourceRoot "javassembly.jar"
$libraryTarget = Join-Path $installRoot "lib"

if (-not (Test-Path $jarSource)) {
    $mainSource = Join-Path $sourceRoot "compiler\Main.java"
    if (-not (Test-Path $mainSource) -or -not (Get-Command javac -ErrorAction SilentlyContinue) -or -not (Get-Command jar -ErrorAction SilentlyContinue)) {
        throw "javassembly.jar nao encontrado. Baixe o pacote de uma Release ou instale um JDK para compilar do codigo-fonte."
    }

    $buildRoot = Join-Path $sourceRoot "build\jar-install"
    $classes = Join-Path $buildRoot "classes"
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    & javac -encoding UTF-8 -d $classes -sourcepath $sourceRoot $mainSource
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & jar --create --file (Join-Path $buildRoot "javassembly.jar") --main-class compiler.Main -C $classes .
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $jarSource = Join-Path $buildRoot "javassembly.jar"
}

New-Item -ItemType Directory -Force -Path $installRoot, $libraryTarget | Out-Null
Copy-Item -Path $jarSource -Destination (Join-Path $installRoot "javassembly.jar") -Force
Copy-Item -Path (Join-Path $sourceRoot "lib\*") -Destination $libraryTarget -Recurse -Force
Copy-Item -Path (Join-Path $sourceRoot "jvsc.cmd") -Destination $installRoot -Force
Copy-Item -Path (Join-Path $sourceRoot "jvs.cmd") -Destination $installRoot -Force
Copy-Item -Path (Join-Path $sourceRoot "jvs.ps1") -Destination $installRoot -Force

$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$pathEntries = @($userPath -split ";" | Where-Object { $_ })
if (-not ($pathEntries | Where-Object { $_.TrimEnd("\") -ieq $installRoot.TrimEnd("\") })) {
    $newUserPath = (@($pathEntries) + $installRoot) -join ";"
    [Environment]::SetEnvironmentVariable("Path", $newUserPath, "User")
}

if (($env:Path -split ";" | Where-Object { $_.TrimEnd("\") -ieq $installRoot.TrimEnd("\") }).Count -eq 0) {
    $env:Path = "$installRoot;$env:Path"
}

Write-Host "JavaAssembly instalado em $installRoot"
Write-Host "Abra um terminal novo e use: jvsc -w C:\caminho\Arquivo.jvs"
