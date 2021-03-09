package io.deepmedia.tools.publisher.common

open class Project {
    var name: String? = null

    var description: String? = null

    // possibly empty
    var url: String? = null

    // possibly empty
    var scm: Scm? = null

    @Deprecated("Use scm")
    var vcsUrl: String? = null

    var group: String? = null

    var artifact: String? = null

    var packaging: String? = null

    // possibly empty
    var licenses = listOf<License>()
        private set

    // possibly empty
    var developers = listOf<Developer>()
        private set

    fun addLicense(name: String, url: String) = addLicense(License(name, url))

    fun addLicense(license: License) {
        licenses += license
    }

    fun addDeveloper(
        name: String,
        email: String,
        organization: String? = null,
        url: String? = null
    ) = addDeveloper(Developer(name, email, organization, url))

    fun addDeveloper(developer: Developer) {
        developers += developer
    }
}