import io.deepmedia.tools.publisher.common.*
import io.deepmedia.tools.publisher.sonatype.Sonatype

plugins {
    `kotlin-dsl`
    // To publish the plugin itself...
    id("io.deepmedia.tools.publisher")
}

dependencies {
    api("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.4") // bintray
    api("com.android.tools.build:gradle:4.1.1") // android gradle plugin
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.21") // kotlin gradle plugin
    api("org.jetbrains.dokka:dokka-gradle-plugin:1.4.20") // dokka for auto docs
    api(gradleApi()) // gradle
    api(gradleKotlinDsl()) // not sure if needed
    api(localGroovy()) // groovy
}

// To publish the plugin itself...

publisher {
    project.artifact = "publisher"
    project.description = "A lightweight, handy tool for publishing maven packages to different kinds of repositories."
    project.group = "io.deepmedia.tools"
    project.url = "https://github.com/deepmedia/MavenPublisher"
    project.scm = GithubScm("deepmedia", "MavenPublisher")
    project.addLicense(License.APACHE_2_0)
    project.addDeveloper(
        name = "natario1",
        email = "mattia@deepmedia.io",
        organization = "DeepMedia",
        url = "deepmedia.io"
    )

    release.version = "0.5.0"
    release.sources = Release.SOURCES_AUTO
    release.docs = Release.DOCS_AUTO

    sonatype {
        auth.user = "SONATYPE_USER"
        auth.password = "SONATYPE_PASSWORD"
        signing.key = "SIGNING_KEY"
        signing.password = "SIGNING_PASSWORD"
    }

    sonatype("snapshot") {
        repository = Sonatype.OSSRH_SNAPSHOT_1
        release.version = "SNAPSHOT"
        auth.user = "SONATYPE_USER"
        auth.password = "SONATYPE_PASSWORD"
        signing.key = "SIGNING_KEY"
        signing.password = "SIGNING_PASSWORD"
    }

    directory {
        directory = "build/prebuilt"
        signing.key = "SIGNING_KEY"
        signing.password = "SIGNING_PASSWORD"
    }

    bintray {
        auth.user = "BINTRAY_USER"
        auth.key = "BINTRAY_KEY"
        auth.repo = "BINTRAY_REPO"
    }
}