plugins { `java-library`; id("xyz.jpenilla.run-paper") version "3.0.2" }
val pluginVersion = project.version.toString()
dependencies {
    implementation(project(":custodian-core"))
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks {
    // Paper does not provide JDBC. Embed the core and SQLite driver in the plugin artifact.
    jar {
        dependsOn(project(":custodian-api").tasks.named("jar"), project(":custodian-core").tasks.named("jar"))
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    }
    runServer { minecraftVersion("1.18.2"); jvmArgs("-Xms2G", "-Xmx2G") }
    processResources { filesMatching("plugin.yml") { expand(mapOf("version" to pluginVersion)) } }
}
