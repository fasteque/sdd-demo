package ch.fasteque.sdd_demo

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "assets")
data class Asset(
	@Id val id: String? = null,
	val name: String,
	val type: String,
	val tags: List<String>,
	val status: String,
)
