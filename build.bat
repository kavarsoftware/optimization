@echo off
setlocal
cd /d "%~dp0"
set "task_javac=javac"
set "task_jar=jar"
if defined JAVA_HOME set "task_javac=%JAVA_HOME%\bin\javac.exe"
if defined JAVA_HOME set "task_jar=%JAVA_HOME%\bin\jar.exe"
if not exist build mkdir build
(for %%f in (src\optimizer\*.java) do @echo "%%f") > build\sources.txt
"%task_javac%" -encoding UTF-8 --release 17 -d build @build\sources.txt
if errorlevel 1 exit /b 1
"%task_jar%" --create --file ServerPlacementLab.jar --main-class optimizer.Main -C build optimizer
if errorlevel 1 exit /b 1
echo Built ServerPlacementLab.jar (Java 17+)
