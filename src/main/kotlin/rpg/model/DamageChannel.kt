package rpg.model

import kotlinx.serialization.Serializable
import rpg.status.StatusType

@Serializable
enum class DamageChannel {
    PHYSICAL,
    MAGIC,
    FIRE,
    ICE,
    POISON,
    BLEED,
    HOLY,
    SHADOW,
    ARCANE,
    PIERCE,
    SLASH,
    BLUNT;

    companion object {
        fun fromKey(raw: String): DamageChannel? {
            val normalized = raw.trim().lowercase()
            return when (normalized) {
                "physical", "phys", "melee" -> PHYSICAL
                "magic", "magical", "spell" -> MAGIC
                "fire", "burn", "burning" -> FIRE
                "ice", "frost", "frozen" -> ICE
                "poison", "venom", "poisoned" -> POISON
                "bleed", "bleeding", "blood" -> BLEED
                "holy", "light", "sacred" -> HOLY
                "shadow", "dark", "darkness", "corruption" -> SHADOW
                "arcane", "energy" -> ARCANE
                "pierce", "piercing", "arrow" -> PIERCE
                "slash", "slashing", "cut" -> SLASH
                "blunt", "impact", "crush" -> BLUNT
                else -> entries.firstOrNull { it.name.lowercase() == normalized }
            }
        }

        fun fromStatusType(type: StatusType): DamageChannel? {
            return when (type) {
                StatusType.BURNING -> FIRE
                StatusType.POISONED -> POISON
                StatusType.BLEEDING -> BLEED
                else -> null
            }
        }
    }
}

