pluginManagement {
    val props = java.util.Properties().apply {
        file("local.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }

    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()

        val user: String? = "GHUB_USER".let { props.getProperty(it) ?: System.getenv(it) }
        val token: String? = "GHUB_PERSONAL_ACCESS_TOKEN".let { props.getProperty(it) ?: System.getenv(it) }
        if (!user.isNullOrEmpty() && !token.isNullOrEmpty()) {
            maven {
                url = uri("https://maven.pkg.github.com/deepmedia/MavenPublisher")
                credentials.username = user
                credentials.password = token
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":deployer")
