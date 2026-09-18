# OmniStore PowerShell Build & Run Script
$ErrorActionPreference = "Stop"

$workspaceRoot = $PSScriptRoot
if (-not $workspaceRoot) {
    $workspaceRoot = (Get-Location).Path
}
Set-Location $workspaceRoot

Write-Host "=========================================================================" -ForegroundColor Cyan
Write-Host "              OMNISTORE: BUILD & RUN (POWERSHELL)                       " -ForegroundColor Cyan
Write-Host "=========================================================================" -ForegroundColor Cyan

# 1. Locate javac and java
$javacCmd = Get-Command javac -ErrorAction SilentlyContinue
$javacPath = if ($javacCmd) { $javacCmd.Source } else { $null }

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
$javaPath = if ($javaCmd) { $javaCmd.Source } else { $null }

$knownJdkDirs = @(
    "C:\Users\Lenovo\.antigravity-ide\extensions\redhat.java-1.55.0-win32-x64\jre\21.0.11-win32-x86_64\bin",
    "C:\Users\Lenovo\.antigravity\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64\bin",
    "C:\Program Files\Java\jdk-21\bin",
    "C:\Program Files\Eclipse Adoptium\jdk-21\bin"
)

if (-not $javacPath) {
    foreach ($dir in $knownJdkDirs) {
        if (Test-Path "$dir\javac.exe") {
            $javacPath = "$dir\javac.exe"
            $javaPath = "$dir\java.exe"
            Write-Host "Using detected JDK 21 at: $dir" -ForegroundColor Green
            break
        }
    }
}

if (-not $javacPath -or -not (Test-Path $javacPath)) {
    Write-Host "[ERROR] Could not find 'javac.exe'. Please install JDK 17+ or add it to PATH." -ForegroundColor Red
    exit 1
}

# 2. Compile
if (-not (Test-Path "$workspaceRoot\bin")) {
    New-Item -ItemType Directory -Path "$workspaceRoot\bin" | Out-Null
}

Write-Host "Compiling sources into bin/..." -ForegroundColor Yellow
$javaFiles = Get-ChildItem -Path "$workspaceRoot\src" -Filter *.java -Recurse | Select-Object -ExpandProperty FullName
& $javacPath -d "$workspaceRoot\bin" $javaFiles

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Compilation successful!`n" -ForegroundColor Green

# 3. Execute
Write-Host "Running com.omnistore.Main...`n" -ForegroundColor Cyan
& $javaPath -cp "$workspaceRoot\bin" com.omnistore.Main

