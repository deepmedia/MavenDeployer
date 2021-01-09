package io.deepmedia.tools.publisher.bintray

import io.deepmedia.tools.publisher.common.DefaultPublication

class BintrayPublication(name: String) : DefaultPublication(name) {
    var dryRun = false
    override val auth = BintrayAuth()
}