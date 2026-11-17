# Carrega as variaveis do arquivo .env para a sessao atual do PowerShell.
# Uso (com ponto e espaco na frente para aplicar na sessao atual):
#   . .\scripts\load-env.ps1

param(
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) ".env")
)

if (-not (Test-Path $EnvFile)) {
    Write-Error "Arquivo .env nao encontrado em: $EnvFile"
    return
}

$loadedCount = 0
Get-Content -Path $EnvFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
        return
    }

    $parts = $line.Split("=", 2)
    if ($parts.Length -eq 2) {
        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        [System.Environment]::SetEnvironmentVariable($key, $value, "Process")
        $loadedCount++
    }
}

Write-Host "[OK] $loadedCount variaveis carregadas de $EnvFile para esta sessao." -ForegroundColor Green
