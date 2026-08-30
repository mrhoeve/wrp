package nl.hicts.websiteregisterrijksoverheidparser.service

import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize
import org.springframework.web.client.RestClient
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

@Component
class RemoteResourceClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${httpconnecttimeout:10s}") connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    @Value("\${httpreadtimeout:60s}") readTimeout: Duration = DEFAULT_READ_TIMEOUT,
    @param:Value("\${httpmaxdownloadsize:256MB}") private val maxDownloadSize: DataSize = DEFAULT_MAX_DOWNLOAD_SIZE,
) {
    init {
        require(connectTimeout.isPositive()) { "httpconnecttimeout must be greater than zero" }
        require(readTimeout.isPositive()) { "httpreadtimeout must be greater than zero" }
        require(maxDownloadSize.toBytes() > 0) { "httpmaxdownloadsize must be greater than zero" }
    }

    private val restClient = restClientBuilder
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
            ).apply { setReadTimeout(readTimeout) }
        )
        .build()

    companion object {
        private val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(60)
        private val DEFAULT_MAX_DOWNLOAD_SIZE: DataSize = DataSize.ofMegabytes(256)
    }

    fun getText(url: String): String =
        restClient.get()
            .uri(URI.create(url))
            .retrieve()
            .body(String::class.java)
            .orEmpty()

    fun downloadToTemporaryFile(url: String): File {
        val temporaryFile = File.createTempFile("document", ".ods")

        try {
            restClient.get()
                .uri(URI.create(url))
                .exchange { _, response ->
                    check(response.statusCode.is2xxSuccessful) {
                        "Download from '$url' failed with HTTP status ${response.statusCode.value()}"
                    }
                    val contentLength = response.headers.contentLength
                    check(contentLength < 0 || contentLength <= maxDownloadSize.toBytes()) {
                        "Download from '$url' exceeds the configured maximum of $maxDownloadSize"
                    }
                    response.body.use { input ->
                        temporaryFile.outputStream().use { output ->
                            input.copyToWithLimit(output, maxDownloadSize.toBytes(), url)
                        }
                    }
                }
            return temporaryFile
        } catch (throwable: Throwable) {
            temporaryFile.delete()
            throw throwable
        }
    }

    private fun InputStream.copyToWithLimit(output: OutputStream, maximumBytes: Long, url: String) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val bytesRead = read(buffer)
            if (bytesRead < 0) return
            totalBytes += bytesRead
            check(totalBytes <= maximumBytes) {
                "Download from '$url' exceeds the configured maximum of $maxDownloadSize"
            }
            output.write(buffer, 0, bytesRead)
        }
    }
}
