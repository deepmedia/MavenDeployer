buildscript {
    repositories {
        jcenter()
        google()
        maven {
            setUrl("https://dl.bintray.com/natario/tools")
        }
    }
    dependencies {
        // classpath("org.jetbrains.dokka:dokka-gradle-plugin:0.9.17")
        classpath("com.otaliastudios.tools:publisher:0.1.5")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        jcenter()
    }
}

tasks.create("clean", Delete::class) {
    delete(buildDir)
}
