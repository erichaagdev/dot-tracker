import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
  java
  alias(libs.plugins.errorprone)
  alias(libs.plugins.spring)
}

java {
  toolchain {
    languageVersion = libs.versions.java.map { JavaLanguageVersion.of(it) }
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(platform(libs.platforms.spring.boot))
  developmentOnly(platform(libs.platforms.spring.boot))
  implementation(libs.bundles.web)
  implementation(libs.bundles.persistence)
  implementation(libs.bundles.security)
  implementation(libs.spring.sessionJdbc)
  implementation(libs.jspecify)
  runtimeOnly(libs.postgres.socketFactory)
  developmentOnly(libs.spring.dockerCompose)
  errorprone(libs.errorprone.core)
  errorprone(libs.nullaway)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.spring.dataJpaTest)
  testImplementation(libs.spring.securityTest)
  testImplementation(libs.spring.webmvcTest)
  testImplementation(libs.bundles.testcontainers)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
  options.errorprone {
    check("NullAway", CheckSeverity.ERROR)
    option("NullAway:OnlyNullMarked", "true")
    option("NullAway:JSpecifyMode", "true")
  }
}

tasks.test {
  useJUnitPlatform()
}

tasks.bootBuildImage {
  imagePlatform = "linux/amd64"
  environment.put("BP_JVM_VERSION", libs.versions.java)
}

// npm runs through a shell so it's found on the PATH of the shell that started the build, where mise puts the Node
// that mise.toml pins. Started directly, it would be looked up on the PATH the daemon started with, which an IDE can
// leave without mise.
val npmInstall = tasks.register<Exec>("npmInstall") {
  workingDir = file("frontend")
  commandLine("sh", "-c", "npm ci")
  inputs.files("frontend/package.json", "frontend/package-lock.json")
  outputs.dir("frontend/node_modules")
}

val buildFrontend = tasks.register<Exec>("buildFrontend") {
  workingDir = file("frontend")
  commandLine("sh", "-c", "npm run build")
  inputs.dir("frontend/src")
  inputs.dir("frontend/public")
  inputs.files("frontend/index.html", "frontend/package.json", "frontend/vite.config.js")
  inputs.dir(npmInstall.map { it.outputs.files.singleFile })
  outputs.dir(layout.buildDirectory.dir("frontend"))
}

sourceSets.main { resources.srcDir(buildFrontend) }
