package io.deepmedia.tools.deployer.central.portal

import io.deepmedia.tools.deployer.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.Directory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration

internal class CentralPortalInvocation(
    logger: Logger,
    private val client: CentralPortalClient,
    private val info: CentralPortalInfo,
    private val timeout: Duration,
    private val pollDelay: Duration,
) {

    private data class State(
        val users: Int, // >= 1
        val directories: Set<File>,
    )

    private fun State?.addingUser(directory: File): State = when (this) {
        null -> State(1, setOf(directory))
        else -> this.copy(users = users + 1, directories = directories + directory)
    }

    private fun State?.removingUser(): State? = when (this?.users) {
        null -> null
        1 -> null
        else -> this.copy(users = users - 1)
    }

    private val log = logger
    private val state = MutableStateFlow<State?>(null)

    fun append(directory: File) {
        log { "append: $directory..." }
        val state = state.updateAndGet { it.addingUser(directory) }!!
        log { "append: ${state.users} users" }
    }

    suspend fun release(buildDir: File) {
        val state = state.getAndUpdate { it.removingUser() }!!
        if (state.users == 1) {

            val archiveHash = state.directories.map { it.path.hashCode() }.hashCode().toUInt()
            log { "release: ZIP ($archiveHash)" }
            val archiveFile = buildDir
                .let { File(it, "deployer") }
                .let { File(it, "centralPortal") }
                .let { File(it, "$archiveHash-upload.zip") }
            archiveFile.parentFile.mkdirs()
            archiveFile.delete()
            ZipOutputStream(archiveFile.outputStream()).use { zipStream ->
                state.directories.forEach { mavenBaseDir ->
                    mavenBaseDir.walkTopDown().forEach { mavenEntry ->
                        if (mavenEntry.isFile) {
                            zipStream.putNextEntry(ZipEntry(mavenBaseDir.toPath().relativize(mavenEntry.toPath()).toString()))
                            mavenEntry.inputStream().use { it.copyTo(zipStream) }
                            zipStream.closeEntry()
                        }
                    }
                }
            }

            log { "release: UPLOAD (${archiveFile.length()} bytes)" }
            val deploymentId = client.createDeployment(info, archiveFile)
            val target = if (info.allowSync) "PUBLISHED" else "VALIDATED"

            log { "release: WAIT" }
            withTimeoutOrNull(timeout) {
                while (true) {
                    val deployment = client.getDeployment(info, deploymentId)
                    log { "release: polling for '$target' state, found ${deployment.deploymentState}" }
                    if (deployment.deploymentState == "FAILED") {
                        runCatching { client.deleteDeployment(info, deploymentId) }
                        log { "release: ${deployment.errors?.let { Json.encodeToString(it) }}" }
                        error("Deployment validation failed: ${deployment.errors?.let { Json.encodeToString(it) }}")
                    } else if (deployment.deploymentState == target) {
                        break
                    } else {
                        delay(pollDelay)
                        continue
                    }
                }
            } ?: error("Deployment did not transition to $target state after $timeout.")
        } else {
            log { "release: not the last user (${state.users})" }
            // Can't do this.
            // Waiting for siblings is OK during creation (where the first child does the job), but when releasing,
            // all subprojects would have to wait for the slowest one to arrive and do the work.
            // Since we are using runBlocking in OssrhService, this means that:
            // - in Gradle parallel mode, this leaves to thread starvation when there are enough subprojects
            // - in no-parallel mode, the build should hang completely.
            // state.release.await()
        }
    }
}

