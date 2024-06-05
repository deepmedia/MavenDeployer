package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.model.AbstractDeploySpec
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.signatory.pgp.PgpSignatory
import org.gradle.security.internal.pgp.BaseInMemoryPgpSignatoryProvider

/**
 * Note: with respect to official docs, we do the extra step of fetching the signatory
 * (which [SigningExtension] generates after [SigningExtension.useInMemoryPgpKeys]) and pass that again
 * to the task using [Sign.setSignatory].
 *
 * This may be helpful in case different key-password pairs are defined for different publications,
 * which our API allows, while the [SigningExtension] is a project-wide item. There's the risk that
 * at execution time, all tasks use the last key-value pair which is not what we want.
 */
internal fun Project.configureSigning(
    info: Pair<String, String>,
    maven: MavenPublication,
    log: Logger
): Sign {
    log { "configureSigning: signing MavenPublication ${maven.name}" }

    // If this publication is shared between specs, there's a chance that the sign task already exists.
    // Note that we have no way of verifying whether that spec used the same key-password pair, so we warn.
    val previous = tasks.withType<Sign>().findByName("sign${maven.name.capitalized()}")
    if (previous != null) {
        logger.log(
            LogLevel.WARN, "Two or more specs share the same MavenPublication under the hood! " +
                    "Only one of the signatures will be used, and other configuration parameters " +
                    "might be conflicting too. Location: ${log.prefix}")
        return previous
    }

    val ext = extensions.getByType(SigningExtension::class)
    ext.useInMemoryPgpKeys(info.first, info.second)
    val signatory = ext.signatory
    return ext.sign(maven).single().apply {
        setSignatory(signatory)
    }
}
