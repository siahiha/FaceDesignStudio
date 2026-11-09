# FaceDesign Studio — Model Assets and Runtime

## مدل‌های باندل‌شده

| فایل | کاربرد | اندازه | SHA-256 |
|---|---|---:|---|
| `app/src/main/assets/blaze_face_short_range.tflite` | تشخیص چهره و Confidence چهره | 229,746 bytes | `B4578F35940BF5A1A655214A1CCE5CAB13EBA73C1297CD78E1A04C2380B0152F` |
| `app/src/main/assets/face_landmarker.task` | مختصات Mesh و Landmarkهای ضروری | 3,758,596 bytes | `64184E229B263107BC2B804C6625DB1341FF2BB731874B0BCC2FE6544E0BC9FF` |

منبع دریافت، مخزن رسمی مدل‌های MediaPipe در Google Cloud Storage است:

- `https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/blaze_face_short_range.tflite`
- `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`

نسخهٔ runtime در Version Catalog برابر MediaPipe Tasks Vision `0.10.35` است. هر دو مدل به‌صورت asset در APK قرار دارند و `FaceDetectionEngine` بدون درخواست شبکه آن‌ها را بارگذاری می‌کند. عکس ورودی برای تحلیل در حافظه decode و حداکثر ضلع آن به حدود 1600 پیکسل downsample می‌شود؛ BLOB اصلی بازنویسی نمی‌شود.

## قرارداد پذیرش فعلی

1. Face Detector تعداد چهره‌ها را تعیین و Category score را به‌عنوان `faceConfidence` می‌دهد.
2. کاندیداهای بدون امتیاز معتبر نادیده گرفته می‌شوند و چهرهٔ دارای بالاترین امتیاز انتخاب می‌شود. اگر بهترین امتیاز به آستانه نرسد، عکس با خطای Confidence پایین رد می‌شود؛ چهره‌های دیگر باعث رد عکس نمی‌شوند.
3. کادر چهرهٔ منتخب با حاشیه‌ای امن crop می‌شود تا Face Landmarker مختصات همان چهره را استخراج کند؛ مختصات به دستگاه مختصات تصویر اصلی بازگردانده می‌شوند.
4. Face Landmarker نقاط ضروری را می‌دهد؛ نبودن هر نقطهٔ لازم عکس را رد می‌کند.
5. آستانهٔ قابل‌تنظیم فقط روی امتیاز واقعی Face Detector اعمال می‌شود. مدل فعلی برای هر Landmark confidence مستقل و قابل‌اتکایی تضمین نمی‌کند؛ میانگین Face + Landmark فعلاً محاسبه یا به‌عنوان gate استفاده نمی‌شود.
6. Frontal score یک تخمین هندسی است؛ پایین‌بودن آن هشدار می‌دهد و عکس را رد نمی‌کند.

## پیش از انتشار

قابلیت دوربین زنده نیز از همین `face_landmarker.task` در نمونه‌ای مستقل با حالت `VIDEO` استفاده می‌کند؛ مدل یا وابستگی جدیدی اضافه نشده است. چندچهره در این مسیر رد می‌شود و کنترل‌های تطبیق آن با پذیرش عکس ثابت متفاوت‌اند؛ [سند 10](10-LIVE-PLAN-GUIDE.md). fixture عمومی تست فقط در source set مربوط به `androidTest` است و داخل APK برنامه قرار نمی‌گیرد.

- مجوز و شرایط بازتوزیع هر دو فایل مدل را از Model Card/منبع رسمی تأیید و در بستهٔ انتشار ثبت کنید؛ این سند هنوز تأیید حقوقی مجوز را اعلام نمی‌کند.
- آستانهٔ پیش‌فرض برنامه ۹۰٪ است؛ آن را با مجموعهٔ ارزیابی نماینده کالیبره کنید و پیش از انتشار بازبینی کنید.
- زمان اجرا، مصرف حافظه، نرخ false-accept/false-reject و خطای مکانی Landmark را روی دستگاه‌های هدف اندازه‌گیری کنید.
- پس از جایگزینی مدل یا تغییر hash، نسخهٔ مدل ذخیره‌شده در هر Plan را به‌روزرسانی کنید.
