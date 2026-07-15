package ch.fasteque.sdd_demo

import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.beans.factory.annotation.Autowired

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTests {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Test
	fun `returns UP status`() {
		mockMvc.perform(get("/health"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.status").value("UP"))
	}
}
