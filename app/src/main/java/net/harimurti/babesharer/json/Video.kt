package net.harimurti.babesharer.json
import kotlinx.serialization.*

@Serializable
data class Video(
    val videoUrl: String,
    val videoDescription: String,
    val videoType: String,
    val videoDuration: Int,
    val videoWidth: Int,
    val videoHeight: Int,
    val videoUnique: String,
    val videoTitle: String
)