import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
  java
  alias(libs.plugins.errorprone)
  alias(libs.plugins.graalvm.native)
  alias(libs.plugins.spring)
}

group = "dev.erichaag"

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
  // The connector reaches these only for connection modes the app doesn't use, and they'd add to the native image
  runtimeOnly(libs.postgres.socketFactory) {
    exclude(group = "io.grpc", module = "grpc-xds")
    exclude(group = "dev.cel")
    exclude(group = "com.google.api.grpc", module = "proto-google-cloud-sql-v1")
  }
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

// The AOT tasks compile code that Spring generates for the native image, which isn't ours to check
tasks.named<JavaCompile>("compileAotJava") {
  options.errorprone.enabled = false
}

tasks.named<JavaCompile>("compileAotTestJava") {
  options.errorprone.enabled = false
}

tasks.test {
  useJUnitPlatform()
}

tasks.bootBuildImage {
  imagePlatform = "linux/amd64"
  environment.put("BP_JVM_VERSION", libs.versions.java)
  // A native image starts by reading its own executable, which Cloud Run loads lazily, so a smaller one starts faster.
  // -Os optimizes for size, relative code pointers shrink the table the loader fixes up at every start, and the native
  // libraries that the Cloud SQL connector's dependencies bundle stay out, since the app never loads them.
  environment.put(
    "BP_NATIVE_IMAGE_BUILD_ARGUMENTS",
    listOf(
      "-Os",
      "-H:+UnlockExperimentalVMOptions",
      "-H:+RelativeCodePointers",
      "-H:ExcludeResources=META-INF/native/.*",
      "-H:ExcludeResources=jni/.*",
      "-H:-UnlockExperimentalVMOptions",
    ).joinToString(" "),
  )
  // Nothing binds certificates to the container, so it needs no helper checking for them at every start
  environment.put("BP_RUNTIME_CERT_BINDING_DISABLED", "true")
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
