// Pure Kotlin/JVM module: forum models, URL routes, the HTML parsers (home, forum, thread, post
// content) and the politeness rules. No Android dependencies, so it is unit tested on a plain JVM
// (also in cloud sessions, see AGENTS.md "Testing").
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.jsoup)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
