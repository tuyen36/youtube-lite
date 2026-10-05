@rem Gradle wrapper toi gian cho Windows (LUNA36 chua cai Gradle).
@rem Cach dung sau khi cai Android Studio: mo project bang Studio de no tu sinh wrapper chuan.
@rem Con khong, cai Gradle 8.7 roi chay: gradle :app:assembleDebug
@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
  echo Chua co Gradle. Cai Android Studio Ladybug+ roi mo thu muc youtube-lite bang Studio.
  echo Hoac cai Gradle 8.7: winget install Gradle.Gradle
  exit /b 1
)
gradle %*
