package io.deepmedia.tools.deployer.model

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class License @Inject constructor(objects: ObjectFactory) {
    val id: Property<String> = objects.property()
    val url: Property<String> = objects.property()
}

interface LicenseScope {
    fun license(action: Action<License>)

    fun license(id: String, url: String) = license {
        this.id.set(id)
        this.url.set(url)
    }

    // https://spdx.org/licenses/
    val apache2: Action<License> get() = Action {
        id.set("Apache-2.0")
        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
    }
    val MIT: Action<License> get() = Action {
        id.set("MIT")
        url.set("https://opensource.org/licenses/MIT")
    }
}