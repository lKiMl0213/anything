package rpg.events

import rpg.model.PlayerState

object EventExecutor {
    fun execute(event: EventDefinition, player: PlayerState, context: EventContext): PlayerState {
        return event.effects.fold(player) { acc, effect ->
            effect.apply(acc, context)
        }
    }
}