package io.deepmedia.tools.deployer.central.ossrh

internal data class OssrhInfo(
    val server: OssrhServer,
    val username: String,
    val password: String,
    // The group of the packages to be published, for example, "io.deepmedia.tools".
    // This identifies (helps select) the staging profile to be used for staging repos.
    // Not ideal to have here because different groups may resolve to the same profile, but works for now
    val group: String
)