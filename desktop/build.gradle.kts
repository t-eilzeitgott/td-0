plugins {
    application
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("neontd.desktop.DesktopMain")
    // Hardware-beschleunigtes Rendering wo verfügbar, ruhige Skalierung auf HiDPI-Bildschirmen.
    applicationDefaultJvmArgs = listOf("-Dsun.java2d.opengl=true", "-Dawt.useSystemAAFontSettings=on")
}
