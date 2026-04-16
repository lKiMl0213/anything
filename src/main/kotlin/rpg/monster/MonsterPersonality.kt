package rpg.monster

enum class MonsterPersonality(val preferMagic: Boolean?) {
    BRUTAL(false),
    ARCANE(true),
    BALANCED(null),
    SWIFT(false),
    MYSTIC(true);

    companion object {
        fun roll(rng: kotlin.random.Random): MonsterPersonality {
            return values().random(rng)
        }
    }
}
