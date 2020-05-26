package com.otaliastudios.tools.publisher.deprecated

import com.otaliastudios.tools.publisher.Publication
import com.otaliastudios.tools.publisher.common.Auth
import com.otaliastudios.tools.publisher.common.Project
import com.otaliastudios.tools.publisher.common.Release
import org.gradle.api.NamedDomainObjectList

/**
 * Extension used by [PublisherPlugin].
 * Should be subclassed by concrete publisher implementations.
 */
abstract class PublisherExtension {
    var publication: String? = null
    var component: String? = null

    open val auth: Auth = Auth()
    open val project: Project = Project()
    open val release: Release = Release()
}