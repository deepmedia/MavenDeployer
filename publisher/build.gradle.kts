/*
 * Copyright (c) 2020 Otalia Studios. Author: Mattia Iavarone.
 */

import com.otaliastudios.tools.publisher.PublisherExtension

plugins {
    `kotlin-dsl`

    // To publish the plugin itself...
    id("maven-publisher-bintray")
    // id("maven-publisher-local")
}

dependencies {
    api("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.4") // bintray
    api("com.android.tools.build:gradle:3.6.1") // android gradle plugin
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.61") // kotlin gradle plugin
    api("org.jetbrains.dokka:dokka-gradle-plugin:0.10.1") // dokka for auto docs
    api(gradleApi()) // gradle
    api(gradleKotlinDsl()) // not sure if needed
    api(localGroovy()) // groovy
}

// To publish the plugin itself...

publisher {
    auth.user = "BINTRAY_USER"
    auth.key = "BINTRAY_KEY"
    auth.repo = "BINTRAY_REPO"
    project.artifact = "publisher"
    project.description = "A lightweight, handy tool for publishing maven packages to different kinds of repositories"
    project.group = "com.otaliastudios.tools"
    project.url = "https://github.com/natario1/MavenPublisher"
    project.vcsUrl = "https://github.com/natario1/MavenPublisher.git"
    project.addLicense(PublisherExtension.License.APACHE_2_0)
    release.version = "0.2.1"
    release.setSources(PublisherExtension.Release.SOURCES_AUTO)
    release.setDocs(PublisherExtension.Release.DOCS_AUTO)
}

/* localPublisher {
    directory = "build/prebuilt"
} */