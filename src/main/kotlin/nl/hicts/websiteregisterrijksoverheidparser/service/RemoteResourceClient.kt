package nl.hicts.websiteregisterrijksoverheidparser.service

import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.File
import java.net.URI
import java.net.http.HttpClient

@Component
class RemoteResourceClient(restClientBuilder: RestClient.Builder) {
    private val restClient = restClientBuilder
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
            )
        )
        .build()

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
                    response.body.use { input ->
                        temporaryFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            return temporaryFile
        } catch (throwable: Throwable) {
            temporaryFile.delete()
            throw throwable
        }
    }
}
