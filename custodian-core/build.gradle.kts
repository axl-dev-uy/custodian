plugins { `java-library` }
dependencies {
    api(project(":custodian-api"))
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
