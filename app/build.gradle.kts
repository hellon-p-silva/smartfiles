plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.smartfiles.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartfiles.app"
        // Android 11+: simplifica o modelo de permissões (MANAGE_EXTERNAL_STORAGE)
        minSdk = 30
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Índice local de arquivos
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Indexação em segundo plano
    implementation(libs.androidx.work.runtime.ktx)

    // Miniaturas de imagens
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // OCR offline (modelo latino embutido: português, inglês, espanhol...)
    implementation(libs.mlkit.text.recognition)
    // Rótulos de imagem offline (gato, comida, praia...) pras categorias automáticas
    implementation(libs.mlkit.image.labeling)
}
