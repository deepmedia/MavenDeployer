package io.deepmedia.tools.publisher.bintray

import io.deepmedia.tools.publisher.common.Auth

class BintrayAuth : Auth() {
    var repo: String? = null
    var user: String? = null
    var key: String? = null
}