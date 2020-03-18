package com.otaliastudios.tools.publisher

import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar

/**
 * Extension used by [PublisherPlugin].
 * Should be subclassed by concrete publisher implementations.
 */
abstract class PublisherExtension {

    var publication: String? = null

    open class Auth
    open val auth: Auth = Auth()

    open class Project {
        var name: String? = null
        var description: String? = null
        var url: String? = null
        var vcsUrl: String? = null
        var group: String? = null
        var artifact: String? = null
        var packaging: String? = null
        var licenses = listOf<License>()
            private set

        fun addLicense(name: String, url: String) {
            addLicense(License(name, url))
        }

        fun addLicense(license: License) {
            licenses = licenses + license
        }
    }
    open val project: Project = Project()

    class License(val name: String, val url: String) {
        // https://spdx.org/licenses/
        companion object {
            @JvmField
            val APACHE_2_0 = License(
                    name = "Apache-2.0",
                    url = "http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }

    open class Release {
        var version: String? = null
        var description: String? = null
        var vcsTag: String? = null
        var sources: Any? = null
            private set
        var docs: Any? = null
            private set

        fun setSources(sourcesJar: Jar) {
            sources = sourcesJar
        }

        fun setDocs(docsJar: Jar) {
            docs = docsJar
        }
    }
    open val release: Release = Release()

}