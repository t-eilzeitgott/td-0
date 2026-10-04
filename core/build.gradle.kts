plugins {
    `java-library`
}

// Das Core-Modul ist reines Java ohne Abhängigkeiten, damit es sowohl von der JVM (Desktop)
// als auch von TeaVM (Browser/iPhone) verwendet werden kann.
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
