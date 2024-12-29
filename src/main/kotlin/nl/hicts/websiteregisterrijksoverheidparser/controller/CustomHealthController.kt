package nl.hicts.websiteregisterrijksoverheidparser.controller

import org.springframework.web.bind.annotation.RestController
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.Status
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.http.MediaType

@RestController
class CustomHealthController(
    private val healthEndpoint: HealthEndpoint
) {
    companion object {
        const val UP = "UP"
        const val DOWN = "DOWN"
    }

    @GetMapping("/health", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun plainHealth(): ResponseEntity<String> {
        val health = healthEndpoint.health()
        if(health.status == Status.UP)
            return ResponseEntity.ok(UP)

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(DOWN)
    }
}
