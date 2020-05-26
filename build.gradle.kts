buildscript {
    repositories {
        maven("publisher/build/prebuilt")
        jcenter()
        google()
    }
    dependencies {
        classpath("com.otaliastudios.tools:publisher:0.3.0-rc1")
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
