import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "org.kkaemok"
version = "3.0-SNAPSHOT-20"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.commons" && requested.name == "commons-lang3") {
            useVersion("3.18.0")
            because("CVE-2025-48924")
        }
        if (requested.group == "org.codehaus.plexus" && requested.name == "plexus-utils") {
            useVersion("4.0.3")
            because("CVE-2025-67030")
        }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    implementation("fr.mrmicky:fastboard:2.1.5")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    relocate("fr.mrmicky.fastboard", "org.kkaemok.reAbility.libs.fastboard")
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

tasks {
    runServer {
        minecraftVersion("1.21.11")
    }
}

val targetJavaVersion = 21
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
