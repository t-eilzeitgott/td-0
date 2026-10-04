import org.teavm.gradle.api.OptimizationLevel

plugins {
    java
    id("org.teavm") version "0.16.0"
}

dependencies {
    implementation(project(":core"))
    implementation(teavm.libs.jsoApis)
}

teavm.js {
    mainClass = "neontd.web.WebMain"
    targetFileName = "game.js"
    optimization = OptimizationLevel.BALANCED
    obfuscated = false
    sourceMap = false
}
