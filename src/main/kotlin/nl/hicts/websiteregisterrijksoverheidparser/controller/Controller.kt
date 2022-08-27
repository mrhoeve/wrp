package nl.hicts.websiteregisterrijksoverheidparser.controller

import nl.hicts.websiteregisterrijksoverheidparser.service.WebsiteregisterRijksoverheidService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class Controller(private val service: WebsiteregisterRijksoverheidService) {
    @GetMapping("/registerdata", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getRegisterData(): ResponseEntity<String> {
        return ResponseEntity(service.getRegisterData(), HttpStatus.OK)
    }

    @GetMapping("/metadata", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getMetadata(): ResponseEntity<String> {
        return ResponseEntity(service.getMetadata(), HttpStatus.OK)
    }

    @GetMapping("/checkfornew")
    fun checkForNewRegister(): ResponseEntity<String> {
        service.checkForNewRegister()
        return ResponseEntity("OK", HttpStatus.OK)
    }
}
