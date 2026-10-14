@echo off
setlocal enabledelayedexpansion
rem CyberShield launcher (Windows). Usage: start.bat [port]
cd /d "%~dp0"

rem Auto-detect JDK 21+ if not in PATH or if default java is old
where javac >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
  if exist "%LOCALAPPDATA%\Programs\STM32CubeMX\jre\bin\javac.exe" (
    set "PATH=%LOCALAPPDATA%\Programs\STM32CubeMX\jre\bin;!PATH!"
  ) else if exist "C:\Program Files\JetBrains\PyCharm 2026.1.1\jbr\bin\javac.exe" (
    set "PATH=C:\Program Files\JetBrains\PyCharm 2026.1.1\jbr\bin;!PATH!"
  ) else if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    set "PATH=%JAVA_HOME%\bin;!PATH!"
  )
) else (
  rem If default java is old (e.g. Java 8), prioritize detected JDK 21+
  java -version 2>&1 | findstr "1.8." >nul
  if !ERRORLEVEL!==0 (
    if exist "%LOCALAPPDATA%\Programs\STM32CubeMX\jre\bin\java.exe" (
      set "PATH=%LOCALAPPDATA%\Programs\STM32CubeMX\jre\bin;!PATH!"
    ) else if exist "C:\Program Files\JetBrains\PyCharm 2026.1.1\jbr\bin\java.exe" (
      set "PATH=C:\Program Files\JetBrains\PyCharm 2026.1.1\jbr\bin;!PATH!"
    )
  )
)

set PORT=%1
if "%PORT%"=="" (
  rem Check if port 8080 is in use
  netstat -ano | findstr /R /C:":8080 .*LISTENING" >nul
  if !ERRORLEVEL!==0 (
    echo [INFO] Port 8080 is already in use. Using port 8081.
    set PORT=8081
  ) else (
    set PORT=8080
  )
)

echo Starting CyberShield on port %PORT%...
where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
  echo ^>^> Building with Maven...
  call mvn -q package || exit /b 1
  start http://localhost:%PORT%
  java -jar target\cybershield.jar %PORT%
) else (
  if exist target\classes\com\cybershield\CyberShieldApp.class (
    echo ^>^> Using existing compiled classes in target\classes
    start http://localhost:%PORT%
    java -cp target\classes com.cybershield.CyberShieldApp %PORT%
  ) else (
    echo ^>^> Compiling with javac...
    if exist build rmdir /s /q build
    mkdir build\classes
    dir /s /b src\main\java\*.java > build\sources.txt
    javac -encoding UTF-8 --release 21 -d build\classes @build\sources.txt || exit /b 1
    xcopy /e /i /q dashboard build\classes\dashboard >nul
    xcopy /e /i /q datasets build\classes\datasets >nul
    start http://localhost:%PORT%
    java -cp build\classes com.cybershield.CyberShieldApp %PORT%
  )
)

