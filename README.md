[![Build Status](https://github.com/deepmedia/MavenPublisher/workflows/Build/badge.svg?event=push)](https://github.com/deepmedia/MavenPublisher/actions)
[![Release](https://img.shields.io/github/release/deepmedia/MavenPublisher.svg)](https://github.com/deepmedia/MavenPublisher/releases)
[![Issues](https://img.shields.io/github/issues-raw/deepmedia/MavenPublisher.svg)](https://github.com/deepmedia/MavenPublisher/issues)

# MavenPublisher

A lightweight, handy tool for publishing your maven packages (for example, Android AARs, Java JARs, Kotlin KLibs)
to different kinds of maven repositories. It supports publishing into:
- local directories, to use them as local maven repositories in other projects
- Sonatype Nexus repositories, including [Sonatype OSSRH / Maven Central](https://central.sonatype.org/)
- Bintray's [JCenter](https://bintray.com), soon to be deprecated

To use any of the publisher plugins, you must configure the plugin repository in your build script:

```kotlin
buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("io.deepmedia.tools:publisher:0.5.0")
    }
}
```

The publisher plugin uses an older version of itself to publish itself into the Maven Central repository.
This means that you can check the plugin source code to see an example of how to use it.

For more examples, please take a look at [natario1/Egloo](https://github.com/natario1/Egloo), [natario1/Firestore](https://github.com/natario1/Firestore) or [natario1/Elements](https://github.com/natario1/Elements).

## Usage

To apply the plugin, declare it in your build script with the `io.deepmedia.tools.publisher` id:

```groovy
apply plugin: 'io.deepmedia.tools.publisher'
```

### Base Configuration

All publishers are configured with the same common fields, although specific publisher
implementations can have extra fields (for example, for authentication). The common configuration
is specified at the root level of the `publisher` block:

```kotlin
publisher {
    // Project name. Defaults to rootProject.name
    project.name = "MavenPublisher"
    
    // Project description. Can be null
    project.description = "Handy tool to publish maven packages in different repositories."
    
    // Package artifact. Defaults to project's archivesBaseName
    project.artifact = "publisher"
    
    // Package group id. Defaults to project's group
    project.group = "io.deepmedia.tools"
    
    // Project url
    project.url = "https://github.com/deepmedia/MavenPublisher"
    
    // Project SCM info. Defaults to simple Scm pointing to project.url
    // Using platform specific functions ensure correct scm values
    project.scm = Scm("https://github.com/deepmedia/MavenPublisher.git")
    project.scm = GithubScm(user = "deepmedia", repository = "MavenPublisher")
    project.scm = BitBucketScm(user = "deepmedia", repository = "MavenPublisher")

    // Project packaging. Automatically set to AAR for Android libraries
    project.packaging = "aar"
    
    // Project licenses
    project.addLicense(License.APACHE_2_0)
    project.addLicense("My license", "https://mylicense.org")
    
    // Release version. Defaults to project's version
    release.version = "0.1.4"
    
    // Release VCS tag. Defaults to "v${release.version}"
    release.tag = "v0.1.4"
    
    // Release description. Defaults to "${project.name} {release.tag}"
    release.description = "New release"
    
    // Release sources
    release.setSources(Release.SOURCES_AUTO) // creates a sources Jar
    release.setSources(sourcesJar.get())
    
    // Release docs
    release.setDocs(Release.DOCS_AUTO) // create a docs Jar
    release.setDocs(dokkaJar.get())

    // Signing keys
    signing.key = "signing.key"
    signing.password = "signing.password"
}
```

Actual publishers can then be registered in this block by using their respective functions,
for example:

```kotlin
publisher {
    // Common configuration...
    project.name = "Project name"
    project.description = "Project description"

    bintray {
        // Override some fields or add missing ones
        project.name = "Project name for bintray"
        release.version = "1.0.0-nightly"
    }

    directory {
        // Override some fields or add missing ones
        project.description = "Project description for local directory"
        release.version = "1.0.0-rc1"
    }
}
```

### Specifying the publication contents

You have two options to specify the publication contents. MavenPublisher will try to infer default
values based on the currently applied plugin.

- `publisher.component` : specifies the name of a `SoftwareComponent`. Defaults to "release" for Android projects and "java" for java projects, both of which create components for you.
- `publisher.publication` : specifies the name of a `MavenPublication`. Defaults to null. When this is set, we'll use the publication's `SoftwareComponent` and modify the other publication fields to match the publisher configuration.

Typically, you should specify either one or the other.

### Secret values

Sensitive values in the `auth` and `signing` configuration blocks are declared as secret.
In this case, instead of passing the real value, you are supposed to pass a **key** to the real
value.

The publisher will use this key and look for the value as follows:

- Look in environment variables
- Search with `project.findProperty(key)`
- Search in `local.properties` file, if present

In other words, `signing.password` - for example - should not host your password in plain text, but rather a key
to the actual password, like the name of an environment variable which holds the password.

When the key value is absent, we assume it is equal to the field name (`"signing.password"`). This
means that you can avoid configuring these values at all in Gradle, and just provide env variables
or properties with the correct name (`"signing.password"`, `"signing.key"`, `"auth.user"`...).

### Publication tasks

The publisher plugin will add a task named `:publishTo` followed by the publication name.
The publication name depends on the publisher being used (for example, sonatype) and the name
that is passed to the configuration. For example:

```kotlin
publisher {
    // Common configuration ...

    sonatype {
        // Sonatype configuration...
    }
    sonatype("foo") {
        // Sonatype configuration for foo...
    }
    sonatype("bar") {
        // Sonatype configuration for bar...
    }
    directory {
        // Local dir configuration...
    }
    directory("abc") {
        // Local dir configuration...
    }
}
```

In the example above, the following tasks will be available:

- `:publishToSonatype`: publishes the default sonatype configuration
- `:publishToSonatypeFoo`: publishes the `foo` sonatype configuration
- `:publishToSonatypeBar`: publishes the `bar` sonatype configuration
- `:publishToDirectory`: publishes the default directory configuration
- `:publishToDirectoryAbc`: publishes the `abc` sonatype configuration
- `:publishAllSonatype`: publishes all "sonatype" publications
- `:publishAllDirectory`: publishes all "directory" publications
- `:publishAll`: publishes everything

## Local repository publisher

In addition to the common configuration fields, the local publisher will ask you for a path to the
local directory where the final package should be published. It can be set as follows:

```kotlin
publisher {
    // Common configuration...
    directory {
        // Directory configuration...
        directory = "build/output"
    }

    // If needed, you can add other named publications.
    directory("other") { ... }
}
```

As described earlier, all the configuration fields that are set in the `bintray` block will override
the root level values. Note also that all `signing` fields are [Secret values](#secret-values)!

If the directory is not set, the local publisher will publish into the "maven local" repository,
typically `$USER_HOME/.m2/repository` (see [docs](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.dsl.RepositoryHandler.html#org.gradle.api.artifacts.dsl.RepositoryHandler:mavenLocal())).

## Sonatype / Nexus / Maven Central publisher

By default, the sonatype publisher will publish packages at `https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/`,
in the Sonatype OSSRH which can be synced to Maven Central. In this case, in addition to the common
configuration fields, you will need to pass your username and password that were used to register
the package group in OSSRH in order to authenticate.

Note that publishing to OSSRH also makes many fields (like a license and at least a developer) mandatory.
MavenPublisher will fail with clear errors notifying you about what's missing.

```kotlin
publisher {
    // Common configuration...
    project.description = "Handy tool to publish maven packages in different repositories."

    sonatype {
        // Sonatype configuration...
        // You can find repository constants in io.deepmedia.tools.publisher.sonatype.Sonatype.
        // To publish a snapshot, just use one of the Sonatype.OSSRH_SNAPSHOT_* urls.
        repository = Sonatype.OSSRH_1

        auth.user = "SONATYPE_USER" // defaults to "auth.user"
        auth.password = "SONATYPE_PASSWORD" // defaults to "auth.password"

        // Signing is required
        signing.key = "SIGNING_KEY" // defaults to "signing.key"
        signing.password = "SIGNING_PASSWORD" // defaults to "signing.password"
    }

    // If needed, you can add other named publications.
    sonatype("snapshot") {
        repository = Sonatype.OSSRH_SNAPSHOT_1
        ...
    }
}
```

As described earlier, all the configuration fields that are set in the `bintray` block will override
the root level values. Note also that all `auth` and `signing` fields are [Secret values](#secret-values)!

## Bintray publisher

In addition to the common configuration fields, to authenticate to Bintray, you will need to pass
a user name, a user key and the Bintray repo name as follows:

```kotlin
publisher {
    // Common configuration...
    project.description = "Handy tool to publish maven packages in different repositories."

    bintray {
        // Bintray configuration...
        auth.user = "BINTRAY_USER" // defaults to "auth.user"
        auth.key = "BINTRAY_KEY" // defaults to "auth.key"
        auth.repo = "BINTRAY_REPO" // defaults to "auth.repo"
        dryRun = false // true for a dry-run publication for testing
    }

    // If needed, you can add other named publications.
    bintray("other") { ... }
}
```

As described earlier, all the configuration fields that are set in the `bintray` block will override
the root level values. Note also that all `auth` and `signing` fields are [Secret values](#secret-values)!
