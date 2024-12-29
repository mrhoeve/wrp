package nl.hicts.websiteregisterrijksoverheidparser.controller

import io.mockk.every
import io.mockk.mockk
import nl.hicts.websiteregisterrijksoverheidparser.controller.CustomHealthController.Companion.DOWN
import nl.hicts.websiteregisterrijksoverheidparser.controller.CustomHealthController.Companion.UP
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.Status
import org.springframework.http.HttpStatus

class CustomHealthControllerTest {
    val healthEndpoint: HealthEndpoint = mockk()
    val controller = CustomHealthController(healthEndpoint)

    @Test
    fun `should return UP if healthy`() {
        every { healthEndpoint.health() } returns mockk {
            every { status } returns Status.UP
        }

        val result = controller.plainHealth()

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(UP, result.body)
    }

    @Test
    fun `should return DOWN if not healthy`() {
        every { healthEndpoint.health() } returns mockk {
            every { status } returns Status.DOWN
        }

        val result = controller.plainHealth()

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.statusCode)
        assertEquals(DOWN, result.body)
    }
}
