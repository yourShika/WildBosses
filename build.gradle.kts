plugins {
    // Applied in :core; declared here (apply false) so the version is shared.
    id("com.gradleup.shadow") version "9.5.1" apply false
}

allprojects {
    group = "com.yourshika"
    version = rootProject.version
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
