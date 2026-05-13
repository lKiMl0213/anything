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
        "O viajante afirma que precisa de {gold} de ouro para atravessar a região.",
        "Ele pede {gold} de ouro e promete retribuir depois."
    ),
    val npcMoneyRefuseLines: List<String> = listOf(
        "O pedido era uma isca. Você cai em uma emboscada.",
        "O estranho muda de postura. A emboscada começa.",
        "A recusa irrita o forasteiro e o ataque vem das sombras."
    ),
    val npcMoneyNoGoldLines: List<String> = listOf(
        "Você não tem ouro suficiente.",
        "Seu bolso vazio não convence o viajante.",
        "Falta ouro para aceitar o pedido."
    ),
    val npcMoneyScamLines: List<String> = listOf(
        "A doação era apenas uma isca para te atrasar.",
        "Ele pega o ouro e assovia para comparsas escondidos.",
        "Você paga, mas percebe tarde demais que era armadilha."
    ),
    val npcMoneyRewardLines: List<String> = listOf(
        "Ele agradece e compartilha informações úteis.",
        "O viajante cumpre a palavra e ajuda com algo valioso.",
        "A ajuda volta em forma de recompensa inesperada."
    ),
    val npcMoneyNeutralLines: List<String> = listOf(
        "Ele agradece, indica um atalho e desaparece sem recompensa imediata.",
        "O estranho some no corredor logo após receber o ouro.",
        "Você entrega o ouro e segue sem retorno imediato."
    ),
    val npcItemPitch: List<String> = listOf(
        "Ele pede {item} x{qty} para continuar a jornada.",
        "O viajante precisa de {item} x{qty} e oferece ajuda em troca.",
        "Faltam suprimentos para ele. Pedido atual: {item} x{qty}."
    ),
    val npcItemNoItemsLines: List<String> = listOf(
        "Ele pede suprimentos, mas você só carrega equipamento.",
        "Sem consumíveis ou materiais, você não consegue ajudar.",
        "Seu inventário não tem o que ele precisa."
    ),
    val npcItemRefuseLines: List<String> = listOf(
        "O homem recua e assobia. A emboscada inicia.",
        "A negativa encerra a conversa e ativa uma armadilha.",
        "Ele sorri sem humor, e inimigos aparecem."
    ),
    val npcItemScamLines: List<String> = listOf(
        "O pedido era uma armadilha. Inimigos cercam você.",
        "Ele pega os itens e chama reforços de imediato.",
        "Você entrega os suprimentos e cai em emboscada."
    ),
    val npcItemRewardLines: List<String> = listOf(
        "Ele recebe os itens e retribui com apoio real.",
        "A troca da certo e você recebe ajuda valiosa.",
        "O viajante honra o acordo e compartilha recursos."
    ),
    val npcItemNeutralLines: List<String> = listOf(
        "Ele recebe os itens, agradece e parte sem olhar para trás.",
        "A troca termina sem bônus extra, mas sem conflito.",
        "Você ajuda e o forasteiro segue viagem."
    ),
    val npcSuspiciousPitch: List<String> = listOf(
        "O viajante oferece uma rota secreta em troca de confiança.",
        "Ele diz conhecer um atalho, mas pede que você o siga agora.",
        "Uma proposta arriscada surge: caminho rápido por trilha oculta."
    ),
    val npcSuspiciousRefuseLines: List<String> = listOf(
        "Você ignora a proposta e continua alerta.",
        "Sem confiar no estranho, você segue pela rota comum.",
        "Você recusa o atalho e evita risco imediato."
    ),
    val npcSuspiciousScamLines: List<String> = listOf(
        "A rota era falsa. A emboscada começa nas sombras.",
        "Ele te desvia para um beco sem saída. Ataque imediato.",
        "O suposto atalho termina em armadilha."
    ),
    val npcSuspiciousRewardLines: List<String> = listOf(
        "A indicação era real e você encontra vantagem no caminho.",
        "O atalho funciona e rende uma oportunidade rara.",
        "A aposta compensa com ganho imediato."
    ),
    val liquidIntro: List<String> = listOf(
        "um líquido estranho pulsa dentro de um frasco quebrado.",
        "uma poça brilhante bloqueia parte do corredor.",
        "um vidro antigo libera um líquido de cor incerta."
    ),
    val liquidIgnoreLines: List<String> = listOf(
        "Você evita o líquido e segue sem tocar em nada.",
        "Sem assumir risco, você deixa o frasco para trás.",
        "Você decide que curiosidade não vale uma armadilha."
    ),
    val chestIntro: List<String> = listOf(
        "um baú antigo repousa junto da parede.",
        "um baú de ferro aparece entre escombros.",
        "uma arca gasta chama sua atenção no canto da sala."
    ),
    val chestIgnoreLines: List<String> = listOf(
        "Você deixa o baú para trás.",
        "Sem pressa, você ignora a arca fechada.",
        "Você segue adiante e não toca no baú."
    ),
    val chestAmbushLines: List<String> = listOf(
        "A tampa abre e uma emboscada salta de dentro.",
        "Não era um prêmio. Era uma armadilha viva.",
        "O baú se deforma e ataca antes da reação."
    )
)

@Serializable
data class DungeonEventDef(
    val id: String = "default",
    val rules: DungeonEventRulesDef = DungeonEventRulesDef(),
    val texts: DungeonEventTextPoolsDef = DungeonEventTextPoolsDef()
)






