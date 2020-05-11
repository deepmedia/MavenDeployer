buildscript {
    repositories {
        maven("publisher/build/prebuilt")
        jcenter()
        google()
    }
    dependencies {
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
