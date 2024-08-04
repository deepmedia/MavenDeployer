@file:Suppress("UnstableApiUsage")

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("io.deepmedia.tools.deployer") version "0.14.0-alpha1"
    kotlin("plugin.serialization") version "1.9.23"
    id("org.jetbrains.dokka") version "1.9.20"
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.0.2")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")

    // api("org.jetbrains.dokka:dokka-gradle-plugin:1.8.20")

    implementation("io.ktor:ktor-client-core:2.3.11")
    implementation("io.ktor:ktor-client-cio:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
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
version = "0.14.0-alpha2" // on change, update both docs and README

val javadocs = tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

deployer {
    verbose = true

    content {
        gradlePluginComponents {
            kotlinSources()
            docs(javadocs)
        }
    }

    projectInfo {
        description = "A lightweight, handy tool for publishing maven / Gradle packages to different kinds of repositories."
        url = "https://github.com/deepmedia/MavenDeployer"
        scm.fromGithub("deepmedia", "MavenDeployer")
        license(apache2)
        developer("natario1", "mattia@deepmedia.io", "DeepMedia", "https://deepmedia.io")
    }

    signing {
        key = secret("SIGNING_KEY")
        password = secret("SIGNING_PASSWORD")
    }

    // use "deployLocal" to deploy to local maven repository
    localSpec {
        // directory.set(rootProject.layout.buildDirectory.get().dir("inspect"))
    }

    // use "deployNexus" to deploy to OSSRH / maven central
    nexusSpec {
        auth.user = secret("SONATYPE_USER")
        auth.password = secret("SONATYPE_PASSWORD")
        syncToMavenCentral = true
    }

    // use "deployNexusSnapshot" to deploy to sonatype snapshots repo
    nexusSpec("snapshot") {
        auth.user = secret("SONATYPE_USER")
        auth.password = secret("SONATYPE_PASSWORD")
        repositoryUrl = ossrhSnapshots1
        release.version = "latest-SNAPSHOT"
    }

    // use "deployGithub" to deploy to github packages
    githubSpec {
        repository = "MavenDeployer"
        owner = "deepmedia"
        auth {
            user = secret("GHUB_USER")
            token = secret("GHUB_PERSONAL_ACCESS_TOKEN")
        }
    }

    // Just for testing centralPortal
    /* centralPortalSpec {
        auth.user = secret("CENTRAL_PORTAL_USERNAME")
        auth.password = secret("CENTRAL_PORTAL_PASSWORD")
        allowMavenCentralSync = false
        projectInfo.groupId.set("io.github.natario1")
        content {
            inherit.set(false)
            component {
                fromMavenPublication("pluginMaven", clone = true)
                packaging.set("jar")
                kotlinSources()
                emptyDocs()
            }

        }
    } */
}
