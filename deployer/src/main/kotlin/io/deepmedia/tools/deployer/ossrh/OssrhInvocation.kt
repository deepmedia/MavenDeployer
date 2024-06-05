package io.deepmedia.tools.deployer.ossrh

import io.deepmedia.tools.deployer.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.time.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class OssrhInvocation(
    logger: Logger,
    private val client: OssrhClient,
    private val info: OssrhInfo,
    private val closeTimeout: Duration,
    private val promoteTimeout: Duration,
    private val pollDelay: Duration
) {

    private data class State(
        val users: Int, // >= 1
        val create: CompletableDeferred<CreateData> = CompletableDeferred(),
        val release: CompletableDeferred<Unit> = CompletableDeferred()
    )

    private fun State?.addingUser(): State = when (this) {
        null -> State(1)
        else -> this.copy(users = users + 1)
    }

    private fun State?.removingUser(): State? = when (this?.users) {
        null -> null
        1 -> null
        else -> this.copy(users = users - 1)
    }

    private val log = logger.child(info.server.name).child(info.group)
    private val state = MutableStateFlow<State?>(null)
    private var unreleasedRepoId: String? = null

    private data class CreateData(val profile: OssrhProfile, val repoId: String)

    suspend fun create(): String = coroutineScope {
        val state = state.updateAndGet { it.addingUser() }!!
        if (state.users == 1) {
            log { "create: start" }
            execute(state.create) {
                val profiles = client.getProfiles(info)
                log { "create: found profiles ${profiles.map { it.name }}" }
                val profile = profiles.singleOrNull { info.group.startsWith(it.name) }
                    ?: error("Could not find an OSSRH-registered staging profile matching ${info.group}: ${profiles.map { it.name }}")
                log { "create: opening repository at ${profile.name}..." }
                val repoId = client.openRepository(info, profile, "Pending deployment (${info.group}).")
                log { "create: done (repoId: $repoId)" }
                unreleasedRepoId = repoId
                CreateData(profile, repoId)
            }.repoId
        } else {
            log { "create: not the first user (${state.users})" }
            state.create.await().repoId
        }
    }

    suspend fun release() {
        val state = state.getAndUpdate { it.removingUser() }!!
        if (state.users == 1) {
            log { "release: start" }
            execute(state.release) {
                val (profile, repoId) = state.create.await()

                // Close, await closed
                log { "release: closing repository $repoId @ ${profile.name}..." }
                client.closeRepository(info, profile, repoId)
                withTimeoutOrNull(closeTimeout) {
                    while (true) {
                        val repo = client.getRepository(info, repoId)
                        log { "release: polling repo for 'closed' state, found ${repo.type} (transitioning: ${repo.transitioning})" }

                        if (repo.transitioning) { delay(pollDelay); continue }
                        else if (repo.type.equals("closed", ignoreCase = true)) break
                        else error("${info.group}: repository transitioned to unexpected state (!= closed): $repo")
                    }
                } ?: error("${info.group}: repository did not transition to closed state after $closeTimeout.")

                // Release, await released
                log { "release: promoting repository $repoId @ ${profile.name}..." }
                client.promoteRepositories(info, repoId)
                withTimeoutOrNull(promoteTimeout) {
                    while (true) {
                        val repo = client.getRepositoryOrNullIfNotFound(info, repoId) ?: break
                        log { "release: polling repo for 'released' state, found ${repo.type} (transitioning: ${repo.transitioning})" }

                        if (repo.transitioning) { delay(pollDelay); continue }
                        else if (repo.type.equals("released", ignoreCase = true)) break
                        else error("${info.group}: repository transitioned to unexpected state (!= released): $repo")
                    }
                } ?: error("${info.group}: repository did not transition to released state after $promoteTimeout.")

                unreleasedRepoId = null
                log { "release: done" }
            }
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

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun dropUnreleased() {
        val unreleasedRepoId = unreleasedRepoId ?: return
        log { "dropUnreleased: dropping $unreleasedRepoId..." }
        client.dropRepositories(info, unreleasedRepoId)
    }

    private inline fun <T> execute(deferred: CompletableDeferred<T>, operation: () -> T): T {
        return try {
            operation().also { deferred.complete(it) }
        } catch (e: Throwable) {
            deferred.completeExceptionally(e)
            throw e
        }
    }
}

