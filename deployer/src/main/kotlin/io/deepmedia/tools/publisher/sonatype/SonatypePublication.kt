package io.deepmedia.tools.publisher.sonatype

import io.deepmedia.tools.publisher.common.DefaultPublication

class SonatypePublication(name: String) : DefaultPublication(name) {

    var repository = Sonatype.OSSRH_1

    override val auth = SonatypeAuth()
}