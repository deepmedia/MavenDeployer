package io.deepmedia.tools.publisher.common

import org.gradle.api.tasks.bundling.Jar

open class Release {
    var version: String? = null
    var description: String? = null
    var vcsTag: String? = null
    var sources: Any? = null
    var docs: Any? = null

    companion object {
        @JvmField
        val SOURCES_AUTO = Any()
        @JvmField
        val DOCS_AUTO = Any()
    }
}