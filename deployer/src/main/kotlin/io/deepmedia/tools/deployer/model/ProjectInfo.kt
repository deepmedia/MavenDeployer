package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.fallback
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.domainObjectSet
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class ProjectInfo @Inject constructor(private val objects: ObjectFactory) : LicenseScope, DeveloperScope, ScmScope {

    val name: Property<String> = objects.property()
    val description: Property<String> = objects.property()
    val url = objects.property<String>()
    val groupId: Property<String> = objects.property()
    val artifactId: Property<String> = objects.property()

    internal lateinit var resolvedName: Provider<String>
    internal lateinit var resolvedDescription: Provider<String>
    internal lateinit var resolvedGroupId: Provider<String>
    internal lateinit var resolvedArtifactId: Provider<String>

    override val scm: Scm = objects.newInstance()

    fun scm(action: Action<Scm>) {
        action.execute(scm)
    }

    val licenses = objects.domainObjectSet(License::class)

    override fun license(action: Action<License>) {
        val license: License = objects.newInstance()
        action.execute(license)
        licenses.add(license)
    }

    val developers = objects.domainObjectSet(Developer::class)

    override fun developer(action: Action<Developer>) {
        val dev: Developer = objects.newInstance()
        action.execute(dev)
        developers.add(dev)
    }

    internal fun fallback(to: ProjectInfo) {
        name.fallback(to.name)
        description.fallback(to.description)
        url.fallback(to.url)
        groupId.fallback(to.groupId)
        artifactId.fallback(to.artifactId)
        scm.fallback(to.scm)
        to.licenses.whenObjectAdded { licenses.add(this) }
        to.licenses.whenObjectRemoved { licenses.remove(this) }
        to.developers.whenObjectAdded { developers.add(this) }
        to.developers.whenObjectRemoved { developers.remove(this) }
    }

    internal fun resolve(target: org.gradle.api.Project, spec: DeploySpec) {
        resolvedName = name.orElse(target.rootProject.name)
        resolvedDescription = description.orElse(target.rootProject.name)
        resolvedGroupId = groupId.orElse(target.provider { target.group.toString() })
        resolvedArtifactId = artifactId.orElse(target.provider {
            val ext: BasePluginExtension? = target.extensions.findByType()
            ext?.archivesName?.orNull ?: target.name
        })
        scm.resolve(target, spec)
    }
}