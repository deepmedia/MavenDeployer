package io.deepmedia.tools.publisher.github

import io.deepmedia.tools.publisher.common.Auth

class GithubAuth : Auth() {
    /** GitHub username. */
    var user: String? = null
    /** GitHub personal access token. */
    var token: String? = null
}