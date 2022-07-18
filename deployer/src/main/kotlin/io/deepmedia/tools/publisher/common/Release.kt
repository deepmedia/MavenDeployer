package io.deepmedia.tools.publisher.common

import org.gradle.api.tasks.bundling.Jar

open class Release {

    // guaranteed, we default to gradle project version
    var version: String? = null

    // guaranteed, we default to v$version
    var tag: String? = null
    @Deprecated("Use tag")
    var vcsTag: String? = null

    // guaranteed, defaults to "${project.name} $tag"
    var description: String? = null

    var sources: Any? = null

    var docs: Any? = null

    companion object {
        @JvmField
        val SOURCES_AUTO = Any()
        @JvmField
        val DOCS_AUTO = Any()
    }
}