@echo off
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle is not installed. Install Gradle 8.11.1 or open this project in Android Studio.
exit /b 1
