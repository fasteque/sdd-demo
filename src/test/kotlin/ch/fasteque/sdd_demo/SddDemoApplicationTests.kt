package ch.fasteque.sdd_demo

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate
import kotlin.test.assertEquals

@SpringBootTest
class SddDemoApplicationTests {

	@Autowired
	lateinit var mongoTemplate: MongoTemplate

	@Test
	fun contextLoads() {
	}

	@Test
	fun `connects to the database named in spring mongodb uri`() {
		assertEquals("sdddemo", mongoTemplate.db.name)
	}

}
