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
  // Postgres connects through the Unix socket that Cloud Run's built-in Cloud SQL connection provides
  runtimeOnly(libs.bundles.junixsocket)
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
  // -Os optimizes for size, and relative code pointers shrink the table the loader fixes up at every start.
  // junixsocket bundles its JNI library for many platforms, and only the one it loads on the image's platform stays in.
  // It names the architecture the way Java does, and the buildpack splits these arguments like a shell, so the pattern
  // is quoted.
  environment.put(
    "BP_NATIVE_IMAGE_BUILD_ARGUMENTS",
    imagePlatform.map { platform ->
      val arch = if (platform.endsWith("/arm64")) "aarch64" else "amd64"
      listOf(
        "-Os",
        "--enable-native-access=ALL-UNNAMED",
        "-H:+UnlockExperimentalVMOptions",
        "-H:+RelativeCodePointers",
        "'-H:ExcludeResources=lib/(?!$arch-Linux-clang/jni/libjunixsocket-native-[0-9.]+\\.so)[^/]+/jni/.*'",
        "-H:-UnlockExperimentalVMOptions",
      ).joinToString(" ")
    },
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
