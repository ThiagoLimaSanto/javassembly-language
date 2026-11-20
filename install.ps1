$ErrorActionPreference = "Stop"

$sourceRoot = $PSScriptRoot
$installRoot = Join-Path $env:LOCALAPPDATA "Programs\JavaAssembly"
$compilerTarget = Join-Path $installRoot "compiler"
$libraryTarget = Join-Path $installRoot "lib"

New-Item -ItemType Directory -Force -Path $compilerTarget, $libraryTarget | Out-Null
Copy-Item -Path (Join-Path $sourceRoot "compiler\*") -Destination $compilerTarget -Recurse -Force
Copy-Item -Path (Join-Path $sourceRoot "lib\*") -Destination $libraryTarget -Recurse -Force
Copy-Item -Path (Join-Path $sourceRoot "jvsc.cmd") -Destination $installRoot -Force
Copy-Item -Path (Join-Path $sourceRoot "jvs.cmd") -Destination $installRoot -Force

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
