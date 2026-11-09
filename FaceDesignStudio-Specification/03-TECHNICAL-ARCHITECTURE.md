# FaceDesign Studio — معماری واقعی پیاده‌سازی

این سند معماری موجود در checkout فعلی را ثبت می‌کند؛ بخش‌هایی که هنوز abstraction پیشنهادی هستند در انتها آمده‌اند.

## 1. Stack

- Kotlin/JVM 17
- Android SDK 36، minSdk 24، targetSdk 36
- Jetpack Compose و Material 3
- CameraX برای دوربین داخل برنامه
- Android Photo Picker برای Gallery
- ViewModel و StateFlow
- Hilt برای dependency injection
- Room روی SQLite محلی
- Coroutines
- MediaPipe Tasks Vision `0.10.35`
- `org.json` برای snapshotهای Landmark، Node و transform

## 2. لایه‌های عملی

```text
FaceDesignApp (Compose screens + Canvas + CameraX)
        ↓
PlannerViewModel (StateFlow, editor operations, analysis flow)
        ↓
PlannerRepository (Room transactions)
        ↓
Room / SQLite
```

منطق تشخیص و هندسه در domain اجرا می‌شود:

```text
FaceDetectionEngine
HairlineGeometry
CoordinateTransformer
ImageDecoder
```

در نسخهٔ فعلی ViewModel مستقیماً از `FaceDetectionEngine` concrete استفاده می‌کند و interface مستقل `FaceLandmarkDetector` وجود ندارد.

## 3. Packageها و فایل‌های کلیدی

| فایل | مسئولیت |
|---|---|
| `domain/FaceDetectionEngine.kt` | MediaPipe detector/landmarker، crop، Landmark و frontal score |
| `domain/HairlineGeometry.kt` | خط زیر قرنیه، محور عمود بر آن از midpoint قرنیه، پیشنهاد خط، Bézier و mirror |
| `domain/CoordinateTransformer.kt` | Image/Canvas/Gesture transform |
| `ui/PlannerViewModel.kt` | state، تحلیل، ویرایش، history، Save/Load |
| `ui/FaceDesignApp.kt` | navigation سادهٔ داخلی، فرم‌ها، CameraX، Editor Canvas |
| `ui/StudioComponents.kt` | عنوان، دکمه، آیکون خطی، مراحل، toggle و پنل پایین مشترک |
| `MainActivity.kt` | تم روشن، رنگ‌ها، تایپوگرافی و تنظیم system barها |
| `data/PlannerRepository.kt` | ذخیرهٔ بیمار و تراکنش Photo + Plan snapshot |
| `data/local/PlannerEntities.kt` | Room entityها |
| `ui/PlanImageExporter.kt` | PNG/تصویر خروجی |
| `domain/LiveFaceTracker.kt` | مدل IMAGE برای مرجع و VIDEO برای فریم زنده، خروجی نقاط سه‌بعدی |
| `domain/SpatialFaceAlignment.kt` | ساخت سطح تقریبی پیشانی، اتصال منحنی و تطبیق سه‌بعدی |
| `domain/LiveAlignment.kt` | قرارداد نتیجه و نمایش راهنما، دروازهٔ پایداری/تأیید و تطبیق دوبعدی قدیمی |
| `domain/LiveFrameDisplay.kt` | حفظ تصویر آخر و تشخیص انتظار برای فریم تازه |
| `ui/LivePlanScreen.kt` | جلسهٔ دوربین، رسم هم‌زمان تصویر و خط، خطاهای مشخص مرجع |

## 4. State و Navigation

`FaceDesignApp` صفحه‌های زیر را در یک `mutableStateListOf<PlannerScreen>` با `rememberSaveable` و Saver سفارشی مدیریت می‌کند:

`HOME`, `PATIENT_FORM`, `PATIENT_DETAIL`, `PHOTO_SOURCE`, `CAMERA`, `PHOTO_REVIEW`, `EDITOR`, `LIVE_PLAN`, `SETTINGS`.

`PlannerUiState` شامل بیمار، عکس pending، تحلیل، rejection، Nodeها، transform، ظاهر خط، انتخاب Node، تقارن، profile، history flags و وضعیت Save است.

پس از `DetectionOutcome.Accepted`، UI مستقیم از Photo Review به Editor می‌رود.

فرم بیمار مقادیر خود را با `rememberSaveable` حفظ می‌کند. پشتهٔ صفحات در بازسازی Activity بازیابی می‌شود، اما عکس و تحلیل موقت فقط در ViewModel هستند؛ پس از مرگ فرایند، مسیر وابسته به دادهٔ ازدست‌رفته به HOME برمی‌گردد. بازگشت از ویرایشگرِ ذخیره‌نشده تأیید می‌خواهد و خروج، مسیر موقت بررسی/انتخاب عکس را از پشته پاک می‌کند. تعویض بیمار ابتدا انتخاب و طرح‌های قبلی را پاک می‌کند تا دادهٔ بیمار قبلی نمایش داده نشود.

## 5. تشخیص

`FaceDetectionEngine.analyze` روی `Dispatchers.Default` از ViewModel اجرا می‌شود. Face Detector همهٔ detectionها را برمی‌گرداند، بیشترین score انتخاب می‌شود و Face Landmarker با `numFaces = 1` روی crop چهرهٔ انتخاب‌شده اجرا می‌شود. عکس از دستگاه خارج نمی‌شود.

مدل‌ها در `app/src/main/assets` باندل هستند. `ImageDecoder` EXIF rotation را اعمال می‌کند و برای inference تصویر بزرگ را downsample می‌کند؛ bytes اصلی باقی می‌ماند.

## 6. ذخیره‌سازی

Entityها:

- `PatientEntity` در `patients`
- `PhotoAssetEntity` در `photo_assets`
- `HairlinePlanVersionEntity` در `hairline_plan_versions`
- `AppSettingsEntity` در `app_settings`

`PhotoAssetEntity.originalBytes` ستون BLOB است. `PlannerRepository.savePhotoAndPlan` درج عکس و snapshot را در `database.withTransaction` انجام می‌دهد. برای هر Save، `planId` حفظ و `version` افزایش می‌یابد. Foreign keyها برای Patient و Photo با cascade حذف می‌شوند.

ویرایش بیمار با `PatientDao.update` در فایل `PlannerDao.kt` و `@Update` انجام می‌شود؛ تعارض کد یکتا ABORT می‌شود. جایگزینی ردیف بیمار با REPLACE مجاز نیست، چون حذف ضمنی آن می‌تواند cascade داده‌های وابسته را فعال کند. هر Save طرح، یک ردیف عکس تازه نیز می‌سازد؛ اشتراک‌گذاری BLOB میان نسخه‌ها پیاده‌سازی نشده است. schema همچنان نسخهٔ ۲ است.

`activePlanThresholdPercent` مقدار آستانهٔ زمان پذیرش یا بارگذاری طرح را جدا از تنظیم عمومی نگه می‌دارد. `saveThreshold` طرح، تحلیل و history جاری را پاک نمی‌کند.

`PlannerRepository.getPhoto` در یک تراکنش metadata بدون BLOB و سپس قطعه‌های ۱۲۸ کیلوبایتی با `substr` را می‌خواند؛ عکس کامل از بایت‌های دست‌نخورده بازسازی می‌شود. این مسیر در `loadPlan` استفاده می‌شود و callback ورود به Editor فقط پس از بارگذاری موفق فراخوانی می‌شود.

`deletePlanSnapshot` در یک تراکنش snapshot را حذف و سپس با شرط `NOT EXISTS` عکسِ بدون ارجاع را پاک می‌کند. ViewModel در زمان عملیات `busy` است و اگر نسخهٔ جاری حذف شود، عکس و وضعیت ویرایشگر را پاک می‌کند. نسخه‌های دیگر و رکورد بیمار تغییر نمی‌کنند.

## 7. هندسه و Editor

تمام Node و Landmarkهای هندسی normalized هستند. تراز، zoom، pan و offset محور در `EditorTransform` نگهداری می‌شوند و عکس اصلی تغییر نمی‌کند.

Canvas ترتیب زیر را دارد:

1. تصویر با transform
2. خط رویش با سایه
3. راهنماها و Landmarkها، در صورت روشن‌بودن
4. Nodeها و فقط handleهای فعال Node انتخاب‌شده
5. حالت انتخاب

Pinch دو انگشتی zoom، drag دو انگشتی pan و double tap بازنشانی zoom/pan را انجام می‌دهد؛ چرخش و offset محور با double tap تغییر نمی‌کنند. دکمهٔ zoom ثابت ۱۰۰/۲۰۰/۴۰۰ در UI فعلی وجود ندارد.

یک `awaitEachGesture` انتخاب، درگ، pinch/pan و double tap را هماهنگ می‌کند. هدف در لحظهٔ لمس از Nodeها و handleهای قابل مشاهده انتخاب می‌شود؛ در فضای خالی، Node انتخاب‌شده هدف درگ است. پس از عبور از touch slop، `translateByCanvasDelta` بردار حرکت را با چرخش معکوس، scale و ابعاد viewport به مختصات تصویر تبدیل می‌کند. موقعیت تازه از state جاری ViewModel خوانده می‌شود. ورود انگشت دوم تا پایان همان gesture، درگ Node را متوقف می‌کند؛ لمس خالی انتخاب را پاک نمی‌کند.

درج نقطه به `HairlineGeometry.insertNodes` واگذار شده است: تقسیم de Casteljau در یک segment مستقل یا دو segment متناظر، با حفظ شکل منحنی. ViewModel سقف ۱۲ Node را پیش از ثبت history کنترل می‌کند.

## 8. History

ViewModel قبل از تغییر snapshot از Nodeها، transform، ظاهر خط و تقارن در stack محدود ۴۰تایی نگه می‌دارد. این کد command interface جداگانه ندارد. تغییرات Node، Handle، افزودن/حذف، rotation و ظاهر خط history دارند. Canvas در هر gesture پیوسته، از جمله zoom/pan، فقط یک بار `beginEditorChange` را فراخوانی می‌کند؛ حرکت‌های بعدی همان gesture state را به‌روزرسانی می‌کنند. آغاز تغییر و Undo/Redo، `saveComplete` را false می‌کنند. Undo/Redo انتخاب Node را پاک می‌کند؛ بارگذاری طرح ذخیره‌شده با `saveComplete = true` انجام می‌شود.

## 9. Export

`PlanImageExporter` تصویر annotated یا clean تولید می‌کند. خروجی‌ها شامل transform و خط رویش فعلی هستند. عکس اصلی در DB overwrite نمی‌شود.

رندر و نوشتن خروجی در `Dispatchers.IO` انجام می‌شود. نام فایل شمارهٔ `savedPlanVersion` را استفاده می‌کند؛ برای طرحی که هنوز ذخیره نشده، مقدار پیش‌فرض ۱ است. تولید خروجی به ورود به پیش‌نمایش وابسته نیست.

## 10. آزمون‌های موجود

در `app/src/test/java/com/example/facedesignstudio/domain`، کلاس `HairlineNodeInsertionTest` هشت تست درج و حفظ منحنی و کلاس `CoordinateTransformerDragTest` شش تست حرکت نسبی دارد. کلاس‌های `LiveAlignmentTest`، `LiveCalibrationGateTest` و `LiveOverlayModeTest` به‌ترتیب ۱۱، ۱۲ و ۷ تست دارند؛ LiveFrameDisplayTest نیز ۳ تست تداوم تصویر و تازگی راهنما دارد و SpatialFaceAlignmentTest دارای ۱۳ تست ردیابی سه‌بعدی و سطح پیشانی است. همراه با یک تست نمونه، ۶۱ تست واحد پاس شده‌اند. این پوشش شامل dispatch واقعی gestureهای Compose، Room یا چرخهٔ عمر ViewModel نیست؛ جزئیات و چک‌لیست پذیرش در سند 05 است.

آزمون دستگاهی `PlanCrudSmokeRunner` جدا از تست‌های JVM، Repository/Room و مسیر load/edit/save/delete در ViewModel را با دیتابیس موقت می‌سنجد. آزمون عکس بیش از ۴ MiB و حفظ نسخه‌ها/عکس مشترک روی گوشی موفق بوده است. این پوشش محدود جای تست جامع چرخهٔ عمر یا gesture واقعی را نمی‌گیرد؛ روش اجرا در [ابزار تست گوشی](../tools/spatial-smoke/README.md) آمده است.

## 11. موارد خارج از معماری فعلی

- Use-case layer مستقل وجود ندارد.
- interface مستقل برای detector وجود ندارد.
- Kotlin Serialization استفاده نشده است؛ snapshotها JSON با `org.json` هستند.
- API برای cloud، backup، sync یا رمزگذاری اضافه وجود ندارد.
- تست‌های جامع unit/instrumentation هنوز نوشته نشده‌اند.

## 12. دوربین زندهٔ طرح

`LivePlanScreen` یک analyzer اختصاصی CameraX و `LiveFaceTracker` مستقل با حالت VIDEO دارد. `SpatialFaceAlignment` منحنی را روی سطح تقریبی پیشانی قرار می‌دهد و با تبدیل تشابه سه‌بعدی به فریم منتقل می‌کند؛ عکس مرجع ابتدای جلسه در حالت IMAGE بازتحلیل می‌شود؛ `LiveCalibrationGate` پایداری و تأیید جلسه را مدیریت می‌کند. فریم و خط با هم رسم می‌شوند و به state طرح یا Room نوشته نمی‌شوند. چرخهٔ عمر، تبدیل جهت و قرینگی، کنترل کیفیت و محدودیت‌ها در [سند 10](10-LIVE-PLAN-GUIDE.md) آمده‌اند.
