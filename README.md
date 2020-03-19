[![Build Status](https://github.com/natario1/MavenPublisher/workflows/Build/badge.svg?event=push)](https://github.com/natario1/MavenPublisher/actions)
[![Release](https://img.shields.io/github/release/natario1/MavenPublisher.svg)](https://github.com/natario1/MavenPublisher/releases)
[![Issues](https://img.shields.io/github/issues-raw/natario1/MavenPublisher.svg)](https://github.com/natario1/MavenPublisher/issues)

&#10240;  <!-- Hack to add whitespace -->

*Need support, consulting, or have any other business-related question? Feel free to <a href="mailto:mat.iavarone@gmail.com">get in touch</a>.*

*Like the project, make profit from it, or simply want to thank back? Please consider [sponsoring me](https://github.com/sponsors/natario1)!*

# MavenPublisher

A lightweight, handly tool for publishing your maven packages (for example, Android AARs or Java JARs)
to different kinds of repositories. Currently, only [Bintray](https://bintray.com) is supported.

To use any of the publisher plugins, you must configure the plugin repository in your build script:

```kotlin
buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.otaliastudios.tools:publisher:0.1.5")
    }
}
```

The publisher plugin uses an older version of itself to publish itself into Bintray's JCenter.
This means that you can check the plugin source code to see an example of how to use it.

## Usage

### Base Configuration

All publisher plugins are configured with the same common fields, although specific publisher
implementations can have extra fields (for example, for authentication).

```kotlin
publisher {
    // Project name. Defaults to rootProject.name
    project.name = "MavenPublisher"
    // Project description. Can be null
    project.description = "Handy tool to publish maven packages in different repositories."
    // Package artifact. Defaults to project's archivesBaseName
    project.artifact = "publisher"
    // Package group id. Defaults to project's group
    project.group = "com.otaliastudios.tools"
    // Project url
    project.url = "https://github.com/natario1/MavenPublisher"
    // Project VCS url. Defaults to project.url
    project.vcsUrl = "https://github.com/natario1/MavenPublisher.git"
    // Project packaging. Automatically set to AAR for Android libraries
    project.packaging = "aar"
    // Project licenses
    project.addLicense(License.APACHE_2_0)
    project.addLicense("My license", "https://mylicense.org")
    // Release version. Defaults to project's version
    release.version = "0.1.4"
    // Release VCS tag. Defaults to "v${release.version}"
    release.vcsTag = "v0.1.4"
    // Release description. Defaults to "${project.name} {release.vcsTag}"
    release.description = "New release"
    // Release sources
    release.setSources(sourcesJar.get())
    // Release docs
    release.setDocs(dokkaJar.get())
    // Publication name. Default value depends on the publisher implementation.
    publication = "bintray"
}
```

### Secret values

Some sensitive values, especially in the auth configuration, are declared as secret.
In this case, instead of passing the real value, you are supposed to pass a **key** to the real
value.

The publisher will use this key and look for the value as follows:

- Look in environment variables
- Search with `project.findProperty(key)`
- Search in `local.properties` file, if present

### Publication

The publisher plugin will add a task named `:publishTo` followed by the publication name.
The publication name can be set in the configuration, but is typically chosen by the
publisher implementation.

For example, the bintray publisher will add a task named `:publishToBintray`.

## Bintray publisher

To apply the plugin, declare it in your build script with the `maven-publisher-bintray` id:

```groovy
apply plugin: 'maven-publisher-bintray'
```

In addition to the common configuration fields, to authenticate to Bintray, you will need to pass
a user name, a user key and the Bintray repo name as follows:

```kotlin
publisher {
    // Bintray authentication
    auth.user = "BINTRAY_USER" // defaults to "auth.user"
    auth.key = "BINTRAY_KEY" // defaults to "auth.key"
    auth.repo = "BINTRAY_REPO" // defaults to "auth.repo"
    // Other configuration values
    // ...
}
```

Note that the `auth` values are [Secret values](#secret-values), so instead of passing the real
value in plain text, a key (to an environment variable or local property) should be passed.

Since there is a default key, there is no need to declare your own keys in the plugin configuration -
you can simply provide values for the default keys (for example, as env properties).
