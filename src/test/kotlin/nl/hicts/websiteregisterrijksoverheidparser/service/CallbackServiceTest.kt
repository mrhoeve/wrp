package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.*
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.io.IOException

class CallbackServiceTest {
    private val callbackURL = "http://localhost"
    private val callbackParams = "test"

    private val service = CallbackService()
    private val connectionMock: Connection = mockk()

    @BeforeEach
    fun setUp() {
        mockkStatic(Jsoup::class)
        every { Jsoup.connect(any()) } returns connectionMock
    }

    @Test
    fun `performCallback without specified callbackURL does nothing`() {
        service.performCallback()
        verify { connectionMock wasNot called }
    }

    @Test
    fun `performCallback without params succeeds`() {
        setupCallbackURL()

        every { connectionMock.get() } returns mockk()

        service.performCallback()

        verify {
            Jsoup.connect(withArg {
                assertTrue(callbackURL == it)
            })
        }
    }

    @Test
    fun `performCallback with params succeeds`() {
        setupCallbackURL()
        setupCallbackParams()

        every { connectionMock.get() } returns mockk()

        service.performCallback()

        verify {
            Jsoup.connect(withArg {
                assertTrue("$callbackURL?$callbackParams" == it)
            })
        }
    }

    @Test
    fun `performCallback receives exception and handles it correctly`() {
        setupCallbackURL()

        every { connectionMock.get() } throws IOException()

        service.performCallback()

        verify {
            Jsoup.connect(withArg {
                assertTrue(callbackURL == it)
            })
        }
    }

    private fun setupCallbackURL() {
        ReflectionTestUtils.setField(
            service,
            "callbackURL",
            callbackURL
        )
    }

    private fun setupCallbackParams() {
        ReflectionTestUtils.setField(
            service,
            "callbackparameter",
            callbackParams
        )
    }

    @AfterEach
    fun `remove all static mockks`() {
        unmockkAll()
    }
}
