@if "%DEBUG%" == "" @echo off
@rem Gradle wrapper bootstrap. Android Studio will complete gradle-wrapper.jar if missing.
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_HOME=%DIRNAME%
set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
if exist "%CLASSPATH%" (
  "%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
  goto end
)
echo gradle-wrapper.jar is missing.
echo Open this folder in Android Studio and let it generate the Gradle wrapper,
echo or run: gradle wrapper --gradle-version 4.10.3
exit /b 1
:end
