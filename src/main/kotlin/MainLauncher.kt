import bot.FishBot

fun main() {
    val token = System.getenv("TG_BOT_TOKEN")
        ?: try {
            java.io.File("token.txt").takeIf { it.exists() }?.readText()?.trim()
        } catch (e: Exception) { null }
        ?: throw IllegalStateException("TG_BOT_TOKEN not set and token.txt not found")

    println("🚀 FishSim-TG starting...")
    FishBot(token).start()
}
