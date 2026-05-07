package rpg.android.tutorial

import rpg.model.GameState

internal class TutorialManager {
    fun startForNewCharacter(state: GameState): GameState {
        return state.copy(
            tutorialCompleted = false,
            tutorialStep = TutorialStep.HUB_OVERVIEW.persistedValue
        )
    }

    fun restart(state: GameState): GameState {
        return state.copy(
            tutorialCompleted = false,
            tutorialStep = TutorialStep.HUB_OVERVIEW.persistedValue
        )
    }

    fun complete(state: GameState): GameState {
        return state.copy(
            tutorialCompleted = true,
            tutorialStep = TutorialStep.FINAL.persistedValue
        )
    }

    fun overlay(state: GameState, context: TutorialScreenContext): TutorialOverlayState? {
        val tutorial = state.toTutorialState()
        if (tutorial.completed) return null
        return when (tutorial.step) {
            TutorialStep.HUB_OVERVIEW -> if (context.isHub) {
                TutorialOverlayState(
                    step = tutorial.step,
                    title = "Tutorial",
                    message = "Essa e sua tela principal. Aqui voce ve personagem, HP, MP, XP, ouro e acessos rapidos.",
                    target = TutorialTarget.HUB_INFO_PANEL,
                    requiresUserAction = false,
                    primaryButtonLabel = "Continuar"
                )
            } else {
                null
            }

            TutorialStep.EXPLORE_BUTTON -> if (context.isHub) {
                TutorialOverlayState(
                    step = tutorial.step,
                    title = "Tutorial",
                    message = "Toque em Explorar para escolher uma area e iniciar combates.",
                    target = TutorialTarget.HUB_EXPLORE_BUTTON,
                    requiresUserAction = true
                )
            } else {
                null
            }

            TutorialStep.EXPLORATION_AREAS -> if (
                context.isExplorationAreas ||
                context.navigation == rpg.navigation.NavigationState.Exploration ||
                context.navigation == rpg.navigation.NavigationState.DungeonSelection
            ) {
                TutorialOverlayState(
                    step = tutorial.step,
                    title = "Tutorial",
                    message = "Aqui ficam as areas disponiveis. Cada area tem inimigos e recompensas diferentes.",
                    target = TutorialTarget.EXPLORATION_AREAS_PANEL,
                    requiresUserAction = false,
                    primaryButtonLabel = "Continuar"
                )
            } else {
                null
            }

            TutorialStep.OPEN_CHARACTER -> TutorialOverlayState(
                step = tutorial.step,
                title = "Tutorial",
                message = "Abra o menu Personagem para ver equipamentos, inventario, atributos e talentos.",
                target = TutorialTarget.BOTTOM_NAV_CHARACTER,
                requiresUserAction = true
            )

            TutorialStep.CHARACTER_ATTR_TALENTS -> if (context.isCharacter) {
                TutorialOverlayState(
                    step = tutorial.step,
                    title = "Tutorial",
                    message = "Aqui voce pode ver e melhorar seus atributos e talentos.",
                    target = TutorialTarget.CHARACTER_ACTION_PANEL,
                    requiresUserAction = false,
                    primaryButtonLabel = "Continuar"
                )
            } else {
                null
            }

            TutorialStep.CHARACTER_EQUIPMENT_INVENTORY -> if (context.isCharacter) {
                TutorialOverlayState(
                    step = tutorial.step,
                    title = "Tutorial",
                    message = "Equipamentos melhoram atributos. O inventario mostra os itens obtidos.",
                    target = TutorialTarget.CHARACTER_EQUIPMENT_PANEL,
                    requiresUserAction = false,
                    primaryButtonLabel = "Continuar"
                )
            } else {
                null
            }

            TutorialStep.OPEN_PRODUCTION -> TutorialOverlayState(
                step = tutorial.step,
                title = "Tutorial",
                message = "Agora abra Producao para coleta, caca, mineracao, pesca e craft.",
                target = TutorialTarget.BOTTOM_NAV_PRODUCTION,
                requiresUserAction = true
            )

            TutorialStep.PRODUCTION_INFO -> if (context.isProduction) {
                TutorialOverlayState(
                    step = tutorial.step,
                    title = "Tutorial",
                    message = "Tarefas de producao levam tempo e evoluem suas habilidades.",
                    target = TutorialTarget.PRODUCTION_PANEL,
                    requiresUserAction = false,
                    primaryButtonLabel = "Continuar"
                )
            } else {
                null
            }

            TutorialStep.OPEN_CITY -> TutorialOverlayState(
                step = tutorial.step,
                title = "Tutorial",
                message = "Na cidade ficam loja, taverna, melhorias e outros servicos.",
                target = TutorialTarget.BOTTOM_NAV_CITY,
                requiresUserAction = true
            )

            TutorialStep.CITY_INFO -> if (context.isCity) {
                TutorialOverlayState(
                    step = tutorial.step,
                    title = "Tutorial",
                    message = "Neste painel voce encontra Taverna, Loja e Melhorias.",
                    target = TutorialTarget.CITY_PANEL,
                    requiresUserAction = false,
                    primaryButtonLabel = "Continuar"
                )
            } else {
                null
            }

            TutorialStep.OPEN_PROGRESSION -> TutorialOverlayState(
                step = tutorial.step,
                title = "Tutorial",
                message = "Agora abra Progresso.",
                target = TutorialTarget.BOTTOM_NAV_PROGRESSION,
                requiresUserAction = true
            )

            TutorialStep.PROGRESSION_INFO -> if (context.isProgression) {
                TutorialOverlayState(
                    step = tutorial.step,
                    title = "Tutorial",
                    message = "Aqui voce pode pegar missoes e completar conquistas.",
                    target = TutorialTarget.PROGRESSION_PANEL,
                    requiresUserAction = false,
                    primaryButtonLabel = "Continuar"
                )
            } else {
                null
            }

            TutorialStep.OPEN_SETTINGS -> {
                if (context.isHub) {
                    TutorialOverlayState(
                        step = tutorial.step,
                        title = "Tutorial",
                        message = "Abra a engrenagem para acessar configuracoes basicas.",
                        target = TutorialTarget.SETTINGS_BUTTON,
                        requiresUserAction = true
                    )
                } else {
                    TutorialOverlayState(
                        step = tutorial.step,
                        title = "Tutorial",
                        message = "Volte para Home/Explorar para abrir as configuracoes.",
                        target = TutorialTarget.BOTTOM_NAV_EXPLORE,
                        requiresUserAction = true
                    )
                }
            }

            TutorialStep.FINAL -> TutorialOverlayState(
                step = tutorial.step,
                title = "Tutorial concluido",
                message = "Pronto! Explore, evolua, produza itens e complete quests para fortalecer seu personagem.",
                target = TutorialTarget.NONE,
                requiresUserAction = false,
                primaryButtonLabel = "Comecar"
            )
        }
    }

    fun onContinue(state: GameState, context: TutorialScreenContext): GameState? {
        val tutorial = state.toTutorialState()
        if (tutorial.completed) return null
        val nextStep = when (tutorial.step) {
            TutorialStep.HUB_OVERVIEW -> if (context.isHub) TutorialStep.EXPLORE_BUTTON else null
            TutorialStep.EXPLORATION_AREAS -> if (
                context.isExplorationAreas ||
                context.navigation == rpg.navigation.NavigationState.Exploration ||
                context.navigation == rpg.navigation.NavigationState.DungeonSelection
            ) {
                TutorialStep.OPEN_CHARACTER
            } else {
                null
            }
            TutorialStep.CHARACTER_ATTR_TALENTS -> if (context.isCharacter) TutorialStep.CHARACTER_EQUIPMENT_INVENTORY else null
            TutorialStep.CHARACTER_EQUIPMENT_INVENTORY -> if (context.isCharacter) TutorialStep.OPEN_PRODUCTION else null
            TutorialStep.PRODUCTION_INFO -> if (context.isProduction) TutorialStep.OPEN_CITY else null
            TutorialStep.CITY_INFO -> if (context.isCity) TutorialStep.OPEN_PROGRESSION else null
            TutorialStep.PROGRESSION_INFO -> if (context.isProgression) TutorialStep.OPEN_SETTINGS else null
            TutorialStep.FINAL -> null
            else -> null
        } ?: return null
        return state.copy(tutorialStep = nextStep.persistedValue)
    }

    fun onAction(
        state: GameState,
        context: TutorialScreenContext,
        action: TutorialAction
    ): TutorialActionDecision {
        val tutorial = state.toTutorialState()
        if (tutorial.completed) {
            return TutorialActionDecision(allowed = true)
        }
        return when (tutorial.step) {
            TutorialStep.HUB_OVERVIEW -> TutorialActionDecision(allowed = false)
            TutorialStep.EXPLORE_BUTTON -> {
                if (action == TutorialAction.OPEN_EXPLORE && context.isHub) {
                    TutorialActionDecision(allowed = true, nextStep = TutorialStep.EXPLORATION_AREAS)
                } else {
                    TutorialActionDecision(allowed = false)
                }
            }

            TutorialStep.EXPLORATION_AREAS -> {
                TutorialActionDecision(allowed = false)
            }

            TutorialStep.OPEN_CHARACTER -> {
                if (action == TutorialAction.OPEN_CHARACTER) {
                    TutorialActionDecision(allowed = true, nextStep = TutorialStep.CHARACTER_ATTR_TALENTS)
                } else {
                    TutorialActionDecision(allowed = false)
                }
            }

            TutorialStep.CHARACTER_ATTR_TALENTS,
            TutorialStep.CHARACTER_EQUIPMENT_INVENTORY,
            TutorialStep.PRODUCTION_INFO,
            TutorialStep.CITY_INFO,
            TutorialStep.PROGRESSION_INFO,
            TutorialStep.FINAL -> TutorialActionDecision(allowed = false)

            TutorialStep.OPEN_PRODUCTION -> {
                if (action == TutorialAction.OPEN_PRODUCTION) {
                    TutorialActionDecision(allowed = true, nextStep = TutorialStep.PRODUCTION_INFO)
                } else {
                    TutorialActionDecision(allowed = false)
                }
            }

            TutorialStep.OPEN_CITY -> {
                if (action == TutorialAction.OPEN_CITY) {
                    TutorialActionDecision(allowed = true, nextStep = TutorialStep.CITY_INFO)
                } else {
                    TutorialActionDecision(allowed = false)
                }
            }

            TutorialStep.OPEN_PROGRESSION -> {
                if (action == TutorialAction.OPEN_PROGRESSION) {
                    TutorialActionDecision(allowed = true, nextStep = TutorialStep.PROGRESSION_INFO)
                } else {
                    TutorialActionDecision(allowed = false)
                }
            }

            TutorialStep.OPEN_SETTINGS -> {
                if (context.isHub && action == TutorialAction.OPEN_SETTINGS) {
                    TutorialActionDecision(allowed = true, nextStep = TutorialStep.FINAL)
                } else if (!context.isHub && action == TutorialAction.OPEN_HUB) {
                    TutorialActionDecision(allowed = true)
                } else {
                    TutorialActionDecision(allowed = false)
                }
            }
        }
    }

    fun applyDecision(state: GameState, decision: TutorialActionDecision): GameState {
        val next = decision.nextStep ?: return state
        return state.copy(tutorialStep = next.persistedValue)
    }
}
