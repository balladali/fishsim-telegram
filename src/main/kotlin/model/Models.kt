package model

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable enum class Rod(val price:Int,val power:Int){ BASIC(50,1), PRO(120,2) }

@Serializable data class Fish(val species:String,val weight:Double,val price:Int)

@Serializable data class Player(
    val id:Long,
    var nick:String,
    var coins:Int = 100,
    var rod:Rod = Rod.BASIC,
    val bag:MutableList<Fish> = mutableListOf()
){
    val totalWeight get() = bag.sumOf { it.weight }
}

object GameData {
    val locations = listOf(
        "lake" to "Озеро",
        "river" to "Река",
        "sea" to "Море"
    )
    private val tables = mapOf(
        "lake" to listOf("Карась" to 60, "Карп" to 30, "Щука" to 10),
        "river" to listOf("Плотва" to 50, "Окунь" to 35, "Сом" to 15),
        "sea"  to listOf("Сардина" to 40, "Сибас" to 40, "Тунец" to 20)
    )
    fun rollFish(loc:String):Fish{
        val table = tables.getValue(loc).flatMap { (s,ch)-> List(ch){s} }
        val species = table.random()
        val w = Random.nextDouble(0.5, if(species=="Тунец")15.0 else 4.0)
        val p = (w * when(species){
            "Карась","Плотва","Сардина"->1.2
            "Карп","Окунь","Сибас"->2.5
            else->4.0 }).toInt()
        return Fish(species,w,p)
    }
}
