package nl.hicts.websiteregisterrijksoverheidparser.controller

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import nl.hicts.websiteregisterrijksoverheidparser.service.WebsiteregisterRijksoverheidService
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class ControllerTest {
    private val service: WebsiteregisterRijksoverheidService = mockk()
    private val mockMvc: MockMvc = standaloneSetup(Controller(service)).build()

    @Test
    fun `GET registerdata preserves the JSON response contract`() {
        val data = """[{"URL":"https://www.rijksoverheid.nl"}]"""
        every { service.getRegisterData() } returns data

        mockMvc.perform(get("/registerdata"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(data, JsonCompareMode.STRICT))

        verify(exactly = 1) { service.getRegisterData() }
    }

    @Test
    fun `GET metadata preserves the JSON response contract`() {
        val metadata = """{"registersFound":1,"columnHeaders":["URL"]}"""
        every { service.getMetadata() } returns metadata

        mockMvc.perform(get("/metadata"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(metadata, JsonCompareMode.STRICT))

        verify(exactly = 1) { service.getMetadata() }
    }

    @Test
    fun `GET checkfornew preserves the trigger response contract`() {
        every { service.checkForNewRegister() } just Runs

        mockMvc.perform(get("/checkfornew"))
            .andExpect(status().isOk)
            .andExpect(content().string("OK"))

        verify(exactly = 1) { service.checkForNewRegister() }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/registerdata", "/metadata", "/checkfornew"])
    fun `POST is not allowed for GET endpoints`(path: String) {
        mockMvc.perform(post(path))
            .andExpect(status().isMethodNotAllowed)
    }
}
