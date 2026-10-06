@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Axle - restart

rem ============================================================
rem  Frees port 8080 and starts the app again.
rem
rem    restart-app.bat         stop whatever holds 8080, then rebuild
rem                            and start the app (via run-frontend.bat)
rem    restart-app.bat free    only free the port, do not start
rem
rem  Stopping the old server first also matters for the build: while it
rem  runs it holds target\*.jar open, and Maven then writes a broken jar.
rem
rem  If the program on 8080 is not Java (so probably not this app), you
rem  are asked before it is stopped.
rem ============================================================

set "PORT=8080"
set "APP_DIR=%~dp0"
set "MODE=%~1"
cd /d "%APP_DIR%"

echo.
echo   Axle. Freeing port %PORT%
echo   -----------------------
echo.

rem ---- Who is listening on the port? (IPv4 and IPv6, each PID once) ----
set "PIDS="
for /f "delims=" %%P in ('powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique"') do (
    set "PIDS=!PIDS! %%P"
)

if not defined PIDS (
    echo   Nothing is using port %PORT%.
    goto port_free
)

set "DECLINED="
for %%P in (%PIDS%) do (
    call :stop_pid %%P
    if errorlevel 2 set "DECLINED=1"
)
if defined DECLINED goto fail

rem ---- The window run-frontend.bat opened for the old server ----
taskkill /FI "WINDOWTITLE eq Axle server*" /T /F >nul 2>nul

rem ---- Wait until the port is really released (up to ~20 seconds) ----
set /a TRIES=0
:wait_free
call :is_listening
if errorlevel 1 goto port_free
set /a TRIES+=1
if !TRIES! geq 10 (
    echo   [ERROR] Port %PORT% is still in use. Try running this file as administrator.
    goto fail
)
ping -n 3 127.0.0.1 >nul
goto wait_free

:port_free
echo   Port %PORT% is free.
echo.
rem Connections the old server left in TIME_WAIT expire by themselves within
rem a couple of minutes and do not stop the new server from listening.

if /i "%MODE%"=="free" (
    echo   Not starting the app ^(free mode^).
    echo.
    endlocal
    exit /b 0
)

if not exist "%APP_DIR%run-frontend.bat" (
    echo   [ERROR] run-frontend.bat was not found next to this file.
    goto fail
)
echo   Starting the app...
call "%APP_DIR%run-frontend.bat"
endlocal
exit /b %errorlevel%

:fail
echo.
pause
endlocal
exit /b 1

rem ---- stop one process: %1 = PID; returns 2 if the user declined ----
:stop_pid
set "PID=%~1"
if "%PID%"=="0" exit /b 0
if "%PID%"=="4" (
    echo   [ERROR] Port %PORT% is held by Windows itself ^(PID 4, usually IIS or
    echo           another HTTP.sys service^). Stop that service, or change
    echo           server.port in src\main\resources\application.properties.
    exit /b 2
)
set "NAME="
for /f "tokens=1 delims=," %%N in ('tasklist /FI "PID eq %PID%" /FO CSV /NH 2^>nul') do set "NAME=%%~N"
if not defined NAME set "NAME=unknown"

if /i not "%NAME%"=="java.exe" if /i not "%NAME%"=="javaw.exe" (
    echo   Port %PORT% is used by %NAME% ^(PID %PID%^), which is not Java.
    set "ANSWER="
    set /p "ANSWER=  Stop it anyway? [y/N] "
    if /i not "!ANSWER!"=="y" (
        echo   Left it running.
        exit /b 2
    )
)

echo   Stopping %NAME% ^(PID %PID%^) on port %PORT%...
taskkill /PID %PID% /T /F >nul 2>nul
if errorlevel 1 (
    echo   [ERROR] Could not stop PID %PID%. Try running this file as administrator.
    exit /b 2
)
exit /b 0

rem ---- returns 0 while something still listens on the port ----
:is_listening
powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>nul
exit /b %errorlevel%
