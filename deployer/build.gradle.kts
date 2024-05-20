import io.deepmedia.tools.deployer.impl.SonatypeAuth
import io.deepmedia.tools.deployer.model.DeploySpec
import io.deepmedia.tools.deployer.model.Secret
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.targets

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("io.deepmedia.tools.deployer") version "0.10.0-rc1"
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.0.2")
    // compileOnly("com.android.tools.build:gradle:7.2.2")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
    // api("org.jetbrains.dokka:dokka-gradle-plugin:1.7.10")
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
version = "0.10.0"

deployer {
    verbose.set(true)

    defaultSpec {
        projectInfo {
            description.set("A lightweight, handy tool for publishing maven / Gradle packages to different kinds of repositories.")
            url.set("https://github.com/deepmedia/MavenDeployer")
            scm.fromGithub("deepmedia", "MavenDeployer")
            license(apache2)
            developer("natario1", "mattia@deepmedia.io", "DeepMedia", "https://deepmedia.io")
        }
        content.autoDocs()
        content.autoSources()

        signing {
            key.set(secret("SIGNING_KEY"))
            password.set(secret("SIGNING_PASSWORD"))
        }
    }

    // use "deployLocal" to deploy to local maven repository
    localSpec()

    val sonatypeAuth: SonatypeAuth.() -> Unit = {
        user.set(secret("SONATYPE_USER"))
        password.set(secret("SONATYPE_PASSWORD"))
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
        repository.set("MavenDeployer")
        owner.set("deepmedia")
        auth {
            user.set(secret("GHUB_USER"))
            token.set(secret("GHUB_PERSONAL_ACCESS_TOKEN"))
        }
    }
}

/* val deployLegacy by tasks.registering {
    dependsOn("publishPluginMavenPublicationToMavenLocal")
    dependsOn("publishDeployerPluginMarkerMavenPublicationToMavenLocal")
} */
