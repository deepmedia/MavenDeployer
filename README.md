[![Build Status](https://github.com/deepmedia/MavenDeployer/actions/workflows/build.yml/badge.svg?event=push)](https://github.com/deepmedia/MavenDeployer/actions)
[![Release](https://img.shields.io/github/release/deepmedia/MavenDeployer.svg)](https://github.com/deepmedia/MavenDeployer/releases)
[![Issues](https://img.shields.io/github/issues-raw/deepmedia/MavenDeployer.svg)](https://github.com/deepmedia/MavenDeployer/issues)

![Project logo](assets/logo.svg)

# MavenDeployer

A lightweight, handy Gradle plugin to deploy your maven packages (for example, Android AARs, Java JARs, Kotlin KLibs)
to different kinds of repositories. It supports publishing to:
- local directories, to use them as local maven repositories in other projects
- [Maven Central](https://central.sonatype.com/) repository via Sonatype's OSSRH
- [Maven Central](https://central.sonatype.com/) repository via Sonatype's [Central Portal](https://central.sonatype.org/register/central-portal/)
- Other Sonatype Nexus repositories
- [GitHub Packages](https://docs.github.com/en/packages)

> For Maven Central builds, the plugin takes care of releasing the artifacts using Sonatype REST APIs so you don't have to use their web UI. 


It supports automatic configuration for a certain set of projects:

- [Android Projects](https://opensource.deepmedia.io/deployer/artifacts#android-projects)
- [Kotlin Projects](https://opensource.deepmedia.io/deployer/artifacts#kotlin-regular-projects)
- [Kotlin Multiplatform Projects](https://opensource.deepmedia.io/deployer/artifacts#kotlin-multiplatform-projects)
- [Gradle Plugin Projects](https://opensource.deepmedia.io/deployer/artifacts#gradle-plugin-projects)

In addition, you may configure deployments manually based on some existing `SoftwareComponent`, `MavenPublication` or simple file artifacts.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// build.gradle.kts of deployable modules
plugins {
    id("io.deepmedia.tools.deployer") version "0.15.0-alpha1"
}
```

Please check out [the documentation](https://opensource.deepmedia.io/deployer).
