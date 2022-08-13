package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.fallback
import org.gradle.api.Transformer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

// https://central.sonatype.org/pages/requirements.html
open class Scm @Inject constructor(objects: ObjectFactory) {

    val url: Property<String> = objects.property()
    internal lateinit var resolvedUrl: Provider<String>
    val connection = objects.property<String?>()
    val developerConnection = objects.property<String?>()

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
        sourceUrl { tag -> "https://bitbucket.org/$user/$repository/src/" }
    }

    internal fun fallback(to: Scm) {
        url.fallback(to.url)
        connection.fallback(to.connection)
        developerConnection.fallback(to.developerConnection)
    }

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