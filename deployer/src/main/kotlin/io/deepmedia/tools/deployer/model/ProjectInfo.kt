package io.deepmedia.tools.deployer.model

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

    internal fun resolvedArtifactId(component: Component): Provider<String> {
        return resolvedArtifactId.map { base ->
            val transformed = component.artifactId.orNull?.transform(base)
            transformed ?: base
        }
    }

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
        name.convention(to.name)
        description.convention(to.description)
        url.convention(to.url)
        groupId.convention(to.groupId)
        artifactId.convention(to.artifactId)
        scm.fallback(to.scm)
        to.licenses.all { licenses.add(this) }
        to.licenses.whenObjectRemoved { licenses.remove(this) }
        to.developers.all { developers.add(this) }
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