@echo off
set "MAVEN_BIN=%~dp0tools\apache-maven-3.9.6\bin\mvn.cmd"
if exist "%MAVEN_BIN%" (
    "%MAVEN_BIN%" %*
) else (
    echo [ERROR] Portable Maven not found at %MAVEN_BIN%. Running setup_maven.ps1...
    powershell -ExecutionPolicy Bypass -File "%~dp0setup_maven.ps1"
    "%MAVEN_BIN%" %*
)
