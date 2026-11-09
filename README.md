# FaceDesign Studio

- نام انگلیسی: **FaceDesign Studio**
- نام فارسی رسمی: **استودیو طراحی چهره**
- نام کوتاه فارسی در رابط کاربری: **چهره‌پرداز**

برنامهٔ Android **FaceDesign Studio** برای ثبت اطلاعات بیمار، تحلیل چهره روی خود گوشی و طراحی خطوط چهره است. نسخهٔ فعلی روی خط رویش تمرکز دارد و معماری برای افزودن خط ریش و طراحی ابرو آماده شده است.

## وضعیت پیاده‌سازی

آخرین تطبیق مستندات با کد: **۲۰۲۶/۱۱/۰۸**.

مبنای این README و مستندات پوشهٔ `FaceDesignStudio-Specification` کد موجود در `app/src/main/java` و assetهای مدل در `app/src/main/assets` است. سند اصلی رفتار فعلی برنامه [09-CURRENT-IMPLEMENTATION.md](FaceDesignStudio-Specification/09-CURRENT-IMPLEMENTATION.md) است؛ موارد پیشنهادی یا آینده در سندهای قدیمی با برچسب «خارج از پیاده‌سازی فعلی» مشخص شده‌اند.

## قابلیت‌های فعلی

- ثبت، ویرایش، جست‌وجو و حذف بیمار
- گرفتن عکس با دوربین داخل برنامه و سوییچ دوربین جلو/پشت
- انتخاب عکس از Gallery
- تشخیص کاملاً روی دستگاه با MediaPipe
- انتخاب قوی‌ترین چهره در تصویر چندچهره‌ای
- Confidence پیش‌فرض ۹۰٪، قابل تغییر در Settings و بررسی تصویر
- هشدار برای عکس غیرروبه‌رو، با امکان ادامه
- ورود مستقیم عکس پذیرفته‌شده به ویرایش طرح
- محور تقارن از وسط مراکز دو قرنیه، عمود بر خط اتصال نقاط زیر قرنیه
- خط رویش Bezier متقارن با ویرایش نقطه و دستگیره
- سه الگوی آمادهٔ متعادل (`CURRENT`)، بالاتر (`CONSERVATIVE`) و پایین‌تر (`LOWER`)؛ هر سه با چهار Node Bézier و قلاب شقیقه
- رابط سادهٔ موبایل با ابزارهای نقطه‌ها، ظاهر خط و تراز تصویر در پنل‌های پایین صفحه
- افزودن دو نقطهٔ متناظر در حالت تقارن، با حفظ شکل منحنی؛ افزودن یک نقطه در حالت مستقل
- انتخاب نقطه و جابه‌جایی نسبی آن با کشیدن هر جای کادر عکس، حتی در زوم و چرخش
- نمایش دستگیره‌های فعال فقط برای نقطهٔ انتخاب‌شده؛ لغو انتخاب از پنل نقطه‌ها
- زوم و جابه‌جایی با لمس دو انگشتی و بازنشانی با دوبار لمس
- راهنماهای چشم، تقارن و شقیقه با کلید روشن/خاموش؛ در ابتدا خاموش
- ذخیرهٔ عکس اصلی به‌صورت BLOB در SQLite محلی با Room
- ذخیرهٔ نسخه‌های تغییرناپذیر طرح
- خروجی تصویر نشانه‌دار، تصویر تمیز و PDF
- ذخیرهٔ مستقیم از ویرایشگر، پیش‌نمایش اختیاری و هشدار خروج با تغییرات ذخیره‌نشده
- ویرایش مشخصات بیمار با حفظ عکس‌ها و نسخه‌های وابسته؛ حفظ طرح جاری هنگام تغییر آستانهٔ تشخیص

## نمایش طرح روی دوربین زنده

طرح ذخیره‌شده را باز کنید و آیکون دوربین بالای ویرایشگر یا «خروجی ← نمایش روی دوربین زنده» را بزنید. پس از پایدارشدن نقاط، تطبیق چشم‌ها و بینی را بررسی و تأیید کنید. خط با حرکت صورت دنبال می‌شود؛ هنگام اختلاف زاویه کمرنگ و با انطباق مناسب پررنگ می‌شود؛ با قطع ردیابی یا اختلاف شدید پنهان می‌شود. این قابلیت آزمایشی، تطبیق دوبعدی است و دقت میلی‌متری آن تأیید نشده است. [راهنما و محدودیت‌ها](FaceDesignStudio-Specification/10-LIVE-PLAN-GUIDE.md).

## ساخت

پیش‌نیاز: JDK 17 و Android SDK Platform 36.

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

خروجی در `app/build/outputs/apk/debug/app-debug.apk` ساخته می‌شود.

## نصب روی گوشی

گوشی را با USB debugging فعال و اتصال تأییدشده وصل کنید. با در دسترس بودن `adb` از Android SDK Platform Tools، در ریشهٔ پروژه اجرا کنید:

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.facedesignstudio/com.example.facedesignstudio.MainActivity
```

اگر چند دستگاه متصل است، به فرمان‌های نصب و اجرا `-s <device-serial>` اضافه کنید. گزینهٔ `-r` نسخهٔ هم‌امضای برنامه را با حفظ داده‌ها به‌روزرسانی می‌کند. نصب نسخهٔ اصلی و اجرای Activity روی گوشی متصل در ۲۰۲۶/۱۱/۰۸ موفق بوده است.

## اعتبارسنجی ثبت‌شده

ساخت Debug و ۴۸ تست واحد موفق بوده‌اند: ۸ تست افزودن نقطه و حفظ منحنی، ۶ تست تبدیل جابه‌جایی لمس، ۳۳ تست تطبیق زنده و تأیید ردیابی و ۱ تست نمونهٔ پروژه. جزئیات پوشش و آزمون‌های باقی‌مانده در [وظایف پیاده‌سازی](FaceDesignStudio-Specification/05-IMPLEMENTATION-TASKS.md) آمده است. نصب موفق، جای بررسی بصری و آزمون واقعی لمس روی گوشی را نمی‌گیرد؛ این بررسی رابط هنوز تکمیل نشده است. اجرای Lint قبلی به‌دلیل خطای دریافت وابستگی از پراکسی محلی کامل نشد.

## مستندات

1. [تصمیم‌های قطعی و باز](FaceDesignStudio-Specification/00-DECISIONS.md)
2. [نیازمندی‌های محصول](FaceDesignStudio-Specification/01-PRODUCT-REQUIREMENTS.md)
3. [رفتار واقعی رابط کاربری](FaceDesignStudio-Specification/02-UI-UX-SPECIFICATION.md)
4. [معماری واقعی](FaceDesignStudio-Specification/03-TECHNICAL-ARCHITECTURE.md)
5. [الگوریتم واقعی خط رویش و تقارن](FaceDesignStudio-Specification/04-HAIRLINE-ALGORITHM-SPEC.md)
6. [وظایف پیاده‌سازی و موارد باز](FaceDesignStudio-Specification/05-IMPLEMENTATION-TASKS.md)
7. [ذخیره‌سازی و حریم خصوصی](FaceDesignStudio-Specification/06-DATA-STORAGE-AND-PRIVACY.md)
8. [تشخیص و اعتبارسنجی](FaceDesignStudio-Specification/07-DETECTION-VALIDATION.md)
9. [مدل‌ها و assetها](FaceDesignStudio-Specification/08-MODEL-ASSETS.md)
10. [وضعیت کامل پیاده‌سازی](FaceDesignStudio-Specification/09-CURRENT-IMPLEMENTATION.md)
11. [نمایش طرح روی دوربین زنده](FaceDesignStudio-Specification/10-LIVE-PLAN-GUIDE.md)
