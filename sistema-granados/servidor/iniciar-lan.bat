@echo off
REM Arranca Postgres (Docker) y el sistema para que otras PCs de la red lo usen.
REM Deja esta ventana ABIERTA. Si la cierras, el sistema se apaga.
cd /d "%~dp0.."

echo [1/2] Arrancando base de datos...
docker compose up -d
if errorlevel 1 (
  echo ERROR: Docker no arranco. Abre Docker Desktop y vuelve a intentar.
  pause
  exit /b 1
)

echo.
echo [2/2] Arrancando Sistema Granados...
echo.
echo En ESTA PC usa:   http://localhost:8085
echo En OTRAS PCs usa: http://IP_DE_ESTA_PC:8085
echo.
echo IPv4 de esta PC:
ipconfig | findstr /c:"IPv4"
echo.
echo No cierres esta ventana.
echo.

call mvnw.cmd spring-boot:run
pause
