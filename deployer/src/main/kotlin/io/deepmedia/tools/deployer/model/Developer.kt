package io.deepmedia.tools.deployer.model

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class Developer @Inject constructor(objects: ObjectFactory) {
    val name: Property<String> = objects.property()
    val email: Property<String> = objects.property()
    val organization = objects.property<String?>().convention(null as String?)
    val url = objects.property<String?>().convention(null as String?)
}

interface DeveloperScope {
    fun developer(action: Action<Developer>)

    fun developer(name: String, email: String, organization: String? = null, url: String? = null) = developer {
        this.name.set(name)
        this.email.set(email)
        this.organization.set(organization)
        this.url.set(url)
    }
}