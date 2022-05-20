buildscript {
    repositories {
        maven("publisher/build/prebuilt")
        val localProperties = file("local.properties")
        if (localProperties.exists()) {
            val map = java.util.Properties().apply {
                localProperties.inputStream().use { load(it) }
            }
            val user = map.getProperty("GITHUB_USER")
            val token = map.getProperty("GITHUB_PERSONAL_ACCESS_TOKEN")
            if (user.isNotEmpty() && token.isNotEmpty()) {
                maven {
                    url = uri("https://maven.pkg.github.com/deepmedia/MavenPublisher")
                    credentials.username = user
                    credentials.password = token
                }
            }
        }
        mavenCentral()
        google()
    }
    dependencies {
        classpath("io.deepmedia.tools:publisher:0.6.0")
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
