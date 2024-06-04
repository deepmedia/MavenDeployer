package io.deepmedia.tools.deployer.model

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class Signing @Inject constructor(objects: ObjectFactory) : SecretScope {
    val key: Property<Secret> = objects.property<Secret>().apply { finalizeValueOnRead() }
    val password: Property<Secret> = objects.property<Secret>().apply { finalizeValueOnRead() }

    internal fun fallback(to: Signing) {
        key.convention(to.key)
        password.convention(to.password)
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun resolve(target: org.gradle.api.Project, spec: DeploySpec) {
        // Nothing to do
    }
}
