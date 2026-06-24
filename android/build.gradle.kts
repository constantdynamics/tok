// Root-build: declareert de plugins (versies) die de :app-module toepast.
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.24" apply false
}
