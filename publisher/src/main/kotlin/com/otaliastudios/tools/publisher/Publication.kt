package com.otaliastudios.tools.publisher

import com.otaliastudios.tools.publisher.common.Auth
import com.otaliastudios.tools.publisher.common.Project
import com.otaliastudios.tools.publisher.common.Release
import org.gradle.api.Named

interface Publication : Named {
    var component: String?
    val auth: Auth
    val project: Project
    val release: Release
}