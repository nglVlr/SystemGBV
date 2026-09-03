@echo off
REM Ejecuta este archivo COMO ADMINISTRADOR (clic derecho > Ejecutar como administrador).
netsh advfirewall firewall delete rule name="Sistema Granados LAN" >nul 2>&1
netsh advfirewall firewall add rule name="Sistema Granados LAN" dir=in action=allow protocol=TCP localport=8085 profile=any
if errorlevel 1 (
  echo ERROR: no se pudo crear la regla. Ejecuta este archivo como Administrador.
  pause
  exit /b 1
)
echo Listo. Puerto 8085 abierto para otras PCs de la red.
pause
