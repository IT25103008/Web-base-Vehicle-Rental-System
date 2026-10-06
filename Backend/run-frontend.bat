@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Axle - Vehicle Rental Studio

rem ============================================================
rem  Starts the Spring Boot backend (which also serves the web
rem  app) and opens http://localhost:8080 in your browser.
rem
rem  Optional environment variables:
rem    DB_USERNAME   MySQL user      (default: root)
rem    DB_PASSWORD   MySQL password  (asked for if not set)
rem ============================================================

set "APP_DIR=%~dp0"
set "URL=http://localhost:8080"
set "JAR=%APP_DIR%target\vehiclerental-backend-0.0.1-SNAPSHOT.jar"
cd /d "%APP_DIR%"

echo.
echo   Axle. Vehicle Rental Studio
echo   ---------------------------
echo.

rem ---- Already running? Just open the browser ----
call :is_up
if !errorlevel! equ 0 (
    echo   The app is already running.
    goto open_browser
)

rem ---- Java ----
where java >nul 2>nul
if errorlevel 1 (
    echo   [ERROR] Java was not found. Install JDK 17 or newer and try again.
    goto fail
)

rem ---- Find Maven: PATH, then the copy IntelliJ/Maven Wrapper downloaded ----
set "MVN="
where mvn >nul 2>nul && set "MVN=mvn"
if not defined MVN if exist "%APP_DIR%mvnw.cmd" set "MVN=%APP_DIR%mvnw.cmd"
if not defined MVN if exist "%USERPROFILE%\.m2\wrapper\dists" (
    for /f "delims=" %%F in ('dir /s /b "%USERPROFILE%\.m2\wrapper\dists\mvn.cmd" 2^>nul') do set "MVN=%%F"
)

rem ---- Build the app (includes the frontend files) ----
if defined MVN (
    echo   Building the app, please wait...
    call "!MVN!" -q -B -DskipTests package
    if errorlevel 1 (
        echo   [ERROR] The build failed. See the messages above.
        goto fail
    )
) else (
    if not exist "%JAR%" (
        echo   [ERROR] Maven was not found and no built app exists yet.
        echo           Open the project in IntelliJ once, or install Maven.
        goto fail
    )
    echo   Maven not found - using the last built version of the app.
)

rem ---- Database login ----
if not defined DB_USERNAME set "DB_USERNAME=root"
if not defined DB_PASSWORD (
    set /p "DB_PASSWORD=  MySQL password for user '%DB_USERNAME%': "
)
echo.

rem ---- Start the server in its own window ----
echo   Starting the server (a new window will open - keep it open)...
start "Axle server - close this window to stop" cmd /k java -jar "%JAR%"

rem ---- Wait until it answers (up to ~90 seconds) ----
set /a TRIES=0
:wait_loop
ping -n 3 127.0.0.1 >nul
call :is_up
if !errorlevel! equ 0 goto started
set /a TRIES+=1
if !TRIES! lss 45 goto wait_loop
echo   [ERROR] The server did not start. Check the server window for errors
echo           (is MySQL running, and is the password correct?).
goto fail

:started
echo   Server is up.

:open_browser
if not defined AXLE_NO_BROWSER start "" "%URL%"
echo   Opened %URL%
echo.
echo   To stop the app, close the "Axle server" window.
echo.
endlocal
exit /b 0

:fail
echo.
pause
endlocal
exit /b 1

rem ---- returns 0 when http://localhost:8080 responds ----
:is_up
powershell -NoProfile -Command "try { Invoke-WebRequest '%URL%/api/branches' -UseBasicParsing -TimeoutSec 2 | Out-Null; exit 0 } catch { if ($_.Exception.Response) { exit 0 } else { exit 1 } }" >nul 2>nul
exit /b %errorlevel%
