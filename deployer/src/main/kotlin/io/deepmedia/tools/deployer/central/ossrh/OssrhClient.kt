package io.deepmedia.tools.deployer.central.ossrh

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class OssrhProfile(val id: String, val name: String)

@Serializable
internal data class OssrhRepository(
    val profileId: String,
    val repositoryId: String,
    val type: String, // open, closed, released
    val transitioning: Boolean
)

// https://oss.sonatype.org/nexus-staging-plugin/default/docs/index.html
internal class OssrhClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val http = HttpClient(CIO) {
        engine {
            requestTimeout = 0
        }
        expectSuccess = true
        install(ContentNegotiation) {
            json(json)
        }
    }

    @Serializable
    private data class Data<T>(val data: T)

    @Serializable
    private data class Description(val description: String)

    // happens to match /start request body and /finish response body
    @Serializable
    private data class RepositoryId(val stagedRepositoryId: String)

    @Serializable
    private data class RepositoryPromotion(val stagedRepositoryIds: List<String>, val autoDropAfterRelease: Boolean)

    @Serializable
    private data class RepositoryRemoval(val stagedRepositoryIds: List<String>)


    suspend fun getProfiles(info: OssrhInfo): List<OssrhProfile> {
        return http.get(info.server.endpointUrl("staging/profiles")) {
            accept(ContentType.Application.Json)
            basicAuth(info.username, info.password)
        }.body<Data<List<OssrhProfile>>>().data
    }

    suspend fun getProfileRepositories(info: OssrhInfo, profile: OssrhProfile): List<OssrhRepository> {
        return http.get(info.server.endpointUrl("staging/profile_repositories/${profile.id}")) {
            accept(ContentType.Application.Json)
            basicAuth(info.username, info.password)
        }.body<Data<List<OssrhRepository>>>().data
    }

    suspend fun openRepository(info: OssrhInfo, profile: OssrhProfile, description: String): String {
        return http.post(info.server.endpointUrl("staging/profiles/${profile.id}/start")) {
            accept(ContentType.Application.Json)
            basicAuth(info.username, info.password)
            contentType(ContentType.Application.Json)
            setBody(Data(Description(description)))
        }.body<Data<RepositoryId>>().data.stagedRepositoryId
    }

    suspend fun closeRepository(info: OssrhInfo, profile: OssrhProfile, id: String) {
        return http.post(info.server.endpointUrl("staging/profiles/${profile.id}/finish")) {
            accept(ContentType.Application.Json)
            basicAuth(info.username, info.password)
            contentType(ContentType.Application.Json)
            setBody(Data(RepositoryId(id)))
        }.body()
    }

    suspend fun promoteRepositories(info: OssrhInfo, vararg ids: String) {
        return http.post(info.server.endpointUrl("staging/bulk/promote")) {
            accept(ContentType.Application.Json)
            basicAuth(info.username, info.password)
            contentType(ContentType.Application.Json)
            setBody(Data(RepositoryPromotion(ids.toList(), autoDropAfterRelease = true)))
        }.body()
    }

    suspend fun dropRepositories(info: OssrhInfo, vararg ids: String) {
        return http.post(info.server.endpointUrl("staging/bulk/drop")) {
            accept(ContentType.Application.Json)
            basicAuth(info.username, info.password)
            contentType(ContentType.Application.Json)
            setBody(Data(RepositoryRemoval(ids.toList())))
        }.body()
    }

    // Note that after release, we may get an empty body and fail with:
    // io.ktor.serialization.JsonConvertException: Illegal input: Fields [profileId, repositoryId, type, transitioning] are required for type with serial name 'io.deepmedia.tools.deployer.ossrh.OssrhRepository', but they were missing at path: $
    suspend fun getRepository(info: OssrhInfo, id: String): OssrhRepository {
        return http.get(info.server.endpointUrl("staging/repository/$id")) {
            accept(ContentType.Application.Json)
            basicAuth(info.username, info.password)
        }.body<OssrhRepository>()
    }

    suspend fun getRepositoryOrNullIfNotFound(info: OssrhInfo, id: String): OssrhRepository? {
        return try {
            getRepository(info, id)
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) return null
            throw e
        }
    }
}