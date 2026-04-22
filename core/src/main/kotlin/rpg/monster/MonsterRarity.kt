package rpg.monster

enum class MonsterRarity(
    val statMultiplier: Double
) {
    COMMON(1.0),
    ELITE(1.2),
    RARE(1.4),
    EPIC(1.6),
    LEGENDARY(2.0)
}
