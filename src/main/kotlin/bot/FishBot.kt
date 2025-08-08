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
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class FishBot(token:String){
    private val players = ConcurrentHashMap<Long,Player>()

    private val bot: Bot = bot {
        this.token = token
        logLevel = LogLevel.Network.Body

        dispatch {
            command("start") {
                val msg = message ?: return@command
                val user = msg.from ?: return@command
                val chatId = msg.chat.id
                val pl = players.computeIfAbsent(user.id){ Player(user.id, user.firstName ?: "Anon") }
                sendMessage(chatId, "🎣 Добро пожаловать в FishSim, ${pl.nick}!", mainMenu())
            }

            callbackQuery {
                val cq = callbackQuery ?: return@callbackQuery
                val data = cq.data ?: return@callbackQuery
                val from = cq.from
                val chatId = cq.message?.chat?.id ?: return@callbackQuery
                val mid = cq.message?.messageId ?: return@callbackQuery

                val pl = players.computeIfAbsent(from.id){ Player(from.id, from.firstName ?: "Anon") }

                when {
                    data == "menu" -> editMessage(chatId, mid, "Главное меню:", mainMenu())
                    data.startsWith("shop") -> handleShop(chatId, mid, pl, data)
                    data.startsWith("loc")  -> chooseLocation(chatId, mid, pl, data)
                    data == "invent"        -> showInvent(chatId, mid, pl)
                    data == "sell"          -> sellAll(chatId, mid, pl)
                    data == "score"         -> score(chatId, mid)
                }
                // Stop the loading spinner on the button
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

    /* ---------- Handlers ---------- */

    private fun handleShop(chatId: Long, mid:Long, pl:Player, data:String){
        if (data=="shop") {
            editMessage(chatId, mid, "Баланс: ${pl.coins}₽\nВыберите товар:", shopMenu(pl))
            return
        }
        if (data=="shop_buy_pro") {
            if (pl.coins < Rod.PRO.price) {
                answerAlert("Недостаточно средств")
                return
            }
            pl.coins -= Rod.PRO.price
            pl.rod = Rod.PRO
            editMessage(chatId, mid, "Поздравляем, вы купили PRO-удилище!\nБаланс: ${pl.coins}₽", mainMenu())
        }
    }

    private fun chooseLocation(chatId:Long, mid:Long, pl:Player, data:String){
        if (data=="loc") {
            editMessage(chatId, mid, "Где будем рыбачить?", locMenu())
            return
        }
        val locId = data.removePrefix("loc_")
        editMessage(chatId, mid, "Заброс удочки... ⏳", null)
        thread {
            Thread.sleep(2_000)
            val fish = GameData.rollFish(locId)
            pl.bag += fish
            sendMessage(chatId, "🐟 Улов: %.2f кг %s (≈%d₽)".format(fish.weight, fish.species, fish.price), mainMenu())
        }
    }

    private fun showInvent(chatId:Long, mid:Long, pl:Player){
        val txt = if(pl.bag.isEmpty()) "Садок пуст."
                  else pl.bag.joinToString("\n"){ "%.2f кг %s".format(it.weight,it.species) }
        editMessage(chatId, mid, txt, ikm(row(btn("⬅️ Назад","menu"))))
    }

    private fun sellAll(chatId:Long, mid:Long, pl:Player){
        val sum = pl.bag.sumOf { it.price }
        pl.coins += sum
        pl.bag.clear()
        editMessage(chatId, mid, "Продали рыбу на $sum₽. Баланс: ${pl.coins}₽", mainMenu())
    }

    private fun score(chatId:Long, mid:Long){
        val table = players.values.sortedByDescending { it.totalWeight }
            .withIndex()
            .joinToString("\n"){ (i,p)-> "${i+1}. ${p.nick} — %.2f кг".format(p.totalWeight) }
            .ifBlank { "Пока никто ничего не поймал." }
        editMessage(chatId, mid, "🏆 Таблица лидеров\n\n$table", ikm(row(btn("⬅️ Назад","menu"))))
    }

    /* ---------- helpers ---------- */

    private fun sendMessage(chatId: Long, text: String, markup: InlineKeyboardMarkup? = null) {
        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, replyMarkup = markup)
    }

    private fun editMessage(chatId: Long, messageId: Long, text: String, markup: InlineKeyboardMarkup? = null) {
        bot.editMessageText(chatId = ChatId.fromId(chatId), messageId = messageId, text = text, replyMarkup = markup)
    }

    private fun answerAlert(text:String){
        // Utility placeholder if you want to show alerts via answerCallbackQuery with showAlert=true.
        // Implemented in-place in handlers when needed.
    }

    private fun ikm(vararg rows: List<InlineKeyboardButton>) =
        InlineKeyboardMarkup.create(*rows)

    private fun row(vararg btns: InlineKeyboardButton) = btns.toList()

    private fun btn(text:String, data:String) = InlineKeyboardButton.CallbackData(text, data)
}
