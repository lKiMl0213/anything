package rpg.io

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object JsonStore {
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    inline fun <reified T> load(path: Path): T {
        val content = Files.readString(path)
        return json.decodeFromString(content)
    }

    inline fun <reified T> save(path: Path, value: T) {
        path.parent?.let { Files.createDirectories(it) }
        val content = json.encodeToString(value)
        Files.writeString(path, content)
    }
}
