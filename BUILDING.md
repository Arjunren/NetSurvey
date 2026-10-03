# Building and signing

## Toolchain

- Android Gradle Plugin 9.1.1
- Gradle 9.3.1
- Kotlin 2.3.20
- Compose BOM 2026.09.00
- Room 2.8.5
- JDK 17+
- compile/target SDK 37
- minimum SDK 26

Open the repository root in Android Studio, allow Gradle sync, and install any requested SDK package through SDK Manager.

## Commands

```powershell
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Release

The repository contains a release build type with code minification and resource shrinking, but no embedded signing credentials. Use Android Studio **Build → Generate Signed Bundle / APK**, or add a local, untracked `keystore.properties` and signing configuration.

Generate a private keystore outside the repository:

```powershell
keytool -genkeypair -v -keystore C:\secure\netsurvey-release.jks -alias netsurvey -keyalg RSA -keysize 4096 -validity 10000
```

Do not commit the keystore, passwords, `keystore.properties`, APK, or AAB. Back up signing material securely; Android updates must use the same key.

## Install

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Android builds enforcing developer verification, the signing identity and installation workflow may require the current Android developer-verification registration process.
