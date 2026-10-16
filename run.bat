@echo off
setlocal
if not exist out mkdir out
if exist sources.txt del sources.txt
for /r src\main\java %%f in (*.java) do echo "%%f">>sources.txt
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 (
  echo Compilation failed. Install JDK 17 or later.
  exit /b 1
)
java -cp out greenhouse.app.Main
