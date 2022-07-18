package io.deepmedia.tools.publisher.sonatype

/**
 * REGISTERING
 * Follow https://central.sonatype.org/pages/ossrh-guide.html#create-a-ticket-with-sonatype :
 * 1. create a new Jira account if needed
 * 2. open a new issue for your groupId 
 * 3. verify the group ownership e.g. using a TXT record
 *
 * The username and password from (1) must be passed to MavenPublisher auth config.
 *
 * SIGNING KEYS
 * https://central.sonatype.org/pages/working-with-pgp-signatures.html#distributing-your-public-key
 *
 * 1. generate the key: gpg --gen-key .
 *    Add name, email and no comment. Choose a password. The final message will already contain
 *    the key id (something like 2AC2B402AAEF6516), but otherwise it can also be retrieved with
 *    gpg --list-secret-keys --keyid-format LONG . The desired ID is in the "sec" line, after the slash.
 *
 * 2. upload the key: gpg --keyserver keyserver.ubuntu.com --send-keys 2AC2B402AAEF6516
 *    This step is needed for signature verification when trying to promote the staging repository.
 *    The upload can be verified by opening the website and looking for 0x2AC2B402AAEF6516 .
 *
 * 3. to avoid working with keyring file, we now have to generate the armored secret key:
 *    https://stackoverflow.com/a/58000485/4288782 . This can be done as follows:
 *    gpg --armor --export-secret-keys 2AC2B402AAEF6516 | awk 'NR == 1 { print "SIGNING_KEY=" } 1' ORS='\\n'
 *    This creates a line suitable for being pasted in local.properties. Otherwise, first command is enough.
 *    Note that anytime the command is called, the key will be slightly different.
 *
 * The password chosen in (1) and the armored key from (3) must be passed to MavenPublisher signing config.
 *
 * RELEASING AFTER PUBLISHING
 * Need to login at https://s01.oss.sonatype.org/ with these credentials. Then locate the newest
 * repository in Staging Repositories, select and click Close. Data will be verified, it will take a
 * while, then click Release. If it's the first time, you also have to send a comment in the jira ticket.
 * The repo will appear here when synced: https://repo1.maven.org/maven2/ .
 */
object Sonatype {
    const val OSSRH_SNAPSHOT_0 = "https://oss.sonatype.org/content/repositories/snapshots/"
    const val OSSRH_SNAPSHOT_1 = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
    const val OSSRH_0 = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
    const val OSSRH_1 = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
}