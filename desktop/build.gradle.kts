plugins {
    application
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("neontd.desktop.DesktopMain")
    applicationName = "neon-td"
    applicationDefaultJvmArgs = listOf("-Dawt.useSystemAAFontSettings=on", "-Dsun.java2d.uiScale.enabled=true")
}
