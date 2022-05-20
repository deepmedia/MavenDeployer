package io.deepmedia.tools.publisher.github

import io.deepmedia.tools.publisher.common.DefaultPublication

class GithubPublication(name: String) : DefaultPublication(name) {
    var owner: String? = null
    var repository: String? = null

    override val auth = GithubAuth()
}