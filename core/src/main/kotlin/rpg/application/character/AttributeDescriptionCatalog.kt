package rpg.application.character

internal object AttributeDescriptionCatalog {
    fun directEffects(attributeCode: String): List<String> = when (attributeCode.uppercase()) {
        "STR" -> listOf(
            "Dano físico: +0,9 por ponto.",
            "Penetração física: +0,5 por ponto.",
            "Defesa física: +0,8 por ponto.",
            "Dano crítico: +0,1% por ponto."
        )

        "AGI" -> listOf(
            "Dano físico: +0,8 por ponto.",
            "Velocidade de ataque: +0,02 por ponto.",
            "Velocidade de movimento: +0,05 por ponto.",
            "Evasão: +0,8 por ponto."
        )

        "DEX" -> listOf(
            "Dano físico: +0,8 por ponto.",
            "Penetração física: +0,02 por ponto.",
            "Velocidade de ataque: +0,02 por ponto.",
            "Velocidade de movimento: +0,02 por ponto.",
            "Crítico: +0,3% por ponto.",
            "Precisão: +0,8 por ponto.",
            "Evasão: +0,5 por ponto."
        )

        "VIT" -> listOf(
            "HP máximo: +10 por ponto.",
            "MP máximo: +0,1 por ponto.",
            "Defesa física: +0,9 por ponto.",
            "Regeneração de HP: +0,2 por ponto.",
            "Tenacidade: +0,18% por ponto."
        )

        "INT" -> listOf(
            "Dano mágico: +1,8 por ponto.",
            "Defesa mágica: +0,9 por ponto.",
            "Penetração mágica: +0,5 por ponto."
        )

        "SPR" -> listOf(
            "Dano mágico: +0,8 por ponto.",
            "HP máximo: +0,1 por ponto.",
            "MP máximo: +10 por ponto.",
            "Defesa mágica: +1,5 por ponto.",
            "Penetração mágica: +0,5 por ponto.",
            "Regeneração de HP: +0,5 por ponto.",
            "Regeneração de MP: +0,5 por ponto.",
            "Recarga: +0,05% por ponto.",
            "Vampirismo: +0,01% por ponto."
        )

        "LUK" -> listOf(
            "Defesa física: +0,4 por ponto.",
            "Defesa mágica: +0,2 por ponto.",
            "Crítico: +0,5% por ponto.",
            "Dano crítico: +0,5% por ponto.",
            "Vampirismo: +0,1% por ponto.",
            "Recarga: +0,05% por ponto.",
            "Bônus de drop: +0,18% por ponto.",
            "Penetração física: +0,01 por ponto.",
            "Penetração mágica: +0,2 por ponto.",
            "Precisão: +0,5 por ponto.",
            "Evasão: +0,2 por ponto.",
            "Tenacidade: +0,2% por ponto."
        )

        else -> listOf("Sem descrição configurada.")
    }

    fun gameplayImpact(attributeCode: String): List<String> = when (attributeCode.uppercase()) {
        "STR" -> listOf(
            "Aumenta o impacto de builds físicas e melhora dano crítico de forma complementar.",
            "Escala junto com DEX e AGI no dano físico total."
        )

        "AGI" -> listOf(
            "Melhora ritmo de combate e mobilidade com ganho direto de evasão.",
            "Também reforça dano físico em builds agressivas."
        )

        "DEX" -> listOf(
            "Atributo híbrido para dano físico, consistência de acerto e chance crítica.",
            "Sinergiza com AGI (velocidade/evasão) e STR (dano bruto)."
        )

        "VIT" -> listOf(
            "Principal fonte de sobrevivência bruta (HP, defesa física e tenacidade).",
            "Sustenta trocas longas por aumentar regeneração de HP."
        )

        "INT" -> listOf(
            "Base ofensiva das builds mágicas, com ganho direto em penetração e defesa mágica.",
            "Combina bem com SPR para dano/sustain mágico completos."
        )

        "SPR" -> listOf(
            "Atributo de sustain mágico: mana, defesa mágica, regenerações e recarga.",
            "Também contribui no dano e na penetração mágica."
        )

        "LUK" -> listOf(
            "Atributo utilitário/ofensivo com muitos cross-scalings (crítico, drop, vampirismo, precisão e defesas).",
            "Excelente como complemento para várias builds, sem substituir o atributo principal."
        )

        else -> listOf("Sem impacto configurado.")
    }
}




