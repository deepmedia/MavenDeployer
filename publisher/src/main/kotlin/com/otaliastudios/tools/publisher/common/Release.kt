package com.otaliastudios.tools.publisher.common

import org.gradle.api.tasks.bundling.Jar

open class Release {
    var version: String? = null
    var description: String? = null
    var vcsTag: String? = null
    var sources: Any? = null
        internal set
    var docs: Any? = null
        internal set

    fun setSources(sourcesJar: Jar) {
        sources = sourcesJar
    }

    fun setDocs(docsJar: Jar) {
        docs = docsJar
    }

    fun setSources(sourcesProvider: Any) {
        sources = sourcesProvider
    }

    fun setDocs(docsProvider: Any) {
        docs = docsProvider
    }

    companion object {
        @JvmField
        val SOURCES_AUTO = Any()
        @JvmField
        val DOCS_AUTO = Any()
    }
}