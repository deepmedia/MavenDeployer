package com.otaliastudios.tools.publisher.common

open class Project {
    var name: String? = null
    var description: String? = null
    var url: String? = null
    var vcsUrl: String? = null
    var group: String? = null
    var artifact: String? = null
    var packaging: String? = null
    var licenses = listOf<License>()
        private set

    fun addLicense(name: String, url: String) {
        addLicense(
            License(
                name,
                url
            )
        )
    }

    fun addLicense(license: License) {
        licenses = licenses + license
    }
}