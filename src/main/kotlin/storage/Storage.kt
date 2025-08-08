
package storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import model.Player
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
private data class SaveState(val players: Map<String, Player> = emptyMap())

class Storage(baseDir: String) {
    private val dir: Path = Path.of(baseDir)
    private val file: Path = dir.resolve("players.json")
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    init { if (!dir.exists()) dir.createDirectories() }

    fun load(): ConcurrentHashMap<Long, Player> {
        if (!Files.exists(file)) return ConcurrentHashMap()
        return try {
            val state: SaveState = json.decodeFromString(file.readText())
            ConcurrentHashMap(state.players.mapKeys { it.key.toLong() })
        } catch (_: Exception) {
            ConcurrentHashMap()
        }
    }

    @Synchronized
    fun save(players: Map<Long, Player>) {
        val tmp = dir.resolve("players.tmp.json")
        val state = SaveState(players.mapKeys { it.key.toString() })
        tmp.writeText(json.encodeToString(state))
        Files.move(
            tmp, file,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }
}
