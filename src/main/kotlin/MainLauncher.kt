
import bot.FishBot

fun main() {
    val token = System.getenv("TG_BOT_TOKEN")
        ?: try { java.io.File("token.txt").takeIf { it.exists() }?.readText()?.trim() } catch (_: Exception) { null }
        ?: throw IllegalStateException("TG_BOT_TOKEN not set and token.txt not found")

    val dataDir = System.getenv("DATA_DIR") ?: "./data"
    println("🚀 FishSim-TG starting... dataDir=$dataDir")
    FishBot(token, dataDir).start()
}
