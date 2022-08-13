package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.fallback
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class Signing @Inject constructor(objects: ObjectFactory) : SecretScope {
    val key: Property<Secret> = objects.property()
    val password: Property<Secret> = objects.property()

    internal fun fallback(to: Signing) {
        key.fallback(to.key)
        password.fallback(to.password)
    }

    internal fun resolve(target: org.gradle.api.Project, spec: DeploySpec) {
        // Nothing to do
    }
}
