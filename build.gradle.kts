// Gemeinsame Einstellungen für alle Module.
allprojects {
    group = "neontd"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        tasks.withType<JavaCompile>().configureEach {
            // Java 17 reicht überall (Desktop-JDK, TeaVM) und hält das Spiel auf vielen Rechnern lauffähig.
            options.release.set(17)
            options.encoding = "UTF-8"
            options.compilerArgs.add("-Xlint:all,-serial")
        }
    }
}
