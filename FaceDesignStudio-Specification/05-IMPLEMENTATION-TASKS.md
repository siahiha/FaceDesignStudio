# FaceDesign Studio — Implementation Tasks

این سند برای یک AI Coding Agent یا برنامه‌نویس است.

## Phase 1 — Project Setup

- [x] ایجاد Android Studio project
- [x] Kotlin
- [x] Jetpack Compose
- [x] Material 3
- [x] Hilt
- [x] SQLite database using Room as Android access layer
- [x] CameraX
- [x] Coroutines
- [x] Serialization (snapshotهای JSON با `org.json`؛ نه Kotlin Serialization)
- [x] ML runtime
- [x] ساخت package structure پایه

## Phase 2 — Patient

- [x] Patient entity
- [x] Patient DAO
- [x] Patient repository
- [x] Patient list
- [x] Search
- [x] Add patient
- [x] Edit patient
- [x] Patient detail
- [x] ویرایش بیمار با UPDATE و حفظ عکس‌ها/نسخه‌های وابسته

Acceptance:
کاربر بتواند بیمار بسازد، ذخیره کند و دوباره باز کند.

## Phase 3 — Photo

- [x] Camera permission
- [x] CameraX preview
- [x] Capture
- [x] Gallery picker
- [x] Image preview
- [x] Image validation
- [x] Store original image bytes as a BLOB in a separate SQLite PhotoAsset row
- [x] Store MIME type, dimensions, source, capture time, and patient relation

Acceptance:
کاربر بتواند با دوربین داخل اپ عکس بگیرد یا از Gallery انتخاب کند. عکس اصلی به‌صورت BLOB در SQLite ذخیره شود. در نسخهٔ فعلی امتیاز Face Detector باید به threshold برسد و Landmark ضروریِ ناموجود باعث رد شود؛ confidence هر Landmark فعلاً gate نیست. عکس مردود وارد Editor نشود و علت رد/امکان گرفتن دوباره نمایش داده شود.

## Phase 4 — Landmark Engine

- [ ] FaceLandmarkDetector interface (current code calls the concrete engine from ViewModel)
- [x] ML implementation
- [x] Model loading
- [x] Preprocessing
- [x] Inference
- [x] Output decoding
- [x] Confidence validation
- [x] Configurable minimum confidence in app settings
- [x] Require required landmarks; gate current version on face detector confidence only
- [ ] Enable equal-weight arithmetic mean when the selected model returns real per-landmark confidence
- [x] Landmark overlay

Acceptance:
امتیاز چهره و Landmarkها در مختصات درست نمایش داده شوند؛ مقدار threshold و نتیجهٔ تحلیل برای عکس‌های پذیرفته‌شده ذخیره شود. تا انتخاب مدل پشتیبان، امتیاز چهره تنها confidence gate است.

## Phase 5 — Alignment

- [x] eye reference line
- [x] angle calculation
- [x] auto level
- [x] manual rotation
- [x] reset transform

Acceptance:
خط زیر قرنیه بعد از Auto Level افقی باشد.

## Phase 6 — Midline

- [x] calculate eye midpoint
- [x] calculate axis through midpoint of cornea-center points perpendicular to lower-iris line
- [x] apply midline offset
- [x] draw midline
- [x] auto level axis to vertical

## Phase 7 — Initial Hairline

ابتدا بدون AI پیچیده.

- [x] profile-dependent initial points (base ratios in geometry; custom rise is UI-controlled)
- [x] left frontal
- [x] right frontal
- [x] left temporal
- [x] right temporal
- [x] initial handles
- [x] standard four-node open path without center node
- [x] Current/Conservative/Lower four-node Bézier presets with temple hooks

Acceptance:
با هر عکس یک خط قابل ویرایش تولید شود.

## Phase 8 — Bezier Editor

- [x] Custom Canvas
- [x] normalized coordinates
- [x] coordinate transformer
- [x] node hit testing
- [x] handle hit testing
- [x] point drag
- [x] handle drag
- [x] add point
- [x] افزودن دو نقطهٔ متمایز در segmentهای متناظر با تقارن فعال و حفظ شکل منحنی
- [x] حرکت نسبی Node انتخاب‌شده با درگ هر جای کادر، با لحاظ zoom و rotation
- [x] حفظ انتخاب با لمس خالی و لغو انتخاب از پنل نقطه‌ها
- [x] توقف درگ Node پس از ورود انگشت دوم تا برداشتن همهٔ انگشت‌ها
- [x] remove point
- [x] balanced handles
- [x] symmetry
- [x] selection
- [x] draw nodes and only the selected node’s active handles

Acceptance:
کاربر بتواند خط را دقیقاً مانند یک Bezier editor ویرایش کند.

## Phase 9 — Zoom/Pan

- [x] pinch zoom
- [x] two-finger pan
- [x] double tap (reset zoom/pan)
- [x] remove fixed 100/200/400 buttons

Acceptance:
Zoom نباید باعث جابجایی Hairline نسبت به صورت شود.

## Phase 10 — Undo/Redo

- [ ] command interface (current implementation uses bounded state snapshots)
- [x] move point
- [x] move handle
- [x] add point
- [x] remove point
- [ ] settings
- [x] rotation
- [x] undo
- [x] redo

## Phase 11 — Hairline Recommendation

- [ ] facial measurements beyond eye-distance/temple ratios
- [x] facial proportion mode selector
- [ ] four finger mode (product definition requested)
- [x] manual mode selector
- [x] سه کارت CURRENT / CONSERVATIVE / LOWER و ارتفاع CUSTOM در پنل تنظیمات بیشتر؛ سازگاری MODERATE/NATURAL برای داده‌های قدیمی
- [ ] warning engine beyond non-frontal warning

## Phase 12 — Preview

- [x] hide editor controls in preview
- [x] hide references in clean preview
- [ ] show existing hairline
- [x] show proposed hairline
- [x] before/proposed comparison (toggle between original photo and proposed overlay)
- [x] guide visibility toggle in editor

## Phase 13 — Save Plan

- [x] HairlinePlan entity
- [x] serialize points
- [x] save transform
- [x] save landmarks
- [x] save algorithm version
- [x] save model id/version and threshold; average Landmark confidence is unavailable in current model
- [x] save editor/recommendation settings snapshot
- [x] save photo reference to SQLite BLOB row
- [x] save app confidence threshold
- [x] حفظ آستانهٔ زمان تحلیل مستقل از تنظیم عمومی و جلوگیری از پاک‌شدن طرح با تغییر تنظیم
- [x] load plan
- [x] edit existing plan
- [x] create an immutable version snapshot on each save

## Phase 14 — Export

- [x] annotated PNG/image file
- [x] clean preview image
- [x] patient information
- [x] plan date
- [x] plan version

## Phase 15 — Tests

- [x] ۸ تست `HairlineNodeInsertionTest`: درج جفت متمایز، حفظ انتخاب هر سمت، حفظ شکل Bézier، درج تکراری تا ۱۲ نقطه، محور کج و تصویر عمودی، مسیر فرد قدیمی، حالت مستقل و مسیر دو Node
- [x] ۶ تست `CoordinateTransformerDragTest`: حرکت صفر، برابری جابه‌جایی صفحه در scale/rotationهای مختلف، استقلال از مبدأ لمس، حرکت چندمرحله‌ای، letterboxing و استقلال از pan
- [ ] پوشش جامع سایر توابع هندسه، تقارن و تبدیل رفت‌وبرگشت مختصات
- [ ] تست رویدادهای واقعی gesture در Compose، از جمله touch slop، لغو رویداد و تعویض یک/دو انگشت
- [ ] undo/redo tests
- [ ] پوشش جامع database و migration
- [ ] پوشش جامع ViewModel و چرخهٔ عمر
- [x] رگرسیون محدود Room/ViewModel روی گوشی با دیتابیس موقت: بازکردن عکس بیش از ۴ مگابایت، ویرایش و ذخیرهٔ نسخهٔ تازه، حفظ نسخهٔ قبلی، حذف مستقل، حفظ عکس مشترک/طرح‌های دیگر، پاک‌کردن عکس بی‌استفاده و عدم ورود به ویرایشگر در بارگذاری ناموفق
- [ ] confidence mean/pass/reject tests
- [x] درج، خواندن قطعه‌ای و حذف BLOB در رگرسیون Room؛ محدودیت فضا و ورودی خراب هنوز آزمون ندارند

هفت مجموعهٔ هندسی و ردیابی همراه با `ExampleUnitTest` مجموعاً **۶۱ تست واحد موفق** دارند؛ تست نمونه فقط صحت `2 + 2 = 4` را می‌سنجد و پوشش قابلیت محسوب نمی‌شود. گزارش‌ها در `app/build/test-results/testDebugUnitTest/TEST-*.xml` تولید می‌شوند. آزمون جداگانهٔ `PlanCrudSmokeRunner` در ابزار [spatial-smoke](../tools/spatial-smoke/README.md) روی Room و ViewModel واقعی با دادهٔ موقت اجرا شده است؛ جزو این ۶۱ تست JVM نیست. آزمون قدیمی SQLite برای UPDATE نیز جدا از پوشش Room است.

## بازطراحی رابط موبایل — ۲۰۲۶/۱۱/۰۸

- [x] تم روشن بنفش، کارت‌ها و آیکون‌های خطی مشترک در `StudioComponents.kt`
- [x] فهرست بیمار با جست‌وجو و دکمهٔ ثابت ثبت؛ فرم کوتاه با اطلاعات تکمیلی بازشونده
- [x] سه کارت الگوی اولیه و انتقال تنظیمات جزئی به پنل پایین
- [x] عکس بزرگ‌تر و سه پنل نقطه‌ها، ظاهر خط و تراز تصویر
- [x] راهنماها پیش‌فرض خاموش؛ فقط دستگیره‌های فعال نقطهٔ انتخاب‌شده
- [x] ذخیرهٔ مستقیم و پیش‌نمایش اختیاری؛ پنل خروجی تصویر تمیز/نشانه‌دار
- [x] حفظ پشته و فرم در بازسازی Activity، مسیر امن پس از مرگ فرایند و هشدار خروج ذخیره‌نشده
- [x] پاک‌کردن انتخاب پس از Undo/Redo و به‌روزرسانی وضعیت ذخیره پس از تغییر
- [x] نوشتن خروجی در IO و اصلاح شمارهٔ نسخه در نام فایل
- [x] ساخت و نصب نسخهٔ اصلی روی گوشی با حفظ داده‌ها و اجرای Activity
- [ ] بررسی بصری و پذیرش کامل تعامل روی گوشی

### چک‌لیست پذیرش دستی باقی‌مانده

- [ ] فرم در صفحهٔ کوچک، صفحه‌کلید باز، RTL و اندازهٔ فونت بزرگ قابل استفاده باشد.
- [ ] افزودن نقطه در حالت قرینه، دو نقطهٔ جدا در دو سمت بسازد و شکل خط تغییر نکند؛ انتخاب سمت راست و چپ بررسی شود.
- [ ] بعد از انتخاب Node، درگ فضای خالی بدون پرش و در جهت انگشت عمل کند؛ در zoomهای ۱ تا ۴ و چرخش‌های مختلف تکرار شود.
- [ ] لمس Node دیگر انتخاب را عوض کند؛ درگ handle فقط handle را هدف بگیرد؛ لمس خالی انتخاب را نگه دارد و لغو انتخاب از پنل عمل کند.
- [ ] ورود انگشت دوم درگ Node را متوقف کند؛ پس از برداشتن یک انگشت تا پایان gesture نقطه ناخواسته حرکت نکند؛ pinch/pan و double tap بررسی شوند.
- [ ] Undo/Redo کل درگ پیوسته را یک مرحله برگرداند؛ خروج از طرح ذخیره‌نشده هشدار بدهد.
- [ ] ویرایش بیمار دارای عکس و چند نسخه، داده‌های وابسته را حفظ کند؛ کد تکراری داده‌ای را حذف نکند.
- [ ] تغییر تنظیم تشخیص طرح جاری را حفظ کند؛ ذخیره و بازکردن مجدد آستانهٔ همان تحلیل را نشان دهد.
- [ ] خروجی تمیز و نشانه‌دار در مقصد انتخابی باز شوند و شمارهٔ نام فایل با نسخهٔ ذخیره‌شده یکسان باشد.
- [ ] بازسازی Activity و مرگ فرایند، مسیر بازگشت قابل استفاده داشته باشند؛ دادهٔ موقت ازدست‌رفته به‌اشتباه طرح بازیابی‌شده معرفی نشود.

## Development Rules

1. هر Feature قبل از رفتن به Feature بعدی باید compile شود.
2. هیچ Feature ناقص به عنوان complete علامت نخورد.
3. از TODOهای مبهم استفاده نشود.
4. از magic number استفاده نشود.
5. UI نباید مستقیماً DB را تغییر دهد.
6. ML نباید روی Main Thread اجرا شود.
7. Original image نباید overwrite شود.
8. مختصات Hairline باید normalized باشند.
9. همه تغییرات Editor باید Undoable باشند.
10. هر خطای ML باید قابل مدیریت باشد و باعث crash نشود.

## Definition of Done

یک Feature زمانی Done است که:
- compile شود.
- crash نداشته باشد.
- UI قابل استفاده باشد.
- state rotation/configuration را تا حد لازم مدیریت کند.
- تست مربوطه وجود داشته باشد.
- کد در package صحیح قرار گرفته باشد.
- هیچ منطق business داخل Composable نباشد.

## وضعیت اجرای فعلی

در ۲۰۲۶/۱۱/۰۹، `:app:testDebugUnitTest` با ۶۱ تست بدون failure/error و `:app:assembleDebug` موفق بوده‌اند. دستور قابل تکرار:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

در اجرای ثبت‌شده از JDK 17 و `--offline` با وابستگی‌های موجود در cache استفاده شده است. اجرای قبلی Lint به‌دلیل خطای دریافت وابستگی از پراکسی محلی کامل نشده است. نصب نسخهٔ اصلی با `adb install -r` و اجرای Activity موفق بوده‌اند، اما بررسی بصری و لمسی کامل نشده است. موارد باز بالا عمداً تیک نخورده‌اند؛ بیلد و تست هندسی، صحت مدل یا ارزیابی بالینی را تأیید نمی‌کنند.
## الگوهای اولیهٔ خط رویش

- [x] افزودن کارت‌های انتخاب الگوی طرح اولیه در Photo Review
- [x] افزودن دقیق سه الگوی Current، Conservative و Lower
- [x] ساخت منحنی Bézier قطعی با ورودی یکسان، قوس نرم و قلاب شقیقه
- [x] نگه‌داشتن Profile قدیمی Moderate برای بارگذاری Planهای قبلی

## نمایش زندهٔ طرح ذخیره‌شده

- [x] مسیر LIVE_PLAN از طرح ذخیره‌شده، مجوز و انتخاب دوربین
- [x] تحلیل ویدئو با مدل موجود روی نخ مستقل و حذف فریم‌های عقب‌مانده
- [x] تطبیق سه‌بعدی منحنی روی سطح تقریبی پیشانی با کنترل باقیمانده، عمق، زاویه و کادر
- [x] پایداری نقاط و تأیید کاربر، reset پس از قطع/خطا/پرش/تغییر دوربین
- [x] نمایش فریم و خط با viewport و قرینگی یکسان، آزادسازی دوربین و مدل
- [x] ۱۱ تست LiveAlignmentTest، ۱۲ تست LiveCalibrationGateTest و ۷ تست LiveOverlayModeTest
- [x] ۳ تست LiveFrameDisplayTest: تداوم تصویر هنگام پردازش کند با حفظ محدودیت تازگی خط
- [x] ۱۳ تست SpatialFaceAlignmentTest و اجرای ابزار مدل واقعی سه‌بعدی در بستهٔ مستقل روی گوشی
- [x] بازسازی مرجع از عکس طرح‌های قبلی بدون تغییر schema، گره‌ها یا عکس ذخیره‌شده
- [ ] اعتبارسنجی مستقل جابه‌جایی خط روی پوست در yaw/pitch واقعی و شرایط کاری
- [x] تثبیت فضای پیام‌ها برای جلوگیری از تغییر اندازهٔ کادر زنده؛ نگه‌داشتن عکس آخر با پیام انتظار
- [x] استفاده از پیش‌نمایش COMPATIBLE در ثبت عکس و آزادسازی فقط use caseهای همان جلسه
- [ ] ارزیابی پیوستهٔ چشمک و پرش در دوربین زنده و ثبت عکس روی گوشی پس از این اصلاح
- [x] نوشتن سه تست مدل واقعی در LiveFaceTrackerInstrumentedTest با fixture عمومی
- [ ] اجرای runner استاندارد instrumentation؛ دریافت artifactهای AndroidX Test فعلاً ناموفق است
- [x] اجرای سه سناریوی مدل با runner موقت روی SM-A505F و بررسی صفحهٔ دوربین بدون چهره
- [ ] پذیرش دستی ردیابی فرد واقعی در نور، فاصله، حرکت و دوربین‌های مختلف
- [ ] اندازه‌گیری مستقل خطای پیشانی/شقیقه و اعتبارسنجی پیش از علامت‌گذاری

جزئیات در [سند 10](10-LIVE-PLAN-GUIDE.md) ثبت شده‌اند.
