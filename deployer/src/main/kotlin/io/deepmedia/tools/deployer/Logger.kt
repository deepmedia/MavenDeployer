package io.deepmedia.tools.deployer

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType


internal class Logger(private val verbose: Provider<Boolean>, private val tags: List<String>) {
    val prefix = (listOf("DeployerPlugin") + tags).joinToString(
        separator = " ",
        transform = { "[$it]" }
    )

    inline operator fun invoke(message: () -> String) {
        if (verbose.get()) println("$prefix ${message()}")
    }

    fun child(tag: String) = Logger(verbose, tags + tag)
}