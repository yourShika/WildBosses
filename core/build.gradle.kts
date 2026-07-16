plugins {
    id("com.gradleup.shadow")
}

val paperVersion: String by project
val placeholderApiVersion: String by project

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("me.clip:placeholderapi:$placeholderApiVersion")
}

tasks {
    named<Jar>("jar") {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveBaseName.set("WildBosses")
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
