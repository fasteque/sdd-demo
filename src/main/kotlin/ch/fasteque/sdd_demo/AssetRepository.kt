package ch.fasteque.sdd_demo

import org.springframework.data.mongodb.repository.MongoRepository

interface AssetRepository : MongoRepository<Asset, String>
