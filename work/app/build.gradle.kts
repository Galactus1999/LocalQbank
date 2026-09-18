plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Firebase is activated only when app/google-services.json is present; local/offline builds remain fully functional without it.
if (file("google-services.json").exists()) {
    pluginManager.apply("com.google.gms.google-services")
    pluginManager.apply("com.google.firebase.crashlytics")
}

android {
    namespace = "com.localqbank.library"
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileSdk = 37
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        // CI uses one persistent keystore so debug APKs remain installable as
        // in-place updates across GitHub Actions runners. Secrets are supplied by CI;
        // no private signing material is stored in source control.
        val storeFilePath = System.getenv("ROVEX_SIGNING_STORE_FILE")
        val storePasswordEnv = System.getenv("ROVEX_SIGNING_STORE_PASSWORD")
        val keyAliasEnv = System.getenv("ROVEX_SIGNING_KEY_ALIAS")
        val keyPasswordEnv = System.getenv("ROVEX_SIGNING_KEY_PASSWORD")
        val ciSigningConfigured = !storeFilePath.isNullOrBlank() &&
            !storePasswordEnv.isNullOrBlank() && !keyAliasEnv.isNullOrBlank() &&
            !keyPasswordEnv.isNullOrBlank()
        if (ciSigningConfigured) {
            create("ci") {
                storeFile = file(storeFilePath!!)
                storePassword = storePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }
    buildTypes {
        debug {
            // Local builds retain the normal Android debug key; CI supplies the
            // persistent signing environment and therefore uses signingConfigs.ci.
            val hasCiSigning = signingConfigs.names.contains("ci")
            if (hasCiSigning) signingConfig = signingConfigs.getByName("ci")
        }
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The release APK is the canonical user-update artifact. In CI it is
            // signed with the same persistent Rovex certificate used by every
            // previous update-safe build, so Android can install it over the
            // existing app without uninstalling or clearing its data.
            val hasCiSigning = signingConfigs.names.contains("ci")
            if (hasCiSigning) signingConfig = signingConfigs.getByName("ci")
        }
        // AGP 9.3.x does not expose a generated `profile {}` shortcut; create the
        // custom build type explicitly. `isProfileable` is the supported AGP DSL
        // and causes the generated manifest to be profileable without a raw
        // android:profileableByShell attribute.
        create("profile") {
            initWith(getByName("release"))
            isDebuggable = false
            isProfileable = true
            val hasCiSigning = signingConfigs.names.contains("ci")
            if (hasCiSigning) signingConfig = signingConfigs.getByName("ci")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    defaultConfig {
        applicationId = "com.localqbank.library"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 359
        versionName = "8.3.265"
    }
}
// Update safety: release artifacts must never silently fall back to the developer/debug key.
// CI injects the persistent signing configuration; local unsigned release builds are allowed
// for development, but the CI guard below rejects any artifact whose certificate differs.

// v8.3.139: the Qualcomm LiteRT v75 JIT runtime (libLiteRtDispatch_Qualcomm.so and friends) was
// previously only staged into app/src/main/jniLibs/arm64-v8a by CI (tools/prepare_litert_qualcomm_v75_jit.sh
// run as a separate workflow step). Any *local* build — Android Studio "Run", a bare `./gradlew
// assembleDebug`, etc. — skipped that step, so jniLibs/arm64-v8a stayed empty, the installed APK
// never contained the Qualcomm libraries, and BenEmbeddingGemmaEngine's runtime-extraction fallback
// failed exactly as designed with "Bundled Qualcomm runtime library not found in installed APKs:
// libLiteRtDispatch_Qualcomm.so" (device diagnostic FAIL/REVIEW on v8.3.138). The engine code itself
// is not the bug here: it is refusing to run a Qualcomm DISPATCH_OP graph without the matching
// vendor runtime, which is correct. The bug is that only the CI build pipeline guaranteed that
// runtime existed. Wiring the same script into `preBuild` closes that gap for every build path.
val qualcommRuntimeDir = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a")
val qualcommRequiredLibs = listOf(
    "libLiteRtDispatch_Qualcomm.so",
    "libLiteRtCompilerPlugin_Qualcomm.so",
    "libQnnHtp.so",
    "libQnnSystem.so",
    "libQnnHtpPrepare.so",
    "libQnnIr.so",
    "libQnnSaver.so"
)

val prepareQualcommRuntime = tasks.register<Exec>("prepareQualcommRuntime") {
    description = "Stages the official LiteRT Qualcomm SM8650/v75 JIT runtime into jniLibs/arm64-v8a " +
        "before packaging, for local builds as well as CI."
    workingDir = rootProject.projectDir
    commandLine("bash", "tools/prepare_litert_qualcomm_v75_jit.sh")

    // Skip the network fetch entirely if a previous run (or CI) already staged a complete,
    // non-empty set of the required libraries plus at least one HTP v75 stub/skel file. This
    // keeps the local edit-build-install loop fast: the script only re-runs after a clean or
    // when a library is actually missing/corrupt.
    onlyIf {
        val dir = qualcommRuntimeDir.asFile
        val hasV75 = dir.listFiles()?.any { it.name.startsWith("libQnnHtpV75") && it.length() > 0L } == true
        val hasAllRequired = qualcommRequiredLibs.all { name ->
            File(dir, name).let { it.isFile && it.length() > 0L }
        }
        !(dir.isDirectory && hasV75 && hasAllRequired)
    }
}

// Keep vendor-runtime preparation off compile-only tasks. Kotlin/resource compilation must never
// download a ~2.35 GB Qualcomm archive. Package-producing tasks still require the runtime, so an
// APK cannot silently omit the libraries needed by the Qualcomm DISPATCH_OP path.
tasks.matching { task ->
    task.name.matches(Regex("merge.*JniLibFolders")) ||
        task.name.matches(Regex("merge.*NativeLibs"))
}.configureEach {
    dependsOn(prepareQualcommRuntime)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.webkit:webkit:1.12.1")
    debugImplementation("androidx.metrics:metrics-performance:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    // Offline-capable bundled OCR for imported RR/reference PDFs.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    // ViewModel layer for incremental Activity architecture stabilization.
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.2")

    // Compose is introduced incrementally for new AI surfaces; XML/View screens remain intact.
    // Stable Compose BOM and Material 3 are used, with Kotlin 2.2.10's compiler plugin.
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Optional cloud Gemini bridge. Firebase AI Logic keeps the Gemini Developer API key
    // server-side; Firebase Authentication gates requests to the signed-in Google account.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-config")
    // Production crash/ANR reporting; enabled when google-services.json is present.
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation("androidx.credentials:credentials:1.6.0-beta01")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0-beta01")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    // Optional on-device generative accelerator. Never bundled with a model artifact.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
    // Optional on-device embedding runtime. LiteRT CompiledModel executes the user-imported
    // EmbeddingGemma .tflite graph through the modern accelerator-first API. CPU is the
    // stability baseline; Qualcomm NPU dispatch uses a strict AOT-only execution path for precompiled Qualcomm artifacts.
    implementation("com.google.ai.edge.litert:litert:2.2.0")
    // Pure-Java SentencePiece tokenizer: avoids DJL's Android JNI extraction path, which
    // can fail with "Cannot copy jni files" before the EmbeddingGemma model is even opened.
    implementation("io.github.eix128:sentencepiece4j:1.0.2")
    // Modern Anki .apkg uses Zstandard-compressed collection.anki21b and media.
    implementation("com.github.luben:zstd-jni:1.5.7-16@aar")
    // JVM unit tests (src/test) - starting point for extracting and locking in pure logic
    // like SpacedRepetitionScheduler out of the Activities/DB layer.
    testImplementation("junit:junit:4.13.2")

    // Android/device-level verification for crash-sensitive flows.
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
