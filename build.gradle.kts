buildscript {
    repositories {
        maven("publisher/build/prebuilt")
        maven("https://dl.bintray.com/deepmedia/tools")
        jcenter()
        google()
    }
    dependencies {
        classpath("io.deepmedia.tools:publisher:0.5.0-st2")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        jcenter()
    }
}

tasks.register("clean", Delete::class) {
    delete(buildDir)
}
