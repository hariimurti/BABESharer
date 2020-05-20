package net.harimurti.babesharer.json
import kotlinx.serialization.*

@Serializable
data class Article(
    val articleType: String,
    val groupId: String,
    val articleId: String,
    val title: String,
    val content: String?,
    val publishTime: Int,
    val video: Video?
)