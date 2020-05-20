package net.harimurti.babesharer.json
import kotlinx.serialization.*

@Serializable
data class Babe(
    val article: Article
)