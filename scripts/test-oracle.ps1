# Script para carregar o .env e testar a conexao JDBC com o Oracle da FIAP.
# Uso:
#   .\scripts\test-oracle.ps1

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$loadEnvScript = Join-Path $PSScriptRoot "load-env.ps1"
$javaTestFile = Join-Path $PSScriptRoot "TestOracleConnection.java"

# 1. Carrega as variaveis do .env para o processo atual
. $loadEnvScript

# 2. Localiza o driver ojdbc11 no repositorio local do Maven
$m2OracleDir = Join-Path $env:USERPROFILE ".m2\repository\com\oracle\database\jdbc\ojdbc11"
$ojdbcJar = $null

if (Test-Path $m2OracleDir) {
    $ojdbcJar = Get-ChildItem -Path $m2OracleDir -Recurse -Filter "ojdbc11-*.jar" |
        Where-Object { $_.Name -notmatch "sources|javadoc" } |
        Select-Object -First 1 -ExpandProperty FullName
}

if (-not $ojdbcJar) {
    Write-Host "[INFO] Driver ojdbc11 nao encontrado no .m2 local. Baixando dependencias com Maven..." -ForegroundColor Yellow
    & (Join-Path $projectRoot "mvnw.cmd") -q dependency:resolve
    $ojdbcJar = Get-ChildItem -Path $m2OracleDir -Recurse -Filter "ojdbc11-*.jar" |
        Where-Object { $_.Name -notmatch "sources|javadoc" } |
        Select-Object -First 1 -ExpandProperty FullName
}

if (-not $ojdbcJar) {
    Write-Error "Nao foi possivel localizar o JAR do ojdbc11 em $m2OracleDir."
    exit 1
}

Write-Host "[INFO] Usando driver: $ojdbcJar" -ForegroundColor Cyan

# 3. Executa o teste JDBC somente leitura (SELECT ... FROM DUAL)
& java -cp $ojdbcJar $javaTestFile
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
