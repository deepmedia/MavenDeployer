/*
 * Copyright (c) 2020 Otalia Studios. Author: Mattia Iavarone.
 */

plugins {
    `kotlin-dsl`

    // To publish the plugin itself...
    id("org.jetbrains.dokka")
    id("maven-publisher-bintray")
}

dependencies {
    api("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.4")
    /* Depend on the android gradle plugin, since we want to access it in our plugin */
    api("com.android.tools.build:gradle:3.6.1")
    /* Depend on the kotlin plugin, since we want to access it in our plugin */
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.61")
    /* Depend on the default Gradle API's since we want to build a custom plugin */
    api(gradleApi())
    api(gradleKotlinDsl())
    api(localGroovy())
}

// To publish the plugin itself...

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

val dokkaJar by tasks.registering(Jar::class) {
    val dokka = tasks["dokka"] as org.jetbrains.dokka.gradle.DokkaTask
    dependsOn(dokka)
    archiveClassifier.set("javadoc")
    from(dokka.outputDirectory)
}

publisher {
    auth.user = "BINTRAY_USER"
    auth.key = "BINTRAY_KEY"
    auth.repo = "BINTRAY_REPO"
    project.artifact = "publisher"
    project.description = "A lightweight, handly tool for publishing maven packages to different kinds of repositories"
    project.group = "com.otaliastudios.tools"
    project.url = "https://github.com/natario1/MavenPublisher"
    project.vcsUrl = "https://github.com/natario1/MavenPublisher.git"
    project.addLicense(com.otaliastudios.tools.publisher.PublisherExtension.License.APACHE_2_0)
    release.version = "0.1.4"
    release.setSources(sourcesJar.get())
    release.setDocs(dokkaJar.get())
}