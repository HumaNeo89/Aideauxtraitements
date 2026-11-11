import java.util.Properties
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

// Signature release : keystore.properties (racine du dépôt) d'abord, sinon variables d'environnement
// DOSEJUSTE_*. Aucune valeur secrète n'est jamais affichée.
class SignatureRelease(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

val signatureRelease: SignatureRelease? = run {
    val fichier = rootProject.file("keystore.properties")
    val props = Properties()
    if (fichier.isFile) fichier.inputStream().use { props.load(it) }
    val source = if (fichier.isFile) "keystore.properties" else "variables d'environnement DOSEJUSTE_*"

    fun valeur(cle: String, env: String): String =
        if (fichier.isFile) props.getProperty(cle).orEmpty().trim() else System.getenv(env).orEmpty().trim()

    val chemin = valeur("storeFile", "DOSEJUSTE_STORE_FILE").ifEmpty { "D:/humaneo/cles/dosejuste-release.jks" }
    val motDePasse = valeur("storePassword", "DOSEJUSTE_STORE_PASSWORD")
    val alias = valeur("keyAlias", "DOSEJUSTE_KEY_ALIAS")
    val motDePasseCle = valeur("keyPassword", "DOSEJUSTE_KEY_PASSWORD")

    val manquants = buildList {
        if (motDePasse.isEmpty()) add("storePassword")
        if (alias.isEmpty()) add("keyAlias")
        if (motDePasseCle.isEmpty()) add("keyPassword")
        if (!file(chemin).isFile) add("fichier keystore introuvable : $chemin")
    }
    if (manquants.isEmpty()) {
        SignatureRelease(chemin, motDePasse, alias, motDePasseCle)
    } else {
        logger.warn(
            "Signature release non configurée (source : $source ; manquant : ${manquants.joinToString(", ")}) : " +
                "l'APK release sera NON signé. Voir docs/release.md.",
        )
        null
    }
}

android {
    namespace = "fr.dosejuste.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.dosejuste.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Signature release, seulement si la configuration est complète (voir docs/release.md).
        if (signatureRelease != null) {
            create("release") {
                storeFile = file(signatureRelease.storeFile)
                storePassword = signatureRelease.storePassword
                keyAlias = signatureRelease.keyAlias
                keyPassword = signatureRelease.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signatureRelease != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Schémas Room exportés (versionnés), lus par MigrationTestHelper dans les tests instrumentés.
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    // Contrainte (pas de dépendance directe) : aligne core sur le json 1.8.1 de room-testing.
    constraints { implementation(libs.kotlinx.serialization.core) }

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)

    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    // Espresso 3.7.0 : le 3.5.0 transitif échoue sur Android 17 (API 37), tests instrumentés seulement.
    androidTestImplementation(libs.androidx.test.espresso.core)
}

// Le test unitaire lit le manifeste déjà fusionné (dépendances comprises).
tasks.withType<Test>().configureEach {
    dependsOn("processDebugManifest")
}
