package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.model.AbstractDeploySpec
import io.deepmedia.tools.deployer.model.Component
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

internal fun Project.configureSigning(
    spec: AbstractDeploySpec<*>,
    maven: MavenPublication,
    log: Logger
): Sign? {
    // Configure signing if present
    if (spec.hasSigning(this)) {
        log { "configureSigning: signing MavenPublication ${maven.name}" }
        val ext = extensions.getByType(SigningExtension::class)
        val key = spec.signing.key.get().resolve(this, "spec.signing.key")
        val password = spec.signing.password.get().resolve(this, "spec.signing.password")
        ext.useInMemoryPgpKeys(key, password)
        try {
            return ext.sign(maven).single()
        } catch (e: Throwable) {
            logger.log(
                LogLevel.WARN, "Two or more specs share the same MavenPublication under the hood! " +
                        "Only one of the signatures will be used, and other configuration parameters " +
                        "might be conflicting as well. Location: ${log.prefix}")
        }
    }
    return null
}
