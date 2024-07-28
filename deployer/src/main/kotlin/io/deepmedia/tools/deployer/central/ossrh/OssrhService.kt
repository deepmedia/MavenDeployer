package io.deepmedia.tools.deployer.central.ossrh

import io.deepmedia.tools.deployer.Logger
import kotlinx.coroutines.*
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import kotlin.time.Duration.Companion.milliseconds


internal abstract class OssrhService : BuildService<OssrhService.Params>, AutoCloseable {
    companion object {
        const val Name = "ossrh"
    }

    interface Params : BuildServiceParameters {
        val pollingDelay: Property<Long>
        val closeTimeout: Property<Long>
        val releaseTimeout: Property<Long>
        val verboseLogging: Property<Boolean>
    }

    private val client = OssrhClient()
    private val job = SupervisorJob()
    private val invocations = mutableMapOf<OssrhInfo, OssrhInvocation>()
    private val lock = Any()
    private val log by lazy { Logger(parameters.verboseLogging, listOf("Ossrh")) }

    private fun invocation(info: OssrhInfo): OssrhInvocation = synchronized(lock) {
        invocations.getOrPut(info) {
            log { "Creating invocation for '${info.group}' at ${info.server}..." }
            OssrhInvocation(
                logger = log,
                client = client,
                info = info,
                closeTimeout = parameters.closeTimeout.get().milliseconds,
                promoteTimeout = parameters.releaseTimeout.get().milliseconds,
                pollDelay = parameters.pollingDelay.get().milliseconds
            )
        }
    }

    fun initialize(info: OssrhInfo): String {
        val invocation = invocation(info)
        val repoId = runBlocking(job + Dispatchers.Default) { invocation.create() }
        return info.server.deployUrl(repoId)
    }

    fun finalize(info: OssrhInfo) {
        val invocation = invocation(info)
        runBlocking(job + Dispatchers.Default) { invocation.release() }
    }

    override fun close() {
        job.cancel()
        job.invokeOnCompletion {
            invocations.values.forEach {
                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch { runCatching { it.dropUnreleased() } }
            }
        }
    }
}