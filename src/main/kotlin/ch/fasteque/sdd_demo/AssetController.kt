package ch.fasteque.sdd_demo

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class CreateAssetRequest(
	val name: String?,
	val type: String?,
	val tags: List<String>? = null,
	val status: String?,
)

@RestController
class AssetController(private val assetRepository: AssetRepository) {

	@PostMapping("/assets")
	@ResponseStatus(HttpStatus.CREATED)
	fun createAsset(@RequestBody request: CreateAssetRequest): Asset {
		val name = request.name?.takeIf { it.isNotBlank() }
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required")
		val type = request.type?.takeIf { it.isNotBlank() }
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required")
		val status = request.status?.takeIf { it.isNotBlank() }
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required")

		val asset = Asset(
			name = name,
			type = type,
			tags = request.tags ?: emptyList(),
			status = status,
		)
		return assetRepository.save(asset)
	}
}
