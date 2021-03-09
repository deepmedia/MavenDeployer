package io.deepmedia.tools.publisher.sonatype

import io.deepmedia.tools.publisher.common.DefaultPublication

class SonatypePublication(name: String) : DefaultPublication(name) {
    override val auth = SonatypeAuth()
}