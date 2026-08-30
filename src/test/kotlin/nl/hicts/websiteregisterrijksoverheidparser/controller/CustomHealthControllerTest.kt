package nl.hicts.websiteregisterrijksoverheidparser.controller

import io.mockk.every
import io.mockk.mockk
import nl.hicts.websiteregisterrijksoverheidparser.controller.CustomHealthController.Companion.DOWN
import nl.hicts.websiteregisterrijksoverheidparser.controller.CustomHealthController.Companion.UP
import org.junit.jupiter.api.Test
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.health.contributor.Status
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class CustomHealthControllerTest {
    private val healthEndpoint: HealthEndpoint = mockk()
    private val mockMvc: MockMvc = standaloneSetup(CustomHealthController(healthEndpoint)).build()

    @Test
    fun `GET health returns UP with status 200 when healthy`() {
        every { healthEndpoint.health() } returns mockk {
            every { status } returns Status.UP
        }

        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
            .andExpect(content().string(UP))
    }

    @Test
    fun `GET health returns DOWN with status 503 when unhealthy`() {
        every { healthEndpoint.health() } returns mockk {
            every { status } returns Status.DOWN
        }

        mockMvc.perform(get("/health"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
            .andExpect(content().string(DOWN))
    }

    @Test
    fun `POST is not allowed for health endpoint`() {
        mockMvc.perform(post("/health"))
            .andExpect(status().isMethodNotAllowed)
    }
}
