@echo off
setlocal
cd /d "%~dp0"
set "task_javac=javac"
set "task_jar=jar"
if defined JAVA_HOME set "task_javac=%JAVA_HOME%\bin\javac.exe"
if defined JAVA_HOME set "task_jar=%JAVA_HOME%\bin\jar.exe"
set "task_build=%TEMP%\placement-build-%RANDOM%-%RANDOM%"
mkdir "%task_build%"
"%task_javac%" -encoding UTF-8 --release 17 -d "%task_build%" src\optimizer\*.java
if errorlevel 1 goto failure
"%task_jar%" --create --file ServerPlacementLab.jar --main-class optimizer.Main -C "%task_build%" optimizer
if errorlevel 1 goto failure
rmdir /s /q "%task_build%"
echo Built ServerPlacementLab.jar (Java 17+)
exit /b 0
:failure
rmdir /s /q "%task_build%"
exit /b 1
