# Building Ntoma Studio

## Prerequisites (sandbox)
The toolchain lives outside the workspace (not persisted):

```bash
bash /home/user/tools/setup_toolchain.sh   # JDK 17 + Gradle 8.9 + Android SDK 34 into /var/tmp, plus a 4 GB swapfile
```

The sandbox has 2 vCPU / 2 GB RAM. The 4 GB swapfile created by the script is **required** —
the Kotlin/Compose compile otherwise OOM-kills the Gradle daemon. `gradle.properties` is tuned
accordingly (1.6 GB heap, in-process Kotlin compiler, workers.max=2).

## Build & test

```bash
export JAVA_HOME=/var/tmp/tools/jdk17 ANDROID_HOME=/var/tmp/android-sdk GRADLE_USER_HOME=/var/tmp/gradle-home
cd /home/user/NtomaStudio
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # JVM unit tests (Robolectric; first run downloads android-all)
./gradlew :app:assembleRelease        # minified APK signed with app/demo-release.keystore (demo key!)
./gradlew :app:bundleRelease          # Play Console artifact (.aab) — see docs/PLAY_DATA_SAFETY.md
```

Outputs:
- `app/build/outputs/apk/debug/app-debug.apk` (~21 MB)
- `app/build/outputs/apk/release/app-release.apk` (~2.6 MB, R8-minified)
- `app/build/outputs/bundle/release/app-release.aab` (~4.4 MB, the Play-uploadable bundle)
- Convenience copies at the repo root: `Ntoma-debug.apk`, `Ntoma-release.apk`, `Ntoma-release.aab`

## Verified status
- `assembleDebug` / `assembleRelease`: **BUILD SUCCESSFUL** (only two unchecked-cast warnings remain).
- `testDebugUnitTest`: **14/14 passing** — ColorPatternAnalyzer (solid/striped/checked classification,
  confidence caps), RecommendationEngine (ordering, score band, reasons, tradition bonus, gender filter),
  EntitlementRepository (free-tier daily/monthly quotas, rewarded bonus, premium bypass).
- No emulator in this environment (`/dev/kvm` absent), so instrumented/androidTest suites and the
  end-to-end UI journey were **not** executed on a device here.

## Release signing
`app/demo-release.keystore` is a throwaway demo key (storepass/keypass `ntomademo`).
Replace `signingConfigs.release` in `app/build.gradle.kts` before any real store upload.
