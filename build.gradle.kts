buildscript {
    repositories {
        maven("publisher/build/prebuilt")
        mavenCentral()
        jcenter()
        google()
    }
    dependencies {
        classpath("io.deepmedia.tools:publisher:0.5.0-rc03")
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
