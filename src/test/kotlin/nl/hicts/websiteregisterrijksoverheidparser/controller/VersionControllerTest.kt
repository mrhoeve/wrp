package nl.hicts.websiteregisterrijksoverheidparser.controller

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.hicts.websiteregisterrijksoverheidparser.model.VersionInfo
import nl.hicts.websiteregisterrijksoverheidparser.service.VersionInformationProvider
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class VersionControllerTest {
    private val versionInformationProvider: VersionInformationProvider = mockk()
    private val mockMvc = standaloneSetup(VersionController(versionInformationProvider)).build()

    @Test
    fun `GET version returns current and update versions`() {
        every { versionInformationProvider.getVersionInfo() } returns VersionInfo(
            version = "2.0.0",
            latestVersion = "2.1.0",
            updateAvailable = true,
            checkedAt = "2026-08-30T12:00:00Z",
        )

        mockMvc.perform(get("/version"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(
                content().json(
                    """{"version":"2.0.0","latestVersion":"2.1.0","updateAvailable":true,"checkedAt":"2026-08-30T12:00:00Z"}""",
                    JsonCompareMode.STRICT,
                ),
            )

        verify(exactly = 1) { versionInformationProvider.getVersionInfo() }
    }

    @Test
    fun `GET version exposes unknown update information as null`() {
        every { versionInformationProvider.getVersionInfo() } returns VersionInfo("2.0.0", null, null, null)

        mockMvc.perform(get("/version"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"version":"2.0.0","latestVersion":null,"updateAvailable":null,"checkedAt":null}""",
                    JsonCompareMode.STRICT,
                ),
            )
    }

    @Test
    fun `POST is not allowed for version endpoint`() {
        mockMvc.perform(post("/version"))
            .andExpect(status().isMethodNotAllowed)
    }
}
