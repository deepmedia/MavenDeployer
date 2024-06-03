[![Build Status](https://github.com/deepmedia/MavenDeployer/workflows/Build/badge.svg?event=push)](https://github.com/deepmedia/MavenDeployer/actions)
[![Release](https://img.shields.io/github/release/deepmedia/MavenDeployer.svg)](https://github.com/deepmedia/MavenDeployer/releases)
[![Issues](https://img.shields.io/github/issues-raw/deepmedia/MavenDeployer.svg)](https://github.com/deepmedia/MavenDeployer/issues)

![Project logo](assets/logo.svg)

# MavenDeployer

A lightweight, handy Gradle plugin to deploy your maven packages (for example, Android AARs, Java JARs, Kotlin KLibs)
to different kinds of repositories. It supports publishing to:
- local directories, to use them as local maven repositories in other projects
- Sonatype Nexus repositories, including [Sonatype OSSRH / Maven Central](https://central.sonatype.org/)
- [GitHub Packages](https://docs.github.com/en/packages)

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
    id("io.deepmedia.tools.deployer") version "0.11.0"
}
```

Please check out [the documentation](https://opensource.deepmedia.io/deployer).
