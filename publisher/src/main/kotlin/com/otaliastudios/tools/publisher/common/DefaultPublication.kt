package com.otaliastudios.tools.publisher.common

import com.otaliastudios.tools.publisher.Publication

open class DefaultPublication(name: String) : Publication {
    private val _name = name
    override fun getName() = _name
    override var component: String? = null
    override var publication: String? = null
    override val auth = Auth()
    override val project = Project()
    override val release = Release()
}