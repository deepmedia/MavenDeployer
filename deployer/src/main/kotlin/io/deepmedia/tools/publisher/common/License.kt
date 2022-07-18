package io.deepmedia.tools.publisher.common

data class License(val name: String, val url: String) {
    // https://spdx.org/licenses/
    companion object {
        @JvmField
        val APACHE_2_0 = License(name = "Apache-2.0", url = "http://www.apache.org/licenses/LICENSE-2.0.txt")
        @JvmField
        val MIT = License(name = "MIT", url = "https://opensource.org/licenses/MIT")
    }
}