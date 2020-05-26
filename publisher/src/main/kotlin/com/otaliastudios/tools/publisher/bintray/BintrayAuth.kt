package com.otaliastudios.tools.publisher.bintray

import com.otaliastudios.tools.publisher.common.Auth

class BintrayAuth : Auth() {
    var repo: String? = null
    var user: String? = null
    var key: String? = null
}