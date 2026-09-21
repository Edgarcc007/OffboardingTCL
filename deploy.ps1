# ============================================================
#  Despliegue OffboardingTCL
#  Uso:  C:\OffboardingTCL\deploy.ps1
# ============================================================

$ErrorActionPreference = "Stop"

$proyecto = "C:\OffboardingTCL"
$destino  = "C:\OffboardingTCL\service\offboarding.jar"
$origen   = "C:\OffboardingTCL\target\offboarding-0.1.0.jar"
$logApp   = "C:\OffboardingTCL\service\app.log"
$tarea    = "OffboardingTCL"
$puerto   = 8081

Write-Host ""
Write-Host "=== DESPLIEGUE OffboardingTCL ===" -ForegroundColor White
Write-Host ""

# ---------- 1. Detener ----------
Write-Host "[1/5] Deteniendo aplicacion..." -ForegroundColor Cyan

Stop-ScheduledTask -TaskName $tarea -ErrorAction SilentlyContinue
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

$intentos = 0
while ((Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue) -and $intentos -lt 10) {
    Start-Sleep -Seconds 2
    $intentos++
}

if (Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue) {
    throw "El puerto $puerto sigue ocupado. Se aborta para no corromper el JAR."
}

Write-Host "      Puerto $puerto libre." -ForegroundColor Green

# ---------- 2. Compilar ----------
Write-Host "[2/5] Compilando..." -ForegroundColor Cyan

Set-Location $proyecto
mvn clean package -DskipTests -q

if ($LASTEXITCODE -ne 0) {
    throw "Build fallido. No se despliega nada."
}

if (-not (Test-Path $origen)) {
    throw "No se encontro el JAR compilado en $origen"
}

Write-Host "      Build correcto." -ForegroundColor Green

# ---------- 3. Respaldar y copiar ----------
Write-Host "[3/5] Respaldando y copiando JAR..." -ForegroundColor Cyan

if (Test-Path $destino) {
    $sello = Get-Date -Format "yyyyMMdd-HHmmss"
    Copy-Item $destino "$destino.$sello.bak" -Force
}

Copy-Item $origen $destino -Force

$tamano = (Get-Item $destino).Length / 1MB
Write-Host ("      JAR desplegado: {0:N1} MB" -f $tamano) -ForegroundColor Green

if ($tamano -lt 20) {
    throw "El JAR parece incompleto ($([math]::Round($tamano,1)) MB). Revisa el build."
}

# ---------- 4. Arrancar ----------
Write-Host "[4/5] Arrancando..." -ForegroundColor Cyan

Start-ScheduledTask -TaskName $tarea
Start-Sleep -Seconds 40

# ---------- 5. Verificar ----------
Write-Host "[5/5] Verificando..." -ForegroundColor Cyan

if (Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue) {
    Write-Host ""
    Write-Host "      OK - escuchando en el puerto $puerto" -ForegroundColor Green
    Write-Host "      http://10.138.96.13:$puerto/" -ForegroundColor Green
    Write-Host ""
    Write-Host "      Ultimas lineas del log:" -ForegroundColor Gray
    Get-Content $logApp -Tail 6
    Write-Host ""
    Write-Host "      Recuerda Ctrl+F5 en el navegador." -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "      FALLO - la aplicacion no responde en $puerto" -ForegroundColor Red
    Write-Host ""
    Write-Host "      Log:" -ForegroundColor Gray
    Get-Content $logApp -Tail 30
}

Write-Host ""