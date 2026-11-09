# FaceDesign Studio — تشخیص و اعتبارسنجی واقعی

## 1. مدل و اجرای inference

برنامه از MediaPipe Tasks Vision `0.10.35` استفاده می‌کند:

- BlazeFace Short Range برای detection و score چهره
- Face Landmarker برای مختصات Landmark

هر دو مدل از asset داخلی APK خوانده می‌شوند و inference فقط روی دستگاه اجرا می‌شود.

## 2. قاعدهٔ انتخاب چهره

همهٔ detectionهای دارای score معتبر بررسی می‌شوند. detection با بیشترین score انتخاب می‌شود. اگر هیچ detection معتبر وجود نداشته باشد، نتیجه `NO_FACE` است. در تصویر چندچهره‌ای فقط بهترین detection مبنا قرار می‌گیرد.

## 3. شرط پذیرش در نسخهٔ فعلی

مقدار Settings با `PlannerRepository.DEFAULT_THRESHOLD_PERCENT = 90` شروع می‌شود و بین ۱ تا ۹۹ درصد ذخیره می‌شود.

```text
acceptFace = bestFaceConfidence >= configuredThreshold
acceptLandmarks = all required landmarks are finite and normalized
accepted = acceptFace && acceptLandmarks
```

مدل فعلی confidence مستقل Landmark را برای gate قابل اتکا ارائه نمی‌کند؛ بنابراین فرمول میانگین Face + Landmark در کد فعلی وجود ندارد. اسناد قدیمی که میانگین را شرط فعلی معرفی می‌کنند معتبر نیستند.

اگر شرط برقرار نباشد، عکس وارد Editor نمی‌شود و یکی از این نتیجه‌ها ثبت می‌شود:

- `NO_FACE`
- `LOW_FACE_CONFIDENCE`
- `REQUIRED_LANDMARK_MISSING`
- `MODEL_ERROR`

## 4. Landmarkهای لازم

Face Landmarker باید این ۱۱ نقطه را با مختصات معتبر برگرداند:

`LEFT_EYE_LOWER`, `RIGHT_EYE_LOWER`, `LEFT_PUPIL`, `RIGHT_PUPIL`, `LEFT_IRIS_BOTTOM`, `RIGHT_IRIS_BOTTOM`, `GLABELLA`, `NOSE_TIP`, `CHIN`, `LEFT_TEMPLE`, `RIGHT_TEMPLE`.

مختصات crop چهره به مختصات تصویر اصلی برگردانده می‌شوند.

## 5. Frontal warning

`frontalScore` از دو جزء ساخته می‌شود:

```text
bilateralSymmetry × 0.75
eyeLevelScore × 0.25
```

اگر score کمتر از `0.65` باشد، پیام هشدار زاویه نمایش داده می‌شود. این هشدار جلوی ادامهٔ ویرایش یا Save را نمی‌گیرد.

## 6. تغییر threshold

آستانه در Settings ذخیره می‌شود. در Photo Review نیز از پنل «تنظیمات بیشتر» قابل تغییر است و تحلیل بعدی با مقدار جدید اجرا می‌شود. عکس مردود برای ارزیابی با مقدار جدید باید دوباره تحلیل شود؛ تغییر تنظیم، پذیرش خودکار ایجاد نمی‌کند.

پس از پذیرش، مقدار استفاده‌شده در `activePlanThresholdPercent` نگه داشته می‌شود و همراه Plan ذخیره می‌شود. بارگذاری طرح همان مقدار تاریخی را بازمی‌گرداند. `saveThreshold` فقط تنظیم تحلیل‌های بعدی را تغییر می‌دهد؛ تحلیل پذیرفته‌شده، Nodeها و history طرح جاری را پاک نمی‌کند و آستانهٔ تاریخی آن را بازنویسی نمی‌کند.

## 7. چیزهایی که این فایل ادعا نمی‌کند

- confidence مدل تضمین‌کنندهٔ کیفیت بالینی نیست.
- threshold پیش‌فرض ۹۰٪ هنوز به مجموعهٔ نمایندهٔ بالینی کالیبره نشده است.
- میانگین confidence Landmarkها در نسخهٔ فعلی فعال نیست.
- frontal warning یک ارزیابی هندسی ساده است، نه تشخیص پزشکی.

## 8. اعتبارسنجی لازم برای انتشار

این موارد هنوز باید با تست واقعی تکمیل شوند:

- نرخ false accept و false reject در نور، فاصله، زاویه و انسداد مختلف
- خطای مکانی Landmark نسبت به فاصلهٔ چشم‌ها
- زمان تحلیل و مصرف حافظه در دستگاه‌های هدف
- تست مرز threshold در مقدار برابر، کمتر و بیشتر
- تست عکس بدون Landmark ضروری
- تست چندچهره‌ای و انتخاب بهترین score
- تست پایدار ماندن threshold و تحلیل پس از بستن و بازکردن Plan

## 9. تفاوت تشخیص عکس و ردیابی زنده

قاعدهٔ انتخاب قوی‌ترین چهره و آستانهٔ عمومی این سند برای تحلیل عکس ثابت است. دوربین زنده مدل مستقلی در حالت VIDEO با حداکثر دو چهره دارد و چندچهره را رد می‌کند؛ تنظیم عمومی عکس gate این مسیر نیست. کنترل کیفیت هندسی و پایداری/تأیید جلسه در [سند 10](10-LIVE-PLAN-GUIDE.md) شرح داده شده‌اند.
