package io.deepmedia.tools.deployer.central.portal

import io.deepmedia.tools.deployer.Logger
import kotlinx.coroutines.*
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import kotlin.time.Duration.Companion.milliseconds


internal abstract class CentralPortalService : BuildService<CentralPortalService.Params>, AutoCloseable {
    companion object {
        const val Name = "centralPortal"
    }

    interface Params : BuildServiceParameters {
        val timeout: Property<Long>
        val pollingDelay: Property<Long>
        val verboseLogging: Property<Boolean>
    }

    private val client = CentralPortalClient()
    private val job = SupervisorJob()
    private val invocations = mutableMapOf<CentralPortalInfo, CentralPortalInvocation>()
    private val lock = Any()
    private val log by lazy { Logger(parameters.verboseLogging, listOf("CentralPortal")) }

    private fun invocation(info: CentralPortalInfo): CentralPortalInvocation = synchronized(lock) {
        invocations.getOrPut(info) {
            log { "Creating invocation..." }
            CentralPortalInvocation(
                logger = log,
                client = client,
                info = info,
                timeout = parameters.timeout.get().milliseconds,
                pollDelay = parameters.pollingDelay.get().milliseconds
            )
        }
    }

    fun initialize(info: CentralPortalInfo, localRepository: File) {
        val invocation = invocation(info)
        invocation.append(localRepository)
    }

    fun finalize(info: CentralPortalInfo, buildDir: File) {
        val invocation = invocation(info)
        runBlocking(job + Dispatchers.Default) { invocation.release(buildDir) }
    }

    override fun close() {
        job.cancel()
    }
}