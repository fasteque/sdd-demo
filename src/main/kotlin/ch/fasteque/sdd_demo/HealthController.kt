package ch.fasteque.sdd_demo

import ch.fasteque.sdd_demo.generated.api.HealthApi
import ch.fasteque.sdd_demo.generated.model.HealthStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController : HealthApi {

	override fun getHealth(): ResponseEntity<HealthStatus> =
		ResponseEntity.ok(HealthStatus(status = "UP"))
}
