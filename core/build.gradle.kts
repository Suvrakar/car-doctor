import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

tasks.register<JavaExec>("proCodes") {
    group = "garidoctor"
    description = "Print offline Pro promo codes. -Pfrom=1 -Pcount=10 -Psalt=..."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.garidoctor.core.pro.ProCodeCliKt")
    args(
        (project.findProperty("from") ?: "1").toString(),
        (project.findProperty("count") ?: "10").toString(),
        (project.findProperty("salt") ?: "garidoctor-dev-salt").toString(),
    )
}
