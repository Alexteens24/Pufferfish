import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("io.papermc.paperweight.patcher") version "2.0.0-beta.21"
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

paperweight {
    upstreams.paper {
        ref = providers.gradleProperty("paperRef")

        patchFile {
            path = "paper-server/build.gradle.kts"
            outputFile = file("pufferfish-server/build.gradle.kts")
            patchFile = file("pufferfish-server/build.gradle.kts.patch")
        }
        patchFile {
            path = "paper-api/build.gradle.kts"
            outputFile = file("pufferfish-api/build.gradle.kts")
            patchFile = file("pufferfish-api/build.gradle.kts.patch")
        }
        patchDir("paperApi") {
            upstreamPath = "paper-api"
            excludes = setOf("build.gradle.kts")
            patchesDir = file("pufferfish-api/paper-patches")
            outputDir = file("paper-api")
        }
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(25)
        options.isFork = true
        options.compilerArgs.addAll(listOf("-Xlint:-deprecation", "-Xlint:-removal"))
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test>().configureEach {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }

    tasks.named("build") {
        setDependsOn(listOf<Task>())
        description = "Use the root ./gradlew build task to produce the server JAR"
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
        maven("https://jitpack.io")
    }
}

tasks.register("printMinecraftVersion") {
    doLast {
        println(providers.gradleProperty("mcVersion").get().trim())
    }
}

tasks.register("printPufferfishVersion") {
    doLast {
        println(project.version)
    }
}

val distJarName = "pufferfish-${providers.gradleProperty("mcVersion").get().trim()}.jar"

tasks.register<Exec>("applyAllPatchesForBuild") {
    group = "build"
    description = "Apply all patches before building the server JAR"
    workingDir = rootDir
    commandLine("./gradlew", "applyAllPatches", "--no-configuration-cache")
}

tasks.register<Exec>("createPaperclipForBuild") {
    group = "build"
    description = "Build the paperclip JAR after patches are applied"
    dependsOn("applyAllPatchesForBuild")
    workingDir = rootDir
    commandLine("./gradlew", ":pufferfish-server:createPaperclipJar", "--no-configuration-cache")
}

tasks.register<Copy>("copyPufferfishJarToDist") {
    group = "build"
    description = "Copy the runnable paperclip JAR to dist/"
    dependsOn("createPaperclipForBuild")
    from(layout.projectDirectory.file("pufferfish-server/build/libs/pufferfish-paperclip-$version.jar"))
    into(layout.projectDirectory.dir("dist"))
    rename("pufferfish-paperclip-$version.jar", distJarName)
    notCompatibleWithConfigurationCache("uses project version in copy paths")
}

tasks.register("build") {
    group = "build"
    description = "Apply patches, build paperclip JAR, and copy to dist/pufferfish-<version>.jar"
    dependsOn("copyPufferfishJarToDist")
}

tasks.register("paperclip") {
    group = "build"
    description = "Build a runnable paperclip JAR for distribution"
    dependsOn("createPaperclipForBuild")
}

tasks.register("buildPufferfishJar") {
    group = "pufferfish"
    description = "Apply all patches and build the paperclip JAR"
    dependsOn("build")
}
