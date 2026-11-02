# Dependencies

Pinned in `MotionStudio/app/build.gradle.kts` and `MotionStudio/build.gradle.kts`.

| Component | Version | Use |
| --- | --- | --- |
| Android Gradle Plugin | 8.7.2 | build |
| Gradle wrapper | 8.11.1 | build |
| Kotlin | 2.0.21 | language, Compose compiler plugin, serialization plugin |
| KSP | 2.0.21-1.0.25 | Room + Hilt annotation processing |
| Compose BOM | 2024.12.01 | Material 3, UI, tooling |
| Core KTX | 1.13.1 | platform extensions |
| Activity Compose | 1.9.3 | `ComponentActivity` + Compose |
| Lifecycle | 2.8.7 | ViewModel, runtime, LiveData→Flow |
| Navigation Compose | 2.8.5 | Home/Editor/Settings navigation |
| Room | 2.6.1 | project database + snapshots |
| Hilt | 2.51.1 | DI (`@HiltAndroidApp`, `@HiltViewModel`, `@HiltWorker`) |
| Hilt Navigation / Work | 1.2.0 | Compose + WorkManager integration |
| WorkManager | 2.9.1 | background export |
| DataStore Preferences | 1.1.1 | settings |
| kotlinx.serialization JSON | 1.7.3 | project format |
| kotlinx.coroutines | 1.9.0 | rendering/export concurrency |
| Media3 | 1.5.0 | media playback APIs / extractor utilities |
| DocumentFile | 1.0.1 | SAF helpers |
| Coil Compose | 2.7.0 | project thumbnails |
| Material Icons Extended | via BOM | editor icons |
| JUnit | 4.13.2 | JVM unit tests |
| AndroidX Test / Espresso / Compose UI test | 1.2.1 / 3.6.1 / BOM | instrumented tests |

Android framework APIs used directly: `MediaCodec`, `MediaMuxer`, `MediaExtractor`,
`MediaMetadataRetriever`, `OpenGL ES 2.0` (`GLES20`/`EGL14`), `Canvas`/`Bitmap`/`Path`,
`MediaStore`, `FileProvider`.

compileSdk / targetSdk 35, minSdk 26 (Android 8.0), JDK 17.
