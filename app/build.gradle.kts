plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun readProperty(content: String, key: String, defaultValue: String): String {
    val pattern = Regex("""(?m)^\s*${Regex.escape(key)}\s*=\s*(.+)\s*$""")
    return pattern.find(content)?.groupValues?.get(1)?.trim() ?: defaultValue
}

fun upsertProperty(content: String, key: String, value: String): String {
    val pattern = Regex("""(?m)^\s*${Regex.escape(key)}\s*=.*$""")
    val line = "$key=$value"
    return if (pattern.containsMatchIn(content)) {
        content.replace(pattern, line)
    } else {
        content.trimEnd() + "\n$line\n"
    }
}

fun bumpVersion(version: String): String {
    val parts = version.split(".")
    var major = parts.getOrNull(0)?.toIntOrNull() ?: 1
    var minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    var patch = (parts.getOrNull(2)?.toIntOrNull() ?: 0) + 1
    if (patch >= 10) {
        patch = 0
        minor += 1
    }
    if (minor >= 10) {
        minor = 0
        major += 1
    }
    return "$major.$minor.$patch"
}

val buildTaskRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("assemble", ignoreCase = true) || taskName.contains("bundle", ignoreCase = true)
}
val gradlePropertiesFile = rootProject.file("gradle.properties")
val gradlePropertiesContent = if (gradlePropertiesFile.exists()) gradlePropertiesFile.readText() else ""
val hasVersionName = Regex("""(?m)^\s*app\.versionName\s*=.*$""").containsMatchIn(gradlePropertiesContent)
val hasVersionCode = Regex("""(?m)^\s*app\.versionCode\s*=.*$""").containsMatchIn(gradlePropertiesContent)
val storedVersionName = readProperty(gradlePropertiesContent, "app.versionName", "1.0.0")
val storedVersionCode = readProperty(gradlePropertiesContent, "app.versionCode", "1").toIntOrNull() ?: 1
var buildVersionName = storedVersionName
var buildVersionCode = storedVersionCode
if (buildTaskRequested) {
    val nextVersionName = if (hasVersionName && hasVersionCode) bumpVersion(storedVersionName) else "1.0.0"
    val nextVersionCode = if (hasVersionName && hasVersionCode) storedVersionCode + 1 else 1
    var updatedProperties = upsertProperty(gradlePropertiesContent, "app.versionName", nextVersionName)
    updatedProperties = upsertProperty(updatedProperties, "app.versionCode", nextVersionCode.toString())
    gradlePropertiesFile.writeText(updatedProperties)
    buildVersionName = nextVersionName
    buildVersionCode = nextVersionCode
}
val apkBaseName = "zenA+"
val apkOutputDirectory = file("/Users/zhanshengwu/Documents/trae_projects/zen/app/mobile/apk")

android {
    namespace = "com.zenaios.zenmobileuse"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.zenaios.zenmobileuse"
        minSdk = 24
        targetSdk = 36
        versionCode = buildVersionCode
        versionName = buildVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") && it.name != "assemble" }.configureEach {
        doLast {
            val variantPart = name.removePrefix("assemble")
            val variantName = variantPart.replaceFirstChar { it.lowercase() }
            val sourceDir = layout.buildDirectory.dir("outputs/apk/$variantName").get().asFile
            val apkFile = sourceDir.listFiles()
                ?.filter { it.isFile && it.extension == "apk" }
                ?.maxByOrNull { it.lastModified() }
                ?: return@doLast
            apkOutputDirectory.mkdirs()
            apkFile.copyTo(file("${apkOutputDirectory.absolutePath}/$apkBaseName$buildVersionName.apk"), overwrite = true)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.google.guava:guava:31.1-android")
}
