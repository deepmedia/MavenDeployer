import io.deepmedia.tools.deployer.impl.SonatypeAuth

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("io.deepmedia.tools.deployer") version "0.8.0-rc01"
}

dependencies {
    compileOnly("com.android.tools.build:gradle:7.0.4")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
    api("org.jetbrains.dokka:dokka-gradle-plugin:1.6.20")
}

// To publish the plugin itself...

gradlePlugin {
    isAutomatedPublishing = true
    plugins {
        create("deployer") {
            id = "io.deepmedia.tools.deployer"
            implementationClass = "io.deepmedia.tools.deployer.DeployerPlugin"
        }
    }
}

group = "io.deepmedia.tools.deployer"
version = "0.8.0-rc03"

deployer {
    defaultSpec {
        projectInfo {
            description.set("A lightweight, handy tool for publishing maven / Gradle packages to different kinds of repositories.")
            url.set("https://github.com/deepmedia/MavenPublisher")
            scm.fromGithub("deepmedia", "MavenPublisher")
            license(apache2)
            developer("natario1", "mattia@deepmedia.io", "DeepMedia", "https://deepmedia.io")
        }
        signing {
            key.set("SIGNING_KEY")
            password.set("SIGNING_PASSWORD")
        }
    }

    // use "deployLocal" to deploy to local maven repository
    localSpec()

    val sonatypeAuth: SonatypeAuth.() -> Unit = {
        user.set("SONATYPE_USER")
        password.set("SONATYPE_PASSWORD")
    }

    // use "deploySonatype" to deploy to OSSRH / maven central
    sonatypeSpec {
        auth.sonatypeAuth()
    }

    // use "deploySonatypeSnapshot" to deploy to sonatype snapshots repo
    sonatypeSpec("snapshot") {
        auth.sonatypeAuth()
        repositoryUrl.set(ossrhSnapshots1)
        release.version.set("latest-SNAPSHOT")
    }

    // use "deployGithub" to deploy to github packages
    githubSpec {
        repository.set("MavenPublisher")
        owner.set("deepmedia")
        auth.user.set("GHUB_USER")
        auth.token.set("GHUB_PERSONAL_ACCESS_TOKEN")
    }
}

/* val deployLegacy by tasks.registering {
    dependsOn("publishPluginMavenPublicationToMavenLocal")
    dependsOn("publishPublisherPluginMarkerMavenPublicationToMavenLocal")
    dependsOn("publishDeployerPluginMarkerMavenPublicationToMavenLocal")
} */
