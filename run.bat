@echo off
setlocal
cd /d "%~dp0"
set "task_java=java"
if defined JAVA_HOME set "task_java=%JAVA_HOME%\bin\java.exe"
"%task_java%" -Xmx512m -jar ServerPlacementLab.jar %*
if errorlevel 1 (
    echo Java 17+ is required. Install a JDK/JRE or set JAVA_HOME.
    pause
)
