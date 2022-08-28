[![Build Status](https://github.com/deepmedia/MavenDeployer/workflows/Build/badge.svg?event=push)](https://github.com/deepmedia/MavenDeployer/actions)
[![Release](https://img.shields.io/github/release/deepmedia/MavenDeployer.svg)](https://github.com/deepmedia/MavenDeployer/releases)
[![Issues](https://img.shields.io/github/issues-raw/deepmedia/MavenDeployer.svg)](https://github.com/deepmedia/MavenDeployer/issues)

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
    id("io.deepmedia.tools.deployer") version "0.9.0"
}
```

The plugin uses an older version of itself to publish itself to the Maven Central repository
and to GitHub Packages. This means that you can check the plugin source code to see an example of how to use it.

It also uses itself to publish snapshots to `https://s01.oss.sonatype.org/content/repositories/snapshots/`
on each push to main. To use the snapshots, add the url as a maven repository and depend on
`id("io.deepmedia.tools.deployer") version "latest-SNAPSHOT"`.

# Usage

Plugin configuration happens through the `deployer` extension. You act on it by adding one or more `DeploySpec`s
and configuring them. In addition, the `defaultSpec` can be used to configure default values that will be propagated 
to all other specs.

```kotlin
deployer {
    defaultSpec {
        release.version.set("1.0.0")
    }

    sonatypeSpec {
        // release.version is 1.0.0
        ...
    }

    sonatypeSpec("snapshot") {
        release.version.set("1.0.0-SNAPSHOT") // override default value
        ...
    }
}
```

For each spec, the plugin will register a gradle task named `deploy<SpecType><SpecName>`. The spec type is either local,
github, or sonatype. The name defaults to `""` but can be configured. In addition, an extra task called `deployAll` will be
generated, running all deployments at once. In the example above, the following tasks are generated:

- `deploySonatype`
- `deploySonatypeSnapshot`
- `deployAll`

> **Note**: Use ./gradlew tasks --group='Deployment' to list all deploy tasks.

# Deploy contents

The contents of a deploy spec (e.g. AARs, JARs, additional artifacts like sources or javadocs) can be configured at `spec.content`.
Each spec can support multiple components, each one corresponding to a maven publication and pom file.

### Component inference

By default, the plugin will try to infer the spec components based on the project structure and plugins. We currently
support the following use cases:

1. Kotlin Multiplatform projects: enabled when the Kotlin Multiplatform plugin is applied. 
   The deploy spec will have one component per target, plus one component publishing the 'common' metadata.
2. Android libraries: enabled when `com.android.library` plugin is applied.
   The deploy spec will have a single component based on the release AAR.
3. Gradle Plugins: enabled when `java-gradle-plugin` plugin is applied and `gradlePlugin.isAutomatedPublishing` is true.
   The deploy spec will have one component for the project, and one gradle marker component for each plugin in `gradlePlugin.plugins`. 
4. Java projects: enabled when the java base plugin is applied.
   The deploy spec will have a single component from the `java` software component.

### Custom components

To declare custom components, simply add them to `spec.content`. Two types of custom components are supported:

- Based on an existing Gradle [SoftwareComponent](https://docs.gradle.org/current/javadoc/org/gradle/api/component/SoftwareComponent.html)
- Based on an existing Gradle [MavenPublication](https://docs.gradle.org/current/dsl/org.gradle.api.publish.maven.MavenPublication.html)

```kotlin
deployer {
    defaultSpec.content {
        softwareComponent("mySoftwareComponent") { ... }
        
        // cloning the publication makes sure that it can be shared across multiple specs.
        mavenPublication("myMavenPublication", clone = false) { ... }
        
        // other options: under the hood, they call softwareComponent or mavenPublication
        kotlinTarget(myKotlinTarget, clone = false) { ... }
        gradlePluginDeclaration(myPluginDeclaration, clone = false) { ... }
    }
}
```

### Component extras

The component builders mentioned above describe the main published artifact (for example, a jar with binary code).
It is possible however to add more files to the publication:

- sources: use `component.sources(mySourcesTask)`
- javadocs: use `component.docs(myJavadocTask)`
- other artifacts: use `component.extras.add(...)`

You can also leverage automatic sources and/or docs depending on your project. Use:

- `content.emptySources()` or `content.emptyDocs()` to create an empty JAR. Might be useful for some repositories that require these jars to be present.
- `content.autoSources()` to add a sources JAR, configured based on your project
- `content.autoDocs()` to add a documentation JAR, for Kotlin projects only (uses Dokka under the hood)

# Spec configuration

In addition to its contents, the `DeploySpec` interface offers a simple DSL to configure other common parameters of
a publication. Under the hood, these will be applied to the publication's POM file.

### Project info

Use the `projectInfo` property or configuration block:

```kotlin
// Inside a spec...
projectInfo {
   // Project name. Defaults to rootProject.name
   name.set("MavenDeployer")
   // Project description. Defaults to rootProject.name
   description.set("Handy tool to publish maven packages in different repositories.")
   // Project url
   url.set("https://github.com/deepmedia/MavenDeployer")
   // Package group id. Defaults to project's group
   groupId.set("io.deepmedia.tools")
   // Package artifact. Defaults to project's archivesName or project.name
   artifactId.set("deployer")
   // Project SCM information. Defaults to project.url
   scm {
       // or: fromGithub("deepmedia", "MavenDeployer")
       // or: fromBitbucket("deepmedia", "MavenDeployer")
       // or: set url, connection and developerConnection directly
   }
   // Licenses. Apache 2.0 and MIT are built-in
   license(apache2)
   license(MIT)
   license("MyLicense", "mylicense.com")
   // Developers
   developer("natario1", "mattia@deepmedia.io")
}
```

### Release details

Use the `release` property or configuration block:

```kotlin
// Inside a spec...
release {
   // Release version. Defaults to project.version, or AGP configured version for Android projects
   release.version.set("1.0.0")
   // Release VCS tag. Defaults to "v${release.version}"
   release.tag.set("v0.1.4")
   // Release description. Defaults to "${project.name} {release.tag}"
   release.description.set("Brand new release")
   // Release packaging. Automatically set to AAR for Android libraries
   project.packaging = "jar"
}
```

### Signing configuration

Use the `signing` property or configuration block:

```kotlin
// Inside a spec...
signing {
   key.set(secret("SIGNING_KEY"))
   password.set(secret("SIGNING_PASSWORD"))
}
```

The signing key and password are considered **secrets**. This means that you will not pass the actual value to
the deployer, but rather a lookup string. This lookup string can be:

- The name of some environment variables that contains the resolved secret
- The name of a Gradle property containing the resolved secret, resolved with `project.findProperty(lookup)`
- The name of a property in the `local.properties` file, if present

The resolved key and password are then passed to the `signing` plugin using [`useInMemoryPgpKeys`](https://docs.gradle.org/current/userguide/signing_plugin.html#sec:in-memory-keys),
to sign the publication artifacts.

# Supported repositories

Different types or repositories offer differ APIs to configure them, which is often mandatory. These options are 
exposed through subclasses of the `DeploySpec` type, so that, for example, a `localSpec { }` block will expose
`LocalDeploySpec` properties.

### Local repositories

Add a new spec with `localSpec {}`. The only configurable property is the directory into which to publish the artifacts:

```kotlin
deployer {
    localSpec {
        directory.set(file("my/dir/here"))
    }

    // If needed, you can add other named specs.
    localSpec("other") { ... }
}
```

If the directory is not set, this defaults to the `mavenLocal()` repository.

### Sonatype / Nexus / Maven Central repositories

Add a new spec with `sonatypeSpec {}`. It adds a mandatory property called `repositoryUrl`, which is the remote URL 
of the sonatype repo. This defaults to `https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/` -
the Sonatype OSSRH which can be synced to Maven Central.

Sonatype repositories make many fields mandatory:
- authentication: use the `auth` block as shown below
- signing
- valid project url and scm
- at least one developer and a license
- possibly more

Deployment will fail with clear errors notifying you about what's missing.

```kotlin
deployer {
    // Common configuration...
    project.description.set("Handy tool to publish maven packages in different repositories.")

    sonatypeSpec {
        // Sonatype configuration...
        repositoryUrl.set(ossrh1)
     
        // Credentials used to register the package group into the OSSRH repository...
        auth.user.set(secret("SONATYPE_USER")) 
        auth.password.set(secret("SONATYPE_PASSWORD"))

        // Signing is required
        signing.key.set(secret("SIGNING_KEY"))
        signing.password.set(secret("SIGNING_PASSWORD"))
    }

    // If needed, you can add other named specs.
    sonatypeSpec("snapshot") { 
        repositoryUrl.set(ossrhSnapshots1)
        release.version.set("latest-SNAPSHOT")
        ...
    }
}
```

### Github Packages repositories

Add a new spec with `githubSpec {}`. It adds two mandatory properties, `owner` and `repository`, which 
are used to identify the GitHub repository.

In addition to this, it is also mandatory to authenticate to GitHub using your username and a
[personal access token](https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages#about-scopes-and-permissions-for-package-registries).
These can be added as secrets to the `auth` block, as shown below.

```kotlin
deployer {
    // Common configuration...
    project.description.set("Handy tool to publish maven packages in different repositories.")

    githubSpec {
        // Identify the GitHub repository: deepmedia/MavenDeployer
        owner.set("deepmedia")
        repository.set("MavenDeployer")
       
        // Personal GitHub username and a personal access token linked to it
        auth.user.set(secret("GITHUB_USER"))
        auth.token.set(secret("GITHUB_TOKEN"))
    }

    // If needed, you can add other named specs.
    githubSpec("private") {
        ...
    }
}
```
