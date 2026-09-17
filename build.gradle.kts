plugins { id("base") }

allprojects {
    group = "com.axl.custodian"
    version = "0.1.0-SNAPSHOT"
    repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
}

subprojects {
    plugins.withId("java-library") {
        extensions.configure<JavaPluginExtension> { toolchain.languageVersion = JavaLanguageVersion.of(17) }
        tasks.withType<JavaCompile>().configureEach { options.release = 17; options.encoding = "UTF-8" }
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
    }
}
