plugins {
    id("com.gradleup.shadow")
}

val paperVersion: String by project
val placeholderApiVersion: String by project

// A dedicated configuration used ONLY to bundle the optional :bettermodel module's
// compiled classes into the final plugin jar. It is NOT on core's compileClasspath,
// so there is no circular dependency: core never references BetterModel classes at
// compile time (the adapter is loaded reflectively at runtime).
val bettermodelHook: Configuration by configurations.creating

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("me.clip:placeholderapi:$placeholderApiVersion")
    bettermodelHook(project(":bettermodel"))
}

tasks {
    named<Jar>("jar") {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveBaseName.set("WildBosses")
        archiveClassifier.set("")
        // Merge the optional BetterModel adapter module into the single plugin jar.
        configurations = listOf(bettermodelHook)
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
