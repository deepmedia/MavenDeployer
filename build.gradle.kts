buildscript {
    repositories {
        maven("publisher/build/prebuilt")
        mavenCentral()
        jcenter() // TODO remove when we bump publisher to 0.6.0 in this file
        google()
    }
    dependencies {
        classpath("io.deepmedia.tools:publisher:0.5.0")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        jcenter() // TODO remove when we bump publisher to 0.6.0 in this file
    }
}

tasks.register("clean", Delete::class) {
    delete(buildDir)
}
