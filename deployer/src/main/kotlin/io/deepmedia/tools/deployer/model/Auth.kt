package io.deepmedia.tools.deployer.model

import javax.inject.Inject

open class Auth @Inject constructor() {
    internal open fun fallback(to: Auth) {}
    internal open fun resolve(target: org.gradle.api.Project, spec: DeploySpec) {}
}