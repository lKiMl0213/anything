package rpg.android.audio

enum class SoundEffect(
    val rawName: String
) {
    CLICK("sfx_click"),
    BACK("sfx_back"),
    CONFIRM("sfx_confirm"),
    CANCEL("sfx_cancel"),
    POPUP_OPEN("sfx_popup_open"),
    POPUP_CLOSE("sfx_popup_close"),
    ATTACK_PLAYER("sfx_attack_player"),
    ATTACK_ENEMY("sfx_attack_enemy"),
    HIT("sfx_hit"),
    MISS("sfx_miss"),
    CRITICAL("sfx_critical"),
    HEAL("sfx_heal"),
    POTION("sfx_potion"),
    LEVEL_UP("sfx_level_up"),
    REWARD("sfx_reward"),
    EQUIP("sfx_equip"),
    SELL("sfx_sell"),
    BUY("sfx_buy"),
    CRAFT_START("sfx_craft_start"),
    CRAFT_FINISH("sfx_craft_finish"),
    FISHING("sfx_fishing"),
    MINING("sfx_mining"),
    WOODCUTTING("sfx_woodcutting"),
    HUNTING("sfx_hunting"),
    GATHERING("sfx_gathering"),
    FORGE("sfx_forge"),
    ENCHANT("sfx_enchant"),
    ERROR("sfx_error")
}

