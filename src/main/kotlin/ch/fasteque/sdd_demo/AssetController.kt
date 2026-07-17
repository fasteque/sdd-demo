package ch.fasteque.sdd_demo

import ch.fasteque.sdd_demo.generated.api.AssetsApi
import ch.fasteque.sdd_demo.generated.model.Asset as AssetResponse
import ch.fasteque.sdd_demo.generated.model.AssetPage
import ch.fasteque.sdd_demo.generated.model.CreateAssetRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private fun Asset.toResponse(): AssetResponse =
	AssetResponse(
		id = checkNotNull(id) { "cannot map an unsaved Asset to a response (id is null)" },
		name = name,
		type = type,
		tags = tags,
		status = status,
	)

@RestController
class AssetController(private val assetRepository: AssetRepository) : AssetsApi {

	override fun createAsset(createAssetRequest: CreateAssetRequest): ResponseEntity<AssetResponse> {
		val asset = Asset(
			name = createAssetRequest.name,
			type = createAssetRequest.type,
			tags = createAssetRequest.tags ?: emptyList(),
			status = createAssetRequest.status,
		)
		val saved = assetRepository.save(asset)
		return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
	}

	override fun getAsset(id: String): ResponseEntity<AssetResponse> {
		val asset = assetRepository.findById(id)
			.orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "asset not found") }
		return ResponseEntity.ok(asset.toResponse())
	}

	override fun deleteAsset(id: String): ResponseEntity<Unit> {
		if (!assetRepository.existsById(id)) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, "asset not found")
		}
		assetRepository.deleteById(id)
		return ResponseEntity.noContent().build()
	}

	override fun listAssets(page: Int, size: Int): ResponseEntity<AssetPage> {
		val result = assetRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "_id")))
		return ResponseEntity.ok(
			AssetPage(
				content = result.content.map { it.toResponse() },
				page = result.number,
				propertySize = result.size,
				totalElements = result.totalElements,
				totalPages = result.totalPages,
			)
		)
	}
}
