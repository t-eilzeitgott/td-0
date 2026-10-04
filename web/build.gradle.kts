import java.security.MessageDigest
import org.teavm.gradle.api.OptimizationLevel

plugins {
    java
    id("org.teavm") version "0.16.0"
}

dependencies {
    implementation(project(":core"))
    implementation(teavm.libs.jsoApis)
    // Nur damit der Compiler die @Language-Annotationen von TeaVM kennt (vermeidet Warnungen).
    compileOnly("org.jetbrains:annotations:24.1.0")
}

teavm.js {
    mainClass = "neontd.web.WebMain"
    targetFileName = "game.js"
    optimization = OptimizationLevel.BALANCED
    // Minifiziert (kleiner, schneller geladen). Für lesbaren Code beim Debuggen: ./gradlew :web:assembleWeb -PdebugJs
    obfuscated = !project.hasProperty("debugJs")
    sourceMap = false
}

// Fügt Seite, Skript, Manifest, Service Worker und Icons zu einem auslieferbaren Ordner zusammen.
// Der Hash des Spiels dient als Versionsnummer: neuer Code => neuer Cache, kein veralteter Stand auf dem iPhone.
val assembleWeb by tasks.registering(Sync::class) {
    group = "build"
    description = "Baut die Web-Version (build/dist) – direkt hostbar, z. B. auf GitHub Pages."
    dependsOn("generateJavaScript")
    from("src/main/webapp")
    from(layout.buildDirectory.dir("generated/teavm/js")) {
        include("game.js")
    }
    into(layout.buildDirectory.dir("dist"))
    doLast {
        val dist = layout.buildDirectory.dir("dist").get().asFile
        val js = File(dist, "game.js")
        val hash = MessageDigest.getInstance("SHA-256").digest(js.readBytes())
            .joinToString("") { "%02x".format(it) }.take(10)
        listOf("index.html", "sw.js").forEach { name ->
            val f = File(dist, name)
            f.writeText(f.readText().replace("@BUILD_ID@", hash))
        }
    }
}

tasks.named("build") {
    dependsOn(assembleWeb)
}
