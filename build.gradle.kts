import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.kotlin.dsl.named
import java.util.Properties

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

relocate("org.yaml.snakeyaml")
relocate("org.spongepowered.configurate")
relocate("com.google.gson")

// Experimental native admission currently has a tested Linux x86_64 development classifier.
val nativePlatforms = listOf("x86_64")

val networkPin = Properties().apply {
    file("registration-network.properties").inputStream().use { load(it) }
}.getProperty("commit")

val id = project.property("id") as String
val extensionName = project.property("name") as String
val author = project.property("author") as String
val version = project.version as String

val geyserVersion = "2.11.0"
val netherNetVersion = "1.8.0"
val libdatachannelVersion = "0.24.1.1-warden.5544964002162d184bacfcd0cb8d70d86ec3f271"

val configurateVersion = "4.2.0-GeyserMC-20251111.004649-11"

repositories {
    mavenLocal { content { includeGroup("dev.ziax.warden") } }
    // Repo for the Geyser API artifact
    maven("https://repo.opencollab.dev/main/")

    // Add other repositories here
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.geysermc.geyser:core:$geyserVersion-SNAPSHOT")
    // Geyser API - needed for all extensions
    compileOnly("org.geysermc.geyser:api:$geyserVersion-SNAPSHOT")

    // Geyser Core - we use things not exposed in the API
    compileOnly("org.geysermc.geyser:core:$geyserVersion-SNAPSHOT")

    implementation("dev.kastle.netty:netty-warden-signalling:0.1.0-registration-dev")

    // The NetherNet Netty transport
    implementation("dev.kastle.netty:netty-transport-nethernet:$netherNetVersion")

    // The WebRTC library and its natives
    implementation("dev.ziax.warden:libdatachannel-java:$libdatachannelVersion")
    nativePlatforms.forEach { platform ->
        runtimeOnly("dev.ziax.warden:libdatachannel-java:$libdatachannelVersion:$platform")
    }

    // Configurate
    annotationProcessor("org.spongepowered:configurate-extra-interface-ap:$configurateVersion")
    implementation("org.spongepowered:configurate-extra-interface:$configurateVersion")
    implementation("org.spongepowered:configurate-yaml:$configurateVersion")
}

// Java currently requires Java 21 or higher, so extensions should also target it
java {
    targetCompatibility = JavaVersion.VERSION_21
    sourceCompatibility = JavaVersion.VERSION_21
}

afterEvaluate {
    val idRegex = Regex("[a-z][a-z0-9-_]{0,63}")
    if (idRegex.matches(id).not()) {
        throw IllegalArgumentException("Invalid extension id $id! Must only contain lowercase letters, " +
                "and cannot start with a number.")
    }

    val nameRegex = Regex("^[A-Za-z_.-]+$")
    if (nameRegex.matches(extensionName).not()) {
        throw IllegalArgumentException("Invalid extension name $extensionName! Must fit regex: ${nameRegex.pattern})")
    }
}

tasks.test { useJUnitPlatform() }

tasks {
    // This automatically fills in the extension.yml file.
    processResources {
        filesMatching("extension.yml") {
            expand(
                "id" to id,
                "name" to extensionName,
                "api" to geyserVersion,
                "version" to version,
                "author" to author
            )
        }
    }

    jar {
        enabled = false
    }

    shadowJar {
        filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
        mergeServiceFiles()
        manifest.attributes["Registration-Revision"] = providers.gradleProperty("registrationRevision").getOrElse("local-development")
        manifest.attributes["Registration-Network-Revision"] = networkPin
        manifest.attributes["Native-Network-Revision"] = networkPin
        manifest.attributes["Native-JNI-Revision"] = "5544964002162d184bacfcd0cb8d70d86ec3f271"
        dependencies {
            // Exclude netty apart from the http codec
            exclude {
                it.moduleGroup == "io.netty" && (it.moduleName != "netty-codec-http" && it.moduleName != "netty-handler")
            }

            exclude {
                it.moduleGroup == "org.slf4j"
            }
        }

        archiveClassifier.set("")
        archiveVersion.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}

fun Project.relocate(pattern: String) {
    tasks.named<ShadowJar>("shadowJar") {
        relocate(pattern, "org.geyser.extension.nethernet.shaded.$pattern")
    }
}
