package io.deepmedia.tools.publisher.sonatype

import io.deepmedia.tools.publisher.common.DefaultPublication

class SonatypePublication(name: String) : DefaultPublication(name) {
    // releases: https://s01.oss.sonatype.org/content/repositories/snapshots/
    // snapshots: https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/
    var repository = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"

    override val auth = SonatypeAuth()
}