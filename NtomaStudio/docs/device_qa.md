# DeepLabV3 person-segmentation device QA

`app/src/androidTest/java/com/ntoma/studio/data/ml/PersonSegmenterDeviceTest.kt`
verifies on real-device hardware what Robolectric cannot: that the bundled
DeepLabV3 model loads on the device's delegate (NNAPI/GPU/CPU fallback),
segments a synthetic person photo, and returns a plausible foreground ratio.

It cannot run in the sandbox (no KVM → no emulator). Run it once per device
model you intend to support.

## From a dev machine with the phone connected

```bash
adb devices   # confirm the phone shows up (USB debugging enabled)

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ntoma.studio.data.ml.PersonSegmenterDeviceTest
```

## Without Gradle (APKs only)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell am instrument -w \
  -e class com.ntoma.studio.data.ml.PersonSegmenterDeviceTest \
  com.ntoma.studio.test/androidx.test.runner.AndroidJUnitRunner
```

Both test APKs must be built first: `./gradlew :app:assembleDebug
:app:assembleDebugAndroidTest` (compilation of the test APK is verified in
this repo's history).

## Reading results

- `INSTRUMENTATION_STATUS_CODE: 0` per test + `INSTRUMENTATION_RESULT_CODE: -1`
  overall = pass.
- A load failure on NNAPI means the model fell back — check logcat for
  `PersonSegmenter` delegate lines; CPU fallback is still a pass, just slower.

## In CI

GitHub Actions' `reactivecircus/android-emulator-action` can run this job on
macOS runners (KVM absent on Linux runners). Keep it opt-in — it is slow and
flaky by nature; unit tests gate every push, device QA gates releases.
