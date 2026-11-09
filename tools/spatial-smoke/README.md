# Device smoke test without AndroidX Test downloads

Uses Android's framework Instrumentation and a separate package, without AndroidX
Test downloads. Run from the repository root with JDK 17 and Android SDK configured.
The public-fixture and plan CRUD runners leave the main app's database untouched.
The optional stored-reference runner reads existing plans on-device only.

```powershell
.\gradlew.bat --offline --no-daemon -I tools/spatial-smoke/check.init.gradle :app:assembleDebug
adb install -r build/spatial-smoke/outputs/apk/debug/app-debug.apk
adb shell am instrument -w com.example.facedesignstudio.spatialsmoke/com.example.facedesignstudio.SpatialSmokeRunner
```

Success is `INSTRUMENTATION_CODE: -1`, with no `failure` entry. A successful build
alone does not confirm the runtime test. The runner checks reference reconstruction,
ten real model inferences, confirmation and loss, known synthetic rotations of the
estimated mesh, and two-face rejection. Timings separate inference and geometry.
Synthetic rotations test math, not detection of a real turning head or skin accuracy.
Keep the test package installed until the desired runners below have finished.
With multiple devices, add `-s <device-serial>` to each adb command.

## Plan editing and deletion regression

`PlanCrudSmokeRunner` uses an isolated in-memory Room database and synthetic
patients. It checks byte-exact loading of a valid JPEG larger than 4 MiB,
ViewModel loading/editing/versioned saving, independent snapshot deletion,
shared-photo retention, orphan cleanup, preservation of other patients/plans,
and no editor navigation after a failed load. It never changes the main database.

```powershell
adb shell am instrument -w com.example.facedesignstudio.spatialsmoke/com.example.facedesignstudio.PlanCrudSmokeRunner
```

## Optional saved-plan compatibility check

Only run when debugging the device owner's saved plans is authorized. Install the
new main APK first, then run the separately registered instrumentation below. It
opens the main database read-only on the device and checks up to three recent
references. It checks all three rather than stopping at the first unsupported
plan, and tests transfer to a synthetic neutral pose. A still reference may exceed
the narrower live pitch limit, so identity can legitimately return POSE_OUT_OF_RANGE
while reconstruction and neutral projection both pass. Output contains only
sequence numbers, estimated angles and success/failure reasons;
photos, names and coordinates are never copied or exported. This does not assess
skin accuracy or movement of a real head. Both APKs must have the same debug signing key.

```powershell
adb shell am instrument -w com.example.facedesignstudio.spatialsmoke/com.example.facedesignstudio.StoredReferenceSmokeRunner
```

## Cleanup and restore the production build

The init script changes the application ID and output directory. Never distribute
its APK as the main app. Alternating build configurations can remove earlier task
outputs; rebuild the production APK after completing the checks.

```powershell
adb uninstall com.example.facedesignstudio.spatialsmoke
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.facedesignstudio/com.example.facedesignstudio.MainActivity
```

The uninstall above targets only the disposable test package. Do not uninstall or
clear data for `com.example.facedesignstudio`.

## Recorded results — 2026-11-09

- JVM suite: 61 tests, no failures/errors; production Debug build passed.
- `SpatialSmokeRunner`: reference reconstruction, ten model frames, confirmation,
  loss, synthetic yaw ±20 degrees and two-face rejection passed on SM-A505F.
- `PlanCrudSmokeRunner`: all reported large-photo, editing, deletion and failed-load
  scenarios passed on SM-A505F with an isolated in-memory Room database.
- `StoredReferenceSmokeRunner`: three recent saved references reconstructed and
  projected to a synthetic neutral pose. Two tilted references correctly exceeded
  the narrower live pitch limit when projected back to their original pose.

These checks do not validate physical marking accuracy, real head rotation,
touch interaction, accessibility, or the full UI lifecycle.
