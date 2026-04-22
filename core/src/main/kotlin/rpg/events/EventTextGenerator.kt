package rpg.events

import kotlin.random.Random

object EventTextGenerator {
    private val liquidIntros = listOf(
        "O liquido vibra ao tocar sua pele.",
        "Voce hesita antes de beber.",
        "Algo antigo desperta no frasco.",
        "O gosto e metalico.",
        "O liquido evapora e deixa um brilho no ar.",
        "A mistura reage ao calor da sua mao."
    )

    private val npcIntros = listOf(
        "O viajante observa seu equipamento em silencio.",
        "Uma voz cansada pede ajuda na trilha.",
        "Um estranho acena de longe e se aproxima devagar.",
        "O forasteiro parece nervoso, mas nao hostil.",
        "Um viajante sujo de poeira pede sua atencao."
    )

    private val chestIntros = listOf(
        "O bau range quando voce toca a tampa.",
        "Trancas antigas cobrem o recipiente de madeira.",
        "O metal frio do bau parece recem-polido.",
        "Marcas de garra cercam o bau abandonado.",
        "A fechadura esta torta, como se alguem fugisse com pressa."
    )

    private val sensations = listOf(
        "Seu coracao acelera.",
        "Sua visao distorce por um instante.",
        "Sua respiracao fica pesada.",
        "Voce sente o corpo mais leve.",
        "Uma onda curta de energia passa por voce.",
        "A sensacao some tao rapido quanto veio."
    )

    fun generate(source: EventSource, rarity: Rarity, effects: List<EventEffect>, rng: Random): String {
        val intro = when (source) {
            EventSource.LIQUID -> liquidIntros.random(rng)
            EventSource.NPC_HELP -> npcIntros.random(rng)
            EventSource.CHEST_REWARD -> chestIntros.random(rng)
        }
        val sensation = sensations.random(rng)
        val tone = when (rarity) {
            Rarity.LEGENDARY -> "A energia e avassaladora."
            Rarity.EPIC -> "Algo incomum acabou de acontecer."
            Rarity.RARE -> "Existe valor real nessa escolha."
            Rarity.COMMON -> "Nada explode, mas algo mudou."
        }
        val hint = effectHint(effects)

        val lines = linkedSetOf(intro, sensation, tone, hint)
        return lines.joinToString(" ")
    }

    private fun effectHint(effects: List<EventEffect>): String {
        return when {
            effects.any { it is EventEffect.DamagePercentCurrent } -> "Nem todo ganho vem sem risco."
            effects.any { it is EventEffect.AddGold } -> "Algumas moedas mudaram de dono."
            effects.any { it is EventEffect.AddItem } -> "Voce encontra algo util para seguir."
            effects.any { it is EventEffect.HealFlat || it is EventEffect.HealPercentCurrent || it is EventEffect.HealPercentMax } ->
                "Seu corpo responde com alivio imediato."
            effects.any { it is EventEffect.BuffAllAttributes || it is EventEffect.BuffAttribute || it is EventEffect.BuffAttributePercent } ->
                "Seu desempenho melhora de forma notavel."
            effects.any { it is EventEffect.PermanentAllAttributes || it is EventEffect.PermanentBuff || it is EventEffect.PermanentAttributePercent } ->
                "A mudanca parece mais profunda e duradoura."
            else -> "O ambiente volta ao silencio."
        }
    }
}
