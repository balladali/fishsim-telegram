package bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.logging.LogLevel
import model.GameData
import model.Player
import model.Rod
import storage.Storage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class FishBot(token:String, dataDir:String = "./data"){
    private val storage = Storage(dataDir)
    private val players: ConcurrentHashMap<Long, Player> = storage.load()

    // debounce save
    private val saver = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var saveScheduled = false
    private fun persist() {
        if (saveScheduled) return
        saveScheduled = true
        saver.schedule({
            try { storage.save(players) } finally { saveScheduled = false }
        }, 500, TimeUnit.MILLISECONDS)
    }

    private val bot: Bot = bot {
        this.token = token
        logLevel = LogLevel.Network.Body

        dispatch {
            command("start") {
                val msg = message ?: return@command
                val user = msg.from ?: return@command
                val chatId = msg.chat.id
                val pl = players.computeIfAbsent(user.id){ Player(user.id, user.firstName ?: "Anon") }
                persist()
                sendMessage(chatId, withBoard("🎣 Добро пожаловать в FishSim, ${pl.nick}!"), mainMenu())
            }

            callbackQuery {
                val cq = callbackQuery ?: return@callbackQuery
                val data = cq.data ?: return@callbackQuery
                val from = cq.from
                val chatId = cq.message?.chat?.id ?: return@callbackQuery
                val mid: Long = cq.message?.messageId ?: return@callbackQuery

                val pl = players.computeIfAbsent(from.id){ Player(from.id, from.firstName ?: "Anon") }

                when {
                    data == "menu" -> editMessage(chatId, mid, withBoard("Главное меню:"), mainMenu())
                    data.startsWith("shop") -> handleShop(chatId, mid, pl, data)
                    data.startsWith("loc")  -> chooseLocation(chatId, mid, pl, data)
                    data == "invent"        -> showInvent(chatId, mid, pl)
                    data == "sell"          -> sellAll(chatId, mid, pl)
                    data == "score"         -> editMessage(chatId, mid, withBoard("🏆 Таблица лидеров"), ikm(row(btn("⬅️ Назад","menu"))))
                }
                bot.answerCallbackQuery(cq.id)
            }
        }
    }

    fun start() = bot.startPolling()

    /* ---------- UI ---------- */

    private fun mainMenu(): InlineKeyboardMarkup =
        ikm(
            row(btn("🛒 Магазин","shop")),
            row(btn("🎣 Заброс","loc")),
            row(btn("📦 Инвентарь","invent")),
            row(btn("💰 Продать рыбу","sell")),
            row(btn("📊 Таблица лидеров","score"))
        )

    private fun shopMenu(pl:Player): InlineKeyboardMarkup =
        ikm(
            listOfNotNull(
                if (pl.rod==Rod.BASIC) btn("Купить удилище PRO — 120₽","shop_buy_pro") else null,
                btn("⬅️ Назад","menu")
            ).let { row(*it.toTypedArray()) }
        )

    private fun locMenu(): InlineKeyboardMarkup =
        ikm(
            row(*GameData.locations.map { btn("🎣 ${it.second}", "loc_${it.first}") }.toTypedArray()),
            row(btn("⬅️ Назад","menu"))
        )

    /* ---------- leaderboard helpers ---------- */

    private fun renderLeaderboard(limit: Int = 10): String {
        val table = players.values
            .sortedByDescending { it.lifetimeWeight } // рейтинг по накопленному весу
            .take(limit)
            .mapIndexed { i, p ->
                "${i + 1}. ${p.nick} — %.2f кг (доход: %d₽, уловов: %d)"
                    .format(p.lifetimeWeight, p.lifetimeEarnings, p.fishCaught)
            }
            .joinToString("\n")

        val body = table.ifBlank { "Пока никто ничего не поймал." }
        return "🏆 Таблица лидеров\n$body"
    }

    private fun withBoard(text: String): String {
        val base = if (text.length > 3800) text.take(3800) + "…" else text
        return "$base\n${renderLeaderboard()}"
    }

    /* ---------- Handlers ---------- */

    private fun handleShop(chatId: Long, mid: Long, pl:Player, data:String){
        if (data=="shop") {
            editMessage(chatId, mid, withBoard("Баланс: ${pl.coins}₽\nВыберите товар:"), shopMenu(pl))
            return
        }
        if (data=="shop_buy_pro") {
            if (pl.coins < Rod.PRO.price) {
                editMessage(chatId, mid, withBoard("Недостаточно средств. Баланс: ${pl.coins}₽"), shopMenu(pl))
                return
            }
            pl.coins -= Rod.PRO.price
            pl.rod = Rod.PRO
            persist()
            editMessage(chatId, mid, withBoard("Поздравляем, вы купили PRO-удилище!\nБаланс: ${pl.coins}₽"), mainMenu())
        }
    }

    private fun chooseLocation(chatId:Long, mid: Long, pl:Player, data:String){
        if (data=="loc") {
            editMessage(chatId, mid, withBoard("Где будем рыбачить?"), locMenu())
            return
        }
        val locId = data.removePrefix("loc_")
        editMessage(chatId, mid, withBoard("Заброс удочки... ⏳"), null)
        thread {
            Thread.sleep(2_000)
            val fish = GameData.rollFish(locId)
            pl.bag += fish
            pl.lifetimeWeight += fish.weight
            pl.fishCaught += 1
            persist()
            sendMessage(chatId, withBoard("🐟 Улов: %.2f кг %s (≈%d₽)".format(fish.weight, fish.species, fish.price)), mainMenu())
        }
    }

    private fun showInvent(chatId:Long, mid: Long, pl:Player){
        val txt = if(pl.bag.isEmpty()) "Садок пуст."
                  else pl.bag.joinToString("\n"){ "%.2f кг %s".format(it.weight,it.species) }
        editMessage(chatId, mid, withBoard(txt), ikm(row(btn("⬅️ Назад","menu"))))
    }

    private fun sellAll(chatId:Long, mid: Long, pl:Player){
        val sum = pl.bag.sumOf { it.price }
        pl.coins += sum
        pl.lifetimeEarnings += sum
        pl.bag.clear()
        persist()
        editMessage(chatId, mid, withBoard("Продали рыбу на $sum₽. Баланс: ${pl.coins}₽"), mainMenu())
    }

    /* ---------- low-level helpers ---------- */

    private fun sendMessage(chatId: Long, text: String, markup: InlineKeyboardMarkup? = null) {
        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, replyMarkup = markup)
    }

    private fun editMessage(chatId: Long, messageId: Long, text: String, markup: InlineKeyboardMarkup? = null) {
        bot.editMessageText(chatId = ChatId.fromId(chatId), messageId = messageId, text = text, replyMarkup = markup)
    }

    private fun ikm(vararg rows: List<InlineKeyboardButton>) =
        InlineKeyboardMarkup.create(*rows)

    private fun row(vararg btns: InlineKeyboardButton) = btns.toList()

    private fun btn(text:String, data:String) = InlineKeyboardButton.CallbackData(text, data)
}
