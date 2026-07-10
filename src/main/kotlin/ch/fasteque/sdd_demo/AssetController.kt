package ch.fasteque.sdd_demo

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class CreateAssetRequest(
	val name: String?,
	val type: String?,
	val tags: List<String>? = null,
	val status: String?,
)

data class AssetPage(
	val content: List<Asset>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
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

	@GetMapping("/assets/{id}")
	fun getAsset(@PathVariable id: String): Asset {
		return assetRepository.findById(id)
			.orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "asset not found") }
	}

	@GetMapping("/assets")
	fun listAssets(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): AssetPage {
		if (page < 0) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative")
		}
		if (size < 1) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be positive")
		}

		val result = assetRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "_id")))
		return AssetPage(
			content = result.content,
			page = result.number,
			size = result.size,
			totalElements = result.totalElements,
			totalPages = result.totalPages,
		)
	}
}
