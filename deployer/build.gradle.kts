@file:Suppress("UnstableApiUsage")

import io.deepmedia.tools.deployer.impl.SonatypeAuth
import io.deepmedia.tools.deployer.model.DeploySpec
import io.deepmedia.tools.deployer.model.Secret
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinOnlyTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.targets

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("io.deepmedia.tools.deployer") version "0.10.1-rc11"
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.0.2")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
    api("org.jetbrains.dokka:dokka-gradle-plugin:1.8.20")
}

// Gradle 7.X has embedded kotlin version 1.6, but kotlin-dsl plugins are compiled with 1.4 for compatibility with older
// gradle versions (I guess). 1.4 is very old and generates a warning, so let's bump to the embedded kotlin version.
// https://handstandsam.com/2022/04/13/using-the-kotlin-dsl-gradle-plugin-forces-kotlin-1-4-compatibility/
// https://github.com/gradle/gradle/blob/7a69f2f3d791044b946040cd43097ce57f430ca8/subprojects/kotlin-dsl-plugins/src/main/kotlin/org/gradle/kotlin/dsl/plugins/dsl/KotlinDslCompilerPlugins.kt#L48-L49
/* afterEvaluate {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            val embedded = embeddedKotlinVersion.split(".").take(2).joinToString(".")
            apiVersion = embedded
            languageVersion = embedded
        }
    }
} */

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
version = "0.11.0-rc1" // on change, update both docs and README

deployer {
    verbose = true

    content {
        gradlePluginComponents {
            kotlinSources()
            emptyDocs()
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
        // directory.set(layout.buildDirectory.get().dir("inspect"))
    }

    // use "deploySonatype" to deploy to OSSRH / maven central
    sonatypeSpec {
        auth.user = secret("SONATYPE_USER")
        auth.password = secret("SONATYPE_PASSWORD")
    }

    // use "deploySonatypeSnapshot" to deploy to sonatype snapshots repo
    sonatypeSpec("snapshot") {
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
}
