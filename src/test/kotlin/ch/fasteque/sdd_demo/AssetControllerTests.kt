package ch.fasteque.sdd_demo

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
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
}
