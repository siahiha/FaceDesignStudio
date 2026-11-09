# FaceDesign Studio — AI Implementation Specification

این پوشه مستندات تصمیم‌ها، نیازمندی‌ها و پیاده‌سازی FaceDesign Studio است.

نام‌های رسمی محصول:

- انگلیسی: **FaceDesign Studio**
- فارسی رسمی: **استودیو طراحی چهره**
- نام کوتاه رابط کاربری: **چهره‌پرداز**

## منبع حقیقت

آخرین تطبیق با کد: **۲۰۲۶/۱۱/۰۸**. برای رفتار فعلی برنامه، ابتدا [وضعیت پیاده‌سازی](09-CURRENT-IMPLEMENTATION.md) و برای جریان صفحات و لمس، [رابط موبایل](02-UI-UX-SPECIFICATION.md) را بخوانید. سند `01` نیازمندی‌های محصول، از جمله موارد آینده را نگه می‌دارد؛ وضعیت اجرای آن‌ها در سند `05` مشخص شده است.

## ترتیب مطالعه

1. `09-CURRENT-IMPLEMENTATION.md`
2. `00-DECISIONS.md`
3. `01-PRODUCT-REQUIREMENTS.md`
4. `02-UI-UX-SPECIFICATION.md`
5. `03-TECHNICAL-ARCHITECTURE.md`
6. `04-HAIRLINE-ALGORITHM-SPEC.md`
7. `06-DATA-STORAGE-AND-PRIVACY.md`
8. `07-DETECTION-VALIDATION.md`
9. `05-IMPLEMENTATION-TASKS.md`
10. `08-MODEL-ASSETS.md`

## برای AI Coding Agent

ابتدا `09-CURRENT-IMPLEMENTATION.md` و سپس فایل‌های موردنیاز را بخوان.

پروژه پیاده‌سازی شده است؛ تغییرات را روی کد موجود انجام بده و وضعیت قابلیت‌ها و آزمون‌های باقی‌مانده را در `05-IMPLEMENTATION-TASKS.md` نگه دار.

قانون مهم:
قبل از پیاده‌سازی Phase بعدی، Phase فعلی باید compile و قابل تست باشد.

## MVP

مسیر اصلی نسخهٔ فعلی:

```text
انتخاب یا ثبت بیمار
→ Camera/Gallery
→ Capture/Select
→ انتخاب الگوی اولیه
→ On-device Face + Landmark Detection
→ Face Detector Confidence Gate (current MVP)
→ ساخت محور، تراز و پیشنهاد اولیه
→ Bezier Editor
→ ویرایش، Zoom/Pan و Undo/Redo
→ پیش‌نمایش اختیاری
→ Save Plan همراه عکس اصلی در SQLite، یا خروجی تصویر/PDF
```

عکس غیرروبه‌رو هشدار می‌گیرد اما قابل ادامه است. Landmarkهای لازم باید حاضر باشند؛ امتیاز جداگانهٔ Landmark و میانگین Confidence هنوز در MVP شرط پذیرش نیست.

## وضعیت نسخهٔ فعلی

این پروژه اکنون یک برنامهٔ Android با package `com.example.facedesignstudio` است. بیلد Debug با `:app:assembleDebug` انجام می‌شود. Compose/Material 3، Room، CameraX، Hilt و MediaPipe Tasks Vision در برنامه استفاده شده‌اند؛ جزئیات نسخه و hash مدل‌ها در `08-MODEL-ASSETS.md` ثبت شده است.

روند عکس‌برداری CameraX بین دوربین پشت و جلو سوییچ می‌کند. پس از پذیرش عکس، برنامه مستقیماً وارد ویرایش طرح می‌شود. روند اصلی همچنین شامل ثبت/ویرایش/حذف بیمار، Gallery Picker، تحلیل روی دستگاه، Canvas برای ویرایش نقطه‌ها و handleها، ذخیره و بازکردن snapshot نسخه‌دار در Room و خروجی تصویر/PDF از طریق انتخاب‌گر فایل Android است.

فرم بیمار اطلاعات تکمیلی را در بخش بازشونده نگه می‌دارد. در Photo Review سه کارت «متعادل، بالاتر، پایین‌تر» دیده می‌شوند؛ روش ترسیم، ارتفاع سفارشی و آستانهٔ Confidence در پنل تنظیمات بیشتر هستند. ابزارهای ویرایش در سه پنل «نقطه‌ها، ظاهر خط، تراز تصویر» قرار دارند. با تقارن فعال، افزودن نقطه یک جفت متمایز می‌سازد و پس از انتخاب نقطه می‌توان آن را با کشیدن هر جای کادر عکس حرکت داد.

Confidence پیش‌فرض ۹۰٪ است و تغییر آن برای تحلیل‌های بعدی می‌ماند، بدون پاک‌کردن طرح جاری یا تغییر آستانهٔ ثبت‌شدهٔ آن. در عکس چندچهره‌ای، چهره با بالاترین Confidence انتخاب و تحلیل می‌شود. شرط پذیرش فقط روی Confidence چهره است و Landmarkهای ضروری باید وجود داشته باشند؛ میانگین Confidence Landmarkها در کد فعلی استفاده نمی‌شود.

## Build

- JDK 17، Android SDK Platform 36 و Android Gradle Plugin `8.13.2` لازم است.
- در ریشهٔ پروژه اجرا کنید: `.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug`
- خروجی: `app/build/outputs/apk/debug/app-debug.apk`

ساخت Debug و ۴۸ تست واحد موفق بوده‌اند: ۸ تست درج نقطه، ۶ تست تبدیل حرکت، ۳۳ تست تطبیق زنده و یک تست نمونه. دستور نصب، نتیجهٔ نصب روی گوشی و محدودیت اجرای Lint در [README پروژه](../README.md) ثبت شده‌اند. آزمون کامل رابط و لمس روی دستگاه، تست‌های Room/ViewModel، حالت چهار انگشت و مقایسهٔ لغزنده‌ای قبل/بعد هنوز باز هستند؛ مقایسهٔ عکس اصلی و طرح به‌صورت toggle اکنون وجود دارد. بیلد و تست هندسی به‌تنهایی جای آزمون عملکردی یا ارزیابی بالینی مدل را نمی‌گیرند.

## نمایش زندهٔ طرح

راهنمای قابلیت آزمایشی دوربین زنده، تأیید تطبیق، حدود پشتیبانی حرکت و آزمون‌های لازم در [10-LIVE-PLAN-GUIDE.md](10-LIVE-PLAN-GUIDE.md) آمده است.
