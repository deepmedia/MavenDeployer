package io.deepmedia.tools.deployer.model

import org.gradle.api.Transformer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

// https://central.sonatype.org/pages/requirements.html
open class Scm @Inject constructor(objects: ObjectFactory) {

    val url: Property<String> = objects.property<String>().apply { finalizeValueOnRead() }
    internal lateinit var resolvedUrl: Provider<String>
    val connection = objects.property<String?>().apply { finalizeValueOnRead() }
    val developerConnection = objects.property<String?>().apply { finalizeValueOnRead() }

    fun fromGithub(user: String, repository: String) {
        url.set("https://github.com/$user/$repository")
        connection.set("scm:git:git://github.com/$user/$repository.git")
        developerConnection.set("scm:git:ssh://github.com:$user/$repository.git")
        sourceUrl { tag -> "https://github.com/$user/$repository/tree/$tag" }
    }

    fun fromBitbucket(user: String, repository: String) {
        url.set("https://bitbucket.org/$user/$repository")
        connection.set("scm:git:git://bitbucket.org/$user/$repository.git")
        developerConnection.set("scm:git:ssh://bitbucket.org:$user/$repository.git")
        sourceUrl { _ /* tag */ -> "https://bitbucket.org/$user/$repository/src/" }
    }

    fun Scm.fromGitlab(user: String, repository: String) {
        url.set("https://gitlab.com/$user/$repository")
        connection.set("scm:git:git://gitlab.com/$user/$repository.git")
        developerConnection.set("scm:git:ssh://gitlab.com:$user/$repository.git")
        sourceUrl { tag -> "https://gitlab.com/$user/$repository/tree/$tag" }
    }

    internal fun fallback(to: Scm) {
        url.convention(to.url)
        connection.convention(to.connection)
        developerConnection.convention(to.developerConnection)
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun resolve(target: org.gradle.api.Project, spec: DeploySpec) {
        resolvedUrl = url.orElse(spec.projectInfo.url)
        sourceUrl.convention(resolvedUrl.map { resolvedUrl ->
            Transformer<String, String> { resolvedUrl }
        })
    }

    internal val sourceUrl: Property<Transformer<String, String>> = objects.property()

    fun sourceUrl(block: (String) -> String) {
        sourceUrl.set(block)
    }

    fun sourceUrl(transformer: Transformer<String, String>) {
        sourceUrl.set(transformer)
    }
}

interface ScmScope {
    val scm: Scm

    fun scm(url: String, connection: String? = null, developerConnection: String? = null) {
        scm.url.set(url)
        scm.connection.set(connection)
        scm.developerConnection.set(developerConnection)
    }
}
