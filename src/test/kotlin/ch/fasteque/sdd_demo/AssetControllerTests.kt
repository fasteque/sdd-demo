package ch.fasteque.sdd_demo

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class AssetControllerTests {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var assetRepository: AssetRepository

	@AfterEach
	fun cleanUp() {
		assetRepository.deleteAll()
	}

	@Test
	fun `creates asset and persists it to MongoDB`() {
		mockMvc.perform(
			post("/assets")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"name":"Cover Photo","type":"image","tags":["hero","featured"],"status":"draft"}""")
		)
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.id").exists())
			.andExpect(jsonPath("$.name").value("Cover Photo"))
			.andExpect(jsonPath("$.type").value("image"))
			.andExpect(jsonPath("$.status").value("draft"))

		val savedAssets = assetRepository.findAll()
		assertEquals(1, savedAssets.size)
		assertEquals("Cover Photo", savedAssets[0].name)
		assertEquals(listOf("hero", "featured"), savedAssets[0].tags)
	}

	@Test
	fun `rejects request missing required field`() {
		mockMvc.perform(
			post("/assets")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"type":"image","tags":[],"status":"draft"}""")
		)
			.andExpect(status().isBadRequest)

		assertTrue(assetRepository.findAll().isEmpty())
	}

	@Test
	fun `defaults tags to empty list when omitted`() {
		mockMvc.perform(
			post("/assets")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"name":"Untagged Asset","type":"document","status":"published"}""")
		)
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.tags").isEmpty)

		val savedAssets = assetRepository.findAll()
		assertEquals(1, savedAssets.size)
		assertTrue(savedAssets[0].tags.isEmpty())
	}

	@Test
	fun `returns asset by id`() {
		val saved = assetRepository.save(
			Asset(name = "Cover Photo", type = "image", tags = listOf("hero"), status = "draft")
		)

		mockMvc.perform(get("/assets/${saved.id}"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.id").value(saved.id))
			.andExpect(jsonPath("$.name").value("Cover Photo"))
			.andExpect(jsonPath("$.type").value("image"))
			.andExpect(jsonPath("$.status").value("draft"))
	}

	@Test
	fun `returns 404 when asset id does not exist`() {
		mockMvc.perform(get("/assets/does-not-exist"))
			.andExpect(status().isNotFound)
	}

	@Test
	fun `lists assets with default pagination`() {
		assetRepository.save(Asset(name = "Asset 1", type = "image", tags = emptyList(), status = "draft"))
		assetRepository.save(Asset(name = "Asset 2", type = "image", tags = emptyList(), status = "draft"))

		mockMvc.perform(get("/assets"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(1))
	}

	@Test
	fun `lists assets with explicit page and size`() {
		for (i in 1..6) {
			assetRepository.save(Asset(name = "Asset $i", type = "image", tags = emptyList(), status = "draft"))
		}

		mockMvc.perform(get("/assets?page=1&size=5"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(5))
			.andExpect(jsonPath("$.totalElements").value(6))
			.andExpect(jsonPath("$.totalPages").value(2))
	}

	@Test
	fun `returns empty content when page is beyond available data`() {
		assetRepository.save(Asset(name = "Asset 1", type = "image", tags = emptyList(), status = "draft"))

		mockMvc.perform(get("/assets?page=5"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.content.length()").value(0))
			.andExpect(jsonPath("$.page").value(5))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1))
	}

	@Test
	fun `rejects negative page`() {
		mockMvc.perform(get("/assets?page=-1"))
			.andExpect(status().isBadRequest)
	}

	@Test
	fun `rejects size less than 1`() {
		mockMvc.perform(get("/assets?size=0"))
			.andExpect(status().isBadRequest)
	}

	@Test
	fun `deletes asset and removes it from MongoDB`() {
		val saved = assetRepository.save(
			Asset(name = "Cover Photo", type = "image", tags = listOf("hero"), status = "draft")
		)

		mockMvc.perform(delete("/assets/${saved.id}"))
			.andExpect(status().isNoContent)

		assertTrue(assetRepository.findById(saved.id!!).isEmpty)
	}

	@Test
	fun `returns 404 when deleting an asset id that does not exist`() {
		mockMvc.perform(delete("/assets/does-not-exist"))
			.andExpect(status().isNotFound)
	}
}
