package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class RoomDef(
    val id: String,
    val name: String,
    val description: String,
    val monsters: List<String> = emptyList(),
    val connections: List<String> = emptyList()
)

@Serializable
data class MapDef(
    val id: String,
    val name: String,
    val startRoomId: String,
    val rooms: List<RoomDef>
)
