package rpg.cli.combat

import rpg.engine.GameEngine

internal class DungeonCombatRenderer(
    private val engine: GameEngine,
    private val format: (Double) -> String,
    private val fixedContextLines: List<String>,
    private val ansiCombatReset: String,
    private val ansiCombatHeader: String,
    private val ansiCombatPlayer: String,
    private val ansiCombatEnemy: String,
    private val ansiCombatLoading: String,
    private val ansiCombatReady: String,
    private val ansiCombatBlocked: String,
    private val ansiCombatCasting: String,
    private val ansiCombatPause: String,
    private val ansiClearLine: String,
    private val ansiClearToEnd: String
) {
    private var lastRenderEpochMs: Long = 0L
    private var lastSignature: String = ""
    private val combatHistory: ArrayDeque<String> = ArrayDeque()
    private val combatHistoryLimit = 6
    private val combatLogHeight = 6
    private val combatMenuHeight = 12
    private val combatDynamicBlockHeight = 2 + 1 + 2 + 1 + 1 + 1 + combatLogHeight + 1 + combatMenuHeight
    private var fixedAreaRendered: Boolean = false
    private var fixedAreaHeight: Int = 0
    private var combatDynamicBlockInitialized: Boolean = false
    private var transientLinesBelowBlock: Int = 0

    fun onFrame(
        snapshot: rpg.combat.CombatSnapshot,
        buildDecisionSectionLines: (rpg.combat.CombatSnapshot) -> List<String>
    ) {
        val now = System.currentTimeMillis()
        val signature = combatFrameSignature(snapshot)
        val unchanged = signature == lastSignature
        val minIntervalMs = if (snapshot.pausedForDecision) 0L else 140L

        if (snapshot.pausedForDecision && unchanged) return
        if (unchanged && now - lastRenderEpochMs < minIntervalMs) return
        if (now - lastRenderEpochMs < minIntervalMs) return

        renderCombatFrameTracked(snapshot, buildDecisionSectionLines, signature, now)
    }

    fun renderNow(
        snapshot: rpg.combat.CombatSnapshot,
        buildDecisionSectionLines: (rpg.combat.CombatSnapshot) -> List<String>
    ) {
        renderCombatFrameTracked(snapshot, buildDecisionSectionLines)
    }

    fun onInputCaptured() {
        transientLinesBelowBlock = 1
    }

    fun appendCombatHistory(rawMessage: String) {
        val message = rawMessage.trimEnd()
        if (message.isBlank()) return
        combatHistory.addLast(message)
        while (combatHistory.size > combatHistoryLimit) {
            combatHistory.removeFirst()
        }
    }

    fun finalizeDisplay() {
        if (!combatDynamicBlockInitialized) {
            clearCombatHistory()
            return
        }
        val totalHeight = fixedAreaHeight + combatDynamicBlockHeight
        moveCursorUp(totalHeight + transientLinesBelowBlock)
        print(ansiClearToEnd)
        System.out.flush()
        fixedAreaRendered = false
        fixedAreaHeight = 0
        combatDynamicBlockInitialized = false
        transientLinesBelowBlock = 0
        clearCombatHistory()
    }

    private fun clearCombatHistory() {
        combatHistory.clear()
    }

    private fun renderCombatFrameTracked(
        snapshot: rpg.combat.CombatSnapshot,
        buildDecisionSectionLines: (rpg.combat.CombatSnapshot) -> List<String>,
        signature: String = combatFrameSignature(snapshot),
        now: Long = System.currentTimeMillis()
    ) {
        lastRenderEpochMs = now
        lastSignature = signature
        renderCombatFrame(snapshot, buildDecisionSectionLines)
    }

    private fun renderCombatFrame(
        snapshot: rpg.combat.CombatSnapshot,
        buildDecisionSectionLines: (rpg.combat.CombatSnapshot) -> List<String>
    ) {
        if (!fixedAreaRendered) {
            renderFixedArea(snapshot)
        }
        redrawDynamicBlock(buildDynamicLines(snapshot, buildDecisionSectionLines))
    }

    private fun renderFixedArea(snapshot: rpg.combat.CombatSnapshot) {
        val enemyName = engine.monsterDisplayName(snapshot.monster)
        val lines = mutableListOf<String>()
        lines += fixedContextLines.filter { it.isNotBlank() }
        lines += combatColor("Combate | $enemyName", ansiCombatHeader)

        for (line in lines) {
            print('\r')
            print(ansiClearLine)
            print(line)
            print('\n')
        }
        System.out.flush()
        fixedAreaHeight = lines.size
        fixedAreaRendered = true
    }

    private fun buildDynamicLines(
        snapshot: rpg.combat.CombatSnapshot,
        buildDecisionSectionLines: (rpg.combat.CombatSnapshot) -> List<String>
    ): List<String> {
        val playerBar = combatActionBar(snapshot.playerRuntime)
        val monsterBar = combatActionBar(snapshot.monsterRuntime)
        val playerState = combatStateLabel(snapshot.playerRuntime.state)
        val monsterState = combatStateLabel(snapshot.monsterRuntime.state)
        val playerHp = "${format(snapshot.player.currentHp)} / ${format(snapshot.playerStats.derived.hpMax)}"
        val playerMp = "${format(snapshot.player.currentMp)} / ${format(snapshot.playerStats.derived.mpMax)}"
        val monsterHp = "${format(snapshot.monsterHp)} / ${format(snapshot.monsterStats.derived.hpMax)}"
        val lineOne = buildString {
            append("Voce    ")
            append(playerBar)
            append(" ")
            append(combatColor(playerState, combatStateColor(snapshot.playerRuntime.state)))
        }
        val lineTwo = buildString {
            append("Inimigo ")
            append(monsterBar)
            append(" ")
            append(combatColor(monsterState, combatStateColor(snapshot.monsterRuntime.state)))
        }
        val lines = mutableListOf<String>()
        lines += "Voce: HP ${combatColor(playerHp, ansiCombatPlayer)} | MP ${combatColor(playerMp, ansiCombatCasting)}"
        lines += "Inimigo: HP ${combatColor(monsterHp, ansiCombatEnemy)}"
        lines += ""
        lines += lineOne
        lines += lineTwo
        lines += ""
        lines += if (snapshot.pausedForDecision) {
            combatColor("Aguardando sua acao.", ansiCombatPause)
        } else {
            ""
        }
        lines += "Historico de batalha:"
        val recentLog = combatHistory.toList().takeLast(combatLogHeight)
        repeat(combatLogHeight) { index ->
            val line = recentLog.getOrNull(index).orEmpty()
            lines += if (line.isBlank()) "" else "- $line"
        }
        lines += ""
        lines += normalizeFixedWindow(buildDecisionSectionLines(snapshot), combatMenuHeight)
        return normalizeFixedWindow(lines, combatDynamicBlockHeight)
    }

    private fun normalizeFixedWindow(lines: List<String>, height: Int): List<String> {
        if (height <= 0) return emptyList()
        val normalized = lines.toMutableList()
        if (normalized.size > height) {
            val keep = (height - 1).coerceAtLeast(1)
            val hidden = (normalized.size - keep).coerceAtLeast(0)
            val trimmed = normalized.take(keep).toMutableList()
            trimmed += "... ($hidden linha(s) ocultas)"
            return trimmed
        }
        while (normalized.size < height) {
            normalized += ""
        }
        return normalized
    }

    private fun redrawDynamicBlock(rawLines: List<String>) {
        val lines = if (rawLines.size == combatDynamicBlockHeight) {
            rawLines
        } else {
            normalizeFixedWindow(rawLines, combatDynamicBlockHeight)
        }
        if (combatDynamicBlockInitialized) {
            moveCursorUp(combatDynamicBlockHeight + transientLinesBelowBlock)
        }
        for (line in lines) {
            print('\r')
            print(ansiClearLine)
            print(line)
            print('\n')
        }
        print('\r')
        print(ansiClearLine)
        System.out.flush()
        combatDynamicBlockInitialized = true
        transientLinesBelowBlock = 0
    }

    private fun moveCursorUp(lines: Int) {
        if (lines <= 0) return
        print("\u001B[${lines}A")
    }

    private fun combatFrameSignature(snapshot: rpg.combat.CombatSnapshot): String {
        val playerPct = ((snapshot.playerRuntime.actionBar / snapshot.playerRuntime.actionThreshold) * 100.0).toInt()
        val monsterPct = ((snapshot.monsterRuntime.actionBar / snapshot.monsterRuntime.actionThreshold) * 100.0).toInt()
        val playerCast = snapshot.playerRuntime.castRemaining.toInt()
        val monsterCast = snapshot.monsterRuntime.castRemaining.toInt()
        val playerGcd = snapshot.playerRuntime.gcdRemaining.toInt()
        val monsterGcd = snapshot.monsterRuntime.gcdRemaining.toInt()
        return listOf(
            format(snapshot.player.currentHp),
            format(snapshot.player.currentMp),
            format(snapshot.monsterHp),
            playerPct.toString(),
            monsterPct.toString(),
            snapshot.playerRuntime.state.name,
            snapshot.monsterRuntime.state.name,
            playerCast.toString(),
            monsterCast.toString(),
            playerGcd.toString(),
            monsterGcd.toString(),
            snapshot.pausedForDecision.toString()
        ).joinToString("|")
    }

    private fun combatActionBar(runtime: rpg.combat.CombatRuntimeState, width: Int = 18): String {
        val threshold = runtime.actionThreshold.coerceAtLeast(1.0)
        val pct = ((runtime.actionBar / threshold) * 100.0).coerceIn(0.0, 100.0)
        val filled = ((pct / 100.0) * width).toInt().coerceIn(0, width)
        val bar = "#".repeat(filled) + "-".repeat(width - filled)
        val suffix = if (runtime.state == rpg.combat.CombatState.READY) "PRONTO" else "${pct.toInt()}%"
        return combatColor("[$bar] $suffix", combatStateColor(runtime.state))
    }

    private fun combatStateLabel(state: rpg.combat.CombatState): String = when (state) {
        rpg.combat.CombatState.IDLE -> "Carregando"
        rpg.combat.CombatState.READY -> "Pronto"
        rpg.combat.CombatState.CASTING -> "Castando"
        rpg.combat.CombatState.STUNNED -> "Atordoado"
        rpg.combat.CombatState.DEAD -> "Morto"
    }

    private fun combatStateColor(state: rpg.combat.CombatState): String = when (state) {
        rpg.combat.CombatState.READY -> ansiCombatReady
        rpg.combat.CombatState.CASTING -> ansiCombatCasting
        rpg.combat.CombatState.STUNNED, rpg.combat.CombatState.DEAD -> ansiCombatBlocked
        else -> ansiCombatLoading
    }

    private fun combatColor(text: String, colorCode: String): String = "$colorCode$text$ansiCombatReset"
}
