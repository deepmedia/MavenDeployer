package io.deepmedia.tools.publisher.sonatype

import io.deepmedia.tools.publisher.common.Auth

class SonatypeAuth : Auth() {
    var user: String? = null
    var password: String? = null
}