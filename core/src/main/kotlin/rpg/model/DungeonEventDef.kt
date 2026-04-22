package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class DungeonEventRulesDef(
    val npcMoneyWeight: Int = 40,
    val npcItemWeight: Int = 40,
    val npcSuspiciousWeight: Int = 20,
    val npcMoneyRefuseAmbushChancePct: Int = 28,
    val npcMoneyNoGoldAmbushChancePct: Int = 35,
    val npcMoneyGiveScamChancePct: Int = 15,
    val npcMoneyGiveRewardChancePct: Int = 70,
    val npcItemRefuseAmbushChancePct: Int = 22,
    val npcItemGiveScamChancePct: Int = 10,
    val npcItemGiveRewardChancePct: Int = 78,
    val npcSuspiciousAmbushChancePct: Int = 40,
    val chestMimicFastChancePct: Int = 30,
    val chestMimicInspectChancePct: Int = 12
)

@Serializable
data class DungeonEventTextPoolsDef(
    val npcIntro: List<String> = listOf(
        "um viajante pede ajuda para seguir viagem.",
        "um forasteiro cansado bloqueia o caminho e pede apoio.",
        "um estranho acena e fala que precisa de ajuda imediata."
    ),
    val npcMoneyPitch: List<String> = listOf(
        "Ele pede {gold} de ouro para pagar uma passagem segura.",
        "O viajante afirma que precisa de {gold} de ouro para atravessar a regiao.",
        "Ele pede {gold} de ouro e promete retribuir depois."
    ),
    val npcMoneyRefuseLines: List<String> = listOf(
        "O pedido era uma isca. Voce cai em uma emboscada.",
        "O estranho muda de postura. A emboscada comeca.",
        "A recusa irrita o forasteiro e o ataque vem das sombras."
    ),
    val npcMoneyNoGoldLines: List<String> = listOf(
        "Voce nao tem ouro suficiente.",
        "Seu bolso vazio nao convence o viajante.",
        "Falta ouro para aceitar o pedido."
    ),
    val npcMoneyScamLines: List<String> = listOf(
        "A doacao era apenas uma isca para te atrasar.",
        "Ele pega o ouro e assovia para comparsas escondidos.",
        "Voce paga, mas percebe tarde demais que era armadilha."
    ),
    val npcMoneyRewardLines: List<String> = listOf(
        "Ele agradece e compartilha informacoes uteis.",
        "O viajante cumpre a palavra e ajuda com algo valioso.",
        "A ajuda volta em forma de recompensa inesperada."
    ),
    val npcMoneyNeutralLines: List<String> = listOf(
        "Ele agradece, indica um atalho e desaparece sem recompensa imediata.",
        "O estranho some no corredor logo apos receber o ouro.",
        "Voce entrega o ouro e segue sem retorno imediato."
    ),
    val npcItemPitch: List<String> = listOf(
        "Ele pede {item} x{qty} para continuar a jornada.",
        "O viajante precisa de {item} x{qty} e oferece ajuda em troca.",
        "Faltam suprimentos para ele. Pedido atual: {item} x{qty}."
    ),
    val npcItemNoItemsLines: List<String> = listOf(
        "Ele pede suprimentos, mas voce so carrega equipamento.",
        "Sem consumiveis ou materiais, voce nao consegue ajudar.",
        "Seu inventario nao tem o que ele precisa."
    ),
    val npcItemRefuseLines: List<String> = listOf(
        "O homem recua e assobia. A emboscada inicia.",
        "A negativa encerra a conversa e ativa uma armadilha.",
        "Ele sorri sem humor, e inimigos aparecem."
    ),
    val npcItemScamLines: List<String> = listOf(
        "O pedido era uma armadilha. Inimigos cercam voce.",
        "Ele pega os itens e chama reforcos de imediato.",
        "Voce entrega os suprimentos e cai em emboscada."
    ),
    val npcItemRewardLines: List<String> = listOf(
        "Ele recebe os itens e retribui com apoio real.",
        "A troca da certo e voce recebe ajuda valiosa.",
        "O viajante honra o acordo e compartilha recursos."
    ),
    val npcItemNeutralLines: List<String> = listOf(
        "Ele recebe os itens, agradece e parte sem olhar para tras.",
        "A troca termina sem bonus extra, mas sem conflito.",
        "Voce ajuda e o forasteiro segue viagem."
    ),
    val npcSuspiciousPitch: List<String> = listOf(
        "O viajante oferece uma rota secreta em troca de confianca.",
        "Ele diz conhecer um atalho, mas pede que voce o siga agora.",
        "Uma proposta arriscada surge: caminho rapido por trilha oculta."
    ),
    val npcSuspiciousRefuseLines: List<String> = listOf(
        "Voce ignora a proposta e continua alerta.",
        "Sem confiar no estranho, voce segue pela rota comum.",
        "Voce recusa o atalho e evita risco imediato."
    ),
    val npcSuspiciousScamLines: List<String> = listOf(
        "A rota era falsa. A emboscada comeca nas sombras.",
        "Ele te desvia para um beco sem saida. Ataque imediato.",
        "O suposto atalho termina em armadilha."
    ),
    val npcSuspiciousRewardLines: List<String> = listOf(
        "A indicacao era real e voce encontra vantagem no caminho.",
        "O atalho funciona e rende uma oportunidade rara.",
        "A aposta compensa com ganho imediato."
    ),
    val liquidIntro: List<String> = listOf(
        "um liquido estranho pulsa dentro de um frasco quebrado.",
        "uma poca brilhante bloqueia parte do corredor.",
        "um vidro antigo libera um liquido de cor incerta."
    ),
    val liquidIgnoreLines: List<String> = listOf(
        "Voce evita o liquido e segue sem tocar em nada.",
        "Sem assumir risco, voce deixa o frasco para tras.",
        "Voce decide que curiosidade nao vale uma armadilha."
    ),
    val chestIntro: List<String> = listOf(
        "um bau antigo repousa junto da parede.",
        "um bau de ferro aparece entre escombros.",
        "uma arca gasta chama sua atencao no canto da sala."
    ),
    val chestIgnoreLines: List<String> = listOf(
        "Voce deixa o bau para tras.",
        "Sem pressa, voce ignora a arca fechada.",
        "Voce segue adiante e nao toca no bau."
    ),
    val chestAmbushLines: List<String> = listOf(
        "A tampa abre e uma emboscada salta de dentro.",
        "Nao era um premio. Era uma armadilha viva.",
        "O bau se deforma e ataca antes da reacao."
    )
)

@Serializable
data class DungeonEventDef(
    val id: String = "default",
    val rules: DungeonEventRulesDef = DungeonEventRulesDef(),
    val texts: DungeonEventTextPoolsDef = DungeonEventTextPoolsDef()
)
