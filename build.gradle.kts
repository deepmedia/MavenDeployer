buildscript {
    repositories {
        maven("publisher/build/prebuilt")
        val props = java.util.Properties().apply {
            file("local.properties")
                .takeIf { it.exists() }
                ?.inputStream()
                .use { load(it) }
        }
        val user: String? = "GITHUB_USER".let { props.getProperty(it) ?: System.getenv(it) }
        val token: String? = "GITHUB_PERSONAL_ACCESS_TOKEN".let { props.getProperty(it) ?: System.getenv(it) }
        if (!user.isNullOrEmpty() && !token.isNullOrEmpty()) {
            maven {
                url = uri("https://maven.pkg.github.com/deepmedia/MavenPublisher")
                credentials.username = user
                credentials.password = token
            }
        }
        mavenCentral()
        google()
    }
    dependencies {
        classpath("io.deepmedia.tools:publisher:0.7.0-rc1")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

tasks.register("clean", Delete::class) {
    delete(buildDir)
}
