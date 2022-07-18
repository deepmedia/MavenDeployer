package io.deepmedia.tools.publisher.common

// https://central.sonatype.org/pages/requirements.html
open class Scm(
    val url: String,
    val connection: String? = null,
    val developerConnection: String? = null
) {
    open fun source(tag: String) = url
}

fun GithubScm(user: String, repository: String) : Scm {
    val url = "https://github.com/$user/$repository"
    val connection = "scm:git:git://github.com/$user/$repository.git"
    val developerConnection = "scm:git:ssh://github.com:$user/$repository.git"
    return object : Scm(url, connection, developerConnection) {
        override fun source(tag: String): String {
            return "$url/tree/$tag"
        }
    }
}

fun BitBucketScm(user: String, repository: String) : Scm {
    val url = "https://bitbucket.org/$user/$repository"
    val connection = "scm:git:git://bitbucket.org/$user/$repository.git"
    val developerConnection = "scm:git:ssh://bitbucket.org:$user/$repository.git"
    return object : Scm(url, connection, developerConnection) {
        override fun source(tag: String): String {
            return "$url/src/"
        }
    }
}
