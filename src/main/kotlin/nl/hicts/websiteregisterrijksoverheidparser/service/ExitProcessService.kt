package nl.hicts.websiteregisterrijksoverheidparser.service

import org.springframework.stereotype.Service
import kotlin.system.exitProcess

@Service
class ExitProcessService {

    /**
     * This causes the termination of the running process
     * Due to its nature, it's untestable
     */
    fun terminateApplicationWithError() {
        exitProcess(1)
    }
}
