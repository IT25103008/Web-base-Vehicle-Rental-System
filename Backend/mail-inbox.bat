@echo off
setlocal EnableExtensions
title Axle Mail Inbox
chcp 65001 >nul

rem ============================================================
rem  Opens the "mail inbox" window.
rem
rem  Without a mail server, the app writes every email it would send
rem  (confirm your email, reset your password, booking updates) to its
rem  log file, logs\axle.log. This window watches that file and shows
rem  each email as it arrives, with the link highlighted:
rem     O = open the newest link   C = copy it   Q = quit
rem
rem  Start the app first (run-frontend.bat, restart-app.bat or
rem  mvn spring-boot:run). Leave this window open next to the app.
rem ============================================================

cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0mail-inbox.ps1"
if errorlevel 1 (
    echo.
    echo   [ERROR] The inbox could not start. Check that PowerShell is available.
    pause
)
endlocal
