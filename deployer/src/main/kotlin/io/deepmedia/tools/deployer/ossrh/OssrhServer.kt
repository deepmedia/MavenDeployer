package io.deepmedia.tools.deployer.ossrh


// OSSRH: Nexus-powered repositories provided by Sonatype to OSS projects for
// - pushing artifacts to Maven Central (push to ossrh/ossrh1, followed by convoluted close/release/drop API calls)
// - hosting development builds (push to ssrhSnapshots/ossrhSnapshots1)
internal enum class OssrhServer(
    val apiUrl: String,
    val deployUrl: String,
    val snapshotUrl: String
) {
    S00(
        apiUrl = "https://oss.sonatype.org/service/local/",
        deployUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2/",
        snapshotUrl = "https://oss.sonatype.org/content/repositories/snapshots/"
    ),
    S01(
        apiUrl = "https://s01.oss.sonatype.org/service/local/",
        deployUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/",
        snapshotUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
    );

    fun deployUrl(stagingRepositoryId: String): String {
        return apiUrl + "staging/deployByRepositoryId/$stagingRepositoryId"
    }

    fun endpointUrl(endpoint: String): String {
        return apiUrl + endpoint
    }
}