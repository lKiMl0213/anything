function Ensure-Dir {
    param([string]$Path)
    if (![string]::IsNullOrWhiteSpace($Path)) {
        New-Item -ItemType Directory -Force -Path $Path | Out-Null
    }
}

function Move-IfExists {
    param([string]$Source, [string]$Target)
    if (!(Test-Path -LiteralPath $Source)) {
        return
    }
    Ensure-Dir (Split-Path -Parent $Target)
    Move-Item -LiteralPath $Source -Destination $Target -Force
}

function Move-SetPieces {
    param(
        [string]$SourcePrefix,
        [string]$TargetBase
    )
    $slotMap = @{
        helm = "head"
        chest = "chest"
        gloves = "gloves"
        legs = "legs"
        boots = "boots"
    }
    foreach ($slot in $slotMap.Keys) {
        $source = "data/item_templates/${SourcePrefix}_${slot}_template.json"
        $target = "data/item_templates/$TargetBase/$($slotMap[$slot])/${SourcePrefix}_${slot}_template.json"
        Move-IfExists $source $target
    }
}

function Move-ClassRewardWeapons {
    param(
        [string]$SourcePrefix,
        [string]$TargetBase,
        [switch]$HasQuiver
    )
    Move-IfExists "data/items/${SourcePrefix}_weapon.json" "data/items/$TargetBase/weapon/${SourcePrefix}_weapon.json"
    if ($HasQuiver) {
        Move-IfExists "data/items/${SourcePrefix}_quiver.json" "data/items/$TargetBase/quiver/${SourcePrefix}_quiver.json"
    }
}

$directMoves = @(
    @{ s = "data/classes/archer.json"; t = "data/classes/archer/archer.json" },
    @{ s = "data/classes/mage.json"; t = "data/classes/mage/mage.json" },
    @{ s = "data/classes/swordman.json"; t = "data/classes/warrior/swordman.json" },

    @{ s = "data/subclasses/hunter.json"; t = "data/subclasses/archer/hunter.json" },
    @{ s = "data/subclasses/ranger.json"; t = "data/subclasses/archer/ranger.json" },
    @{ s = "data/subclasses/arcanist.json"; t = "data/subclasses/mage/arcanist.json" },
    @{ s = "data/subclasses/elementalist.json"; t = "data/subclasses/mage/elementalist.json" },
    @{ s = "data/subclasses/warrior.json"; t = "data/subclasses/warrior/warrior.json" },
    @{ s = "data/subclasses/barbarian.json"; t = "data/subclasses/warrior/barbarian.json" },

    @{ s = "data/specializations/assassin.json"; t = "data/specializations/archer/hunter/assassin.json" },
    @{ s = "data/specializations/bounty_hunter.json"; t = "data/specializations/archer/hunter/bounty_hunter.json" },
    @{ s = "data/specializations/sharpshooter.json"; t = "data/specializations/archer/ranger/sharpshooter.json" },
    @{ s = "data/specializations/shadow_hunter.json"; t = "data/specializations/archer/ranger/shadow_hunter.json" },
    @{ s = "data/specializations/archmage.json"; t = "data/specializations/mage/arcanist/archmage.json" },
    @{ s = "data/specializations/cleric.json"; t = "data/specializations/mage/arcanist/cleric.json" },
    @{ s = "data/specializations/pyromancer.json"; t = "data/specializations/mage/elementalist/pyromancer.json" },
    @{ s = "data/specializations/elemental_master.json"; t = "data/specializations/mage/elementalist/elemental_master.json" },
    @{ s = "data/specializations/paladin.json"; t = "data/specializations/warrior/warrior/paladin.json" },
    @{ s = "data/specializations/elite_guard.json"; t = "data/specializations/warrior/warrior/elite_guard.json" },
    @{ s = "data/specializations/predator.json"; t = "data/specializations/warrior/barbarian/predator.json" },
    @{ s = "data/specializations/berserker.json"; t = "data/specializations/warrior/barbarian/berserker.json" },

    @{ s = "data/talent_trees/Archer_base.json"; t = "data/talent_trees/archer/base/Archer_base.json" },
    @{ s = "data/talent_trees/A_hunter.json"; t = "data/talent_trees/archer/hunter/A_hunter.json" },
    @{ s = "data/talent_trees/A_hunter_assassin.json"; t = "data/talent_trees/archer/hunter/assassin/A_hunter_assassin.json" },
    @{ s = "data/talent_trees/A_hunter_bounty_hunter.json"; t = "data/talent_trees/archer/hunter/bounty_hunter/A_hunter_bounty_hunter.json" },
    @{ s = "data/talent_trees/A_ranger.json"; t = "data/talent_trees/archer/ranger/A_ranger.json" },
    @{ s = "data/talent_trees/A_ranger_sharpshooter.json"; t = "data/talent_trees/archer/ranger/sharpshooter/A_ranger_sharpshooter.json" },
    @{ s = "data/talent_trees/A_ranger_shadow_hunter.json"; t = "data/talent_trees/archer/ranger/shadow_hunter/A_ranger_shadow_hunter.json" },
    @{ s = "data/talent_trees/Mage_base.json"; t = "data/talent_trees/mage/base/Mage_base.json" },
    @{ s = "data/talent_trees/M_arcanist.json"; t = "data/talent_trees/mage/arcanist/M_arcanist.json" },
    @{ s = "data/talent_trees/M_arcanist_archmage.json"; t = "data/talent_trees/mage/arcanist/archmage/M_arcanist_archmage.json" },
    @{ s = "data/talent_trees/M_arcanist_cleric.json"; t = "data/talent_trees/mage/arcanist/cleric/M_arcanist_cleric.json" },
    @{ s = "data/talent_trees/M_elementalist.json"; t = "data/talent_trees/mage/elementalist/M_elementalist.json" },
    @{ s = "data/talent_trees/M_elementalist_pyromancer.json"; t = "data/talent_trees/mage/elementalist/pyromancer/M_elementalist_pyromancer.json" },
    @{ s = "data/talent_trees/M_elementalist_elemental_master.json"; t = "data/talent_trees/mage/elementalist/elemental_master/M_elementalist_elemental_master.json" },
    @{ s = "data/talent_trees/Swordman_base.json"; t = "data/talent_trees/warrior/base/Swordman_base.json" },
    @{ s = "data/talent_trees/S_warrior.json"; t = "data/talent_trees/warrior/warrior/S_warrior.json" },
    @{ s = "data/talent_trees/S_warrior_paladin.json"; t = "data/talent_trees/warrior/warrior/paladin/S_warrior_paladin.json" },
    @{ s = "data/talent_trees/S_warrior_elite_guard.json"; t = "data/talent_trees/warrior/warrior/elite_guard/S_warrior_elite_guard.json" },
    @{ s = "data/talent_trees/S_barbarian.json"; t = "data/talent_trees/warrior/barbarian/S_barbarian.json" },
    @{ s = "data/talent_trees/S_barbarian_predator.json"; t = "data/talent_trees/warrior/barbarian/predator/S_barbarian_predator.json" },
    @{ s = "data/talent_trees/S_barbarian_berserker.json"; t = "data/talent_trees/warrior/barbarian/berserker/S_barbarian_berserker.json" },

    @{ s = "data/quest_templates/unlock_archer_subclass_lv25.json"; t = "data/quest_templates/progression/archer/unlock_archer_subclass_lv25.json" },
    @{ s = "data/quest_templates/unlock_archer_specialization_lv50.json"; t = "data/quest_templates/progression/archer/unlock_archer_specialization_lv50.json" },
    @{ s = "data/quest_templates/unlock_mage_subclass_lv25.json"; t = "data/quest_templates/progression/mage/unlock_mage_subclass_lv25.json" },
    @{ s = "data/quest_templates/unlock_mage_specialization_lv50.json"; t = "data/quest_templates/progression/mage/unlock_mage_specialization_lv50.json" },
    @{ s = "data/quest_templates/unlock_swordman_subclass_lv25.json"; t = "data/quest_templates/progression/warrior/unlock_swordman_subclass_lv25.json" },
    @{ s = "data/quest_templates/unlock_swordman_specialization_lv50.json"; t = "data/quest_templates/progression/warrior/unlock_swordman_specialization_lv50.json" },
    @{ s = "data/quest_templates/kill_tag_undead.json"; t = "data/quest_templates/board/kill/kill_tag_undead.json" },
    @{ s = "data/quest_templates/kill_tag_elite.json"; t = "data/quest_templates/board/kill/kill_tag_elite.json" },
    @{ s = "data/quest_templates/kill_tag_beast.json"; t = "data/quest_templates/board/kill/kill_tag_beast.json" },
    @{ s = "data/quest_templates/kill_monster_any.json"; t = "data/quest_templates/board/kill/kill_monster_any.json" },
    @{ s = "data/quest_templates/gather_woodcutting_focus.json"; t = "data/quest_templates/board/gather/gather_woodcutting_focus.json" },
    @{ s = "data/quest_templates/gather_resource_any.json"; t = "data/quest_templates/board/gather/gather_resource_any.json" },
    @{ s = "data/quest_templates/gather_mining_focus.json"; t = "data/quest_templates/board/gather/gather_mining_focus.json" },
    @{ s = "data/quest_templates/gather_herbalism_focus.json"; t = "data/quest_templates/board/gather/gather_herbalism_focus.json" },
    @{ s = "data/quest_templates/gather_fishing_focus.json"; t = "data/quest_templates/board/gather/gather_fishing_focus.json" },
    @{ s = "data/quest_templates/craft_item_any.json"; t = "data/quest_templates/board/craft/craft_item_any.json" },
    @{ s = "data/quest_templates/complete_runs.json"; t = "data/quest_templates/board/run/complete_runs.json" },
    @{ s = "data/quest_templates/reach_floor.json"; t = "data/quest_templates/board/run/reach_floor.json" },
    @{ s = "data/quest_templates/collect_item_stockpile.json"; t = "data/quest_templates/board/collect/collect_item_stockpile.json" },
    @{ s = "data/quest_templates/collect_item_delivery.json"; t = "data/quest_templates/board/collect/collect_item_delivery.json" },

    @{ s = "data/drop_tables/beast_low.json"; t = "data/drop_tables/beast/beast_low.json" },
    @{ s = "data/drop_tables/wolf_beast.json"; t = "data/drop_tables/beast/wolf_beast.json" },
    @{ s = "data/drop_tables/city_cutpurse.json"; t = "data/drop_tables/urban/city_cutpurse.json" },
    @{ s = "data/drop_tables/plage_common.json"; t = "data/drop_tables/urban/plage_common.json" },
    @{ s = "data/drop_tables/bandit_shadow.json"; t = "data/drop_tables/urban/bandit_shadow.json" },
    @{ s = "data/drop_tables/construct_arcane.json"; t = "data/drop_tables/arcane/construct_arcane.json" },
    @{ s = "data/drop_tables/slime_core.json"; t = "data/drop_tables/arcane/slime_core.json" },
    @{ s = "data/drop_tables/elemental_fire.json"; t = "data/drop_tables/elemental/elemental_fire.json" },
    @{ s = "data/drop_tables/undead_low.json"; t = "data/drop_tables/undead/undead_low.json" },
    @{ s = "data/drop_tables/undead_high.json"; t = "data/drop_tables/undead/undead_high.json" },
    @{ s = "data/drop_tables/undead_ancient.json"; t = "data/drop_tables/undead/undead_ancient.json" },
    @{ s = "data/drop_tables/undead_relics.json"; t = "data/drop_tables/undead/undead_relics.json" },
    @{ s = "data/drop_tables/war_scraps.json"; t = "data/drop_tables/war/war_scraps.json" },

    @{ s = "data/shop/cash_entry_archer_bow_lv5.json"; t = "data/shop/cash/archer/cash_entry_archer_bow_lv5.json" },
    @{ s = "data/shop/cash_entry_archer_bow_lv10.json"; t = "data/shop/cash/archer/cash_entry_archer_bow_lv10.json" },
    @{ s = "data/shop/cash_entry_archer_bow_lv50.json"; t = "data/shop/cash/archer/cash_entry_archer_bow_lv50.json" },
    @{ s = "data/shop/cash_entry_archer_bow_lv100.json"; t = "data/shop/cash/archer/cash_entry_archer_bow_lv100.json" },
    @{ s = "data/shop/cash_entry_archer_quiver_lv10.json"; t = "data/shop/cash/archer/cash_entry_archer_quiver_lv10.json" },
    @{ s = "data/shop/cash_entry_mage_staff_lv5.json"; t = "data/shop/cash/mage/cash_entry_mage_staff_lv5.json" },
    @{ s = "data/shop/cash_entry_mage_staff_lv10.json"; t = "data/shop/cash/mage/cash_entry_mage_staff_lv10.json" },
    @{ s = "data/shop/cash_entry_mage_staff_lv50.json"; t = "data/shop/cash/mage/cash_entry_mage_staff_lv50.json" },
    @{ s = "data/shop/cash_entry_mage_staff_lv100.json"; t = "data/shop/cash/mage/cash_entry_mage_staff_lv100.json" },
    @{ s = "data/shop/cash_entry_mage_cloak_lv10.json"; t = "data/shop/cash/mage/cash_entry_mage_cloak_lv10.json" },
    @{ s = "data/shop/cash_entry_bag_small.json"; t = "data/shop/cash/general/cash_entry_bag_small.json" },
    @{ s = "data/shop/cash_entry_bag_medium.json"; t = "data/shop/cash/general/cash_entry_bag_medium.json" },
    @{ s = "data/shop/cash_entry_bag_large.json"; t = "data/shop/cash/general/cash_entry_bag_large.json" },
    @{ s = "data/shop/cash_entry_full_restore.json"; t = "data/shop/cash/general/cash_entry_full_restore.json" },
    @{ s = "data/shop/cash_entry_immunity.json"; t = "data/shop/cash/general/cash_entry_immunity.json" },
    @{ s = "data/shop/cash_entry_room_buff_10.json"; t = "data/shop/cash/general/cash_entry_room_buff_10.json" },
    @{ s = "data/shop/cash_entry_room_buff_25.json"; t = "data/shop/cash/general/cash_entry_room_buff_25.json" },
    @{ s = "data/shop/cash_entry_room_buff_50.json"; t = "data/shop/cash/general/cash_entry_room_buff_50.json" },
    @{ s = "data/shop/cash_entry_run_buff_10.json"; t = "data/shop/cash/general/cash_entry_run_buff_10.json" },
    @{ s = "data/shop/cash_entry_run_buff_25.json"; t = "data/shop/cash/general/cash_entry_run_buff_25.json" },
    @{ s = "data/shop/cash_entry_run_buff_50.json"; t = "data/shop/cash/general/cash_entry_run_buff_50.json" },
    @{ s = "data/shop/cash_entry_weapon_lv5.json"; t = "data/shop/cash/general/cash_entry_weapon_lv5.json" },
    @{ s = "data/shop/cash_entry_weapon_lv10.json"; t = "data/shop/cash/general/cash_entry_weapon_lv10.json" },
    @{ s = "data/shop/cash_entry_weapon_lv50.json"; t = "data/shop/cash/general/cash_entry_weapon_lv50.json" },
    @{ s = "data/shop/cash_entry_weapon_lv100.json"; t = "data/shop/cash/general/cash_entry_weapon_lv100.json" },
    @{ s = "data/shop/shop_arrow_simple_bundle.json"; t = "data/shop/gold/ammo/shop_arrow_simple_bundle.json" },
    @{ s = "data/shop/shop_arrow_iron_bundle.json"; t = "data/shop/gold/ammo/shop_arrow_iron_bundle.json" },
    @{ s = "data/shop/shop_arrow_steel_bundle.json"; t = "data/shop/gold/ammo/shop_arrow_steel_bundle.json" },
    @{ s = "data/shop/shop_potion_fixed.json"; t = "data/shop/gold/consumables/shop_potion_fixed.json" },
    @{ s = "data/shop/shop_ashwood_bow.json"; t = "data/shop/gold/archer/shop_ashwood_bow.json" },
    @{ s = "data/shop/shop_simple_bow.json"; t = "data/shop/gold/archer/shop_simple_bow.json" },
    @{ s = "data/shop/shop_iron_longbow.json"; t = "data/shop/gold/archer/shop_iron_longbow.json" },
    @{ s = "data/shop/shop_sunstone_recurve.json"; t = "data/shop/gold/archer/shop_sunstone_recurve.json" },
    @{ s = "data/shop/shop_field_quiver.json"; t = "data/shop/gold/archer/shop_field_quiver.json" },
    @{ s = "data/shop/shop_hunter_quiver.json"; t = "data/shop/gold/archer/shop_hunter_quiver.json" },
    @{ s = "data/shop/shop_leather_hood.json"; t = "data/shop/gold/archer/shop_leather_hood.json" },
    @{ s = "data/shop/shop_scout_tunic.json"; t = "data/shop/gold/archer/shop_scout_tunic.json" },
    @{ s = "data/shop/shop_trailwrap_leggings.json"; t = "data/shop/gold/archer/shop_trailwrap_leggings.json" },
    @{ s = "data/shop/shop_softstep_boots.json"; t = "data/shop/gold/archer/shop_softstep_boots.json" },
    @{ s = "data/shop/shop_falcon_gloves.json"; t = "data/shop/gold/archer/shop_falcon_gloves.json" },
    @{ s = "data/shop/shop_wayfarer_cloak.json"; t = "data/shop/gold/archer/shop_wayfarer_cloak.json" },
    @{ s = "data/shop/shop_amethyst_rod.json"; t = "data/shop/gold/mage/shop_amethyst_rod.json" },
    @{ s = "data/shop/shop_basic_scepter.json"; t = "data/shop/gold/mage/shop_basic_scepter.json" },
    @{ s = "data/shop/shop_oak_staff.json"; t = "data/shop/gold/mage/shop_oak_staff.json" },
    @{ s = "data/shop/shop_ironbound_staff.json"; t = "data/shop/gold/mage/shop_ironbound_staff.json" },
    @{ s = "data/shop/shop_linen_hat.json"; t = "data/shop/gold/mage/shop_linen_hat.json" },
    @{ s = "data/shop/shop_spellweave_robe.json"; t = "data/shop/gold/mage/shop_spellweave_robe.json" },
    @{ s = "data/shop/shop_mystic_slacks.json"; t = "data/shop/gold/mage/shop_mystic_slacks.json" },
    @{ s = "data/shop/shop_mistwalk_sandals.json"; t = "data/shop/gold/mage/shop_mistwalk_sandals.json" },
    @{ s = "data/shop/shop_channeler_gloves.json"; t = "data/shop/gold/mage/shop_channeler_gloves.json" },
    @{ s = "data/shop/shop_sage_cloak.json"; t = "data/shop/gold/mage/shop_sage_cloak.json" },
    @{ s = "data/shop/shop_rusty_sword.json"; t = "data/shop/gold/warrior/shop_rusty_sword.json" },
    @{ s = "data/shop/shop_wooden_buckler.json"; t = "data/shop/gold/warrior/shop_wooden_buckler.json" }
)

foreach ($move in $directMoves) {
    Move-IfExists $move.s $move.t
}

$archerBaseTemplates = @{
    weapon = @("ashwood_bow", "bone_bow", "iron_longbow", "sunstone_recurve", "cash_archer_bow_lv5", "cash_archer_bow_lv10", "cash_archer_bow_lv50", "cash_archer_bow_lv100")
    quiver = @("field_quiver", "hunter_quiver", "cash_archer_quiver_lv10")
    head   = @("leather_hood")
    chest  = @("scout_tunic")
    legs   = @("trailwrap_leggings")
    boots  = @("softstep_boots")
    gloves = @("falcon_gloves")
    cape   = @("wayfarer_cloak")
}

$mageBaseTemplates = @{
    weapon = @("apprentice_staff", "oak_staff", "ironbound_staff", "amethyst_rod", "cash_mage_staff_lv5", "cash_mage_staff_lv10", "cash_mage_staff_lv50", "cash_mage_staff_lv100")
    head   = @("linen_hat")
    chest  = @("spellweave_robe")
    legs   = @("mystic_slacks")
    boots  = @("mistwalk_sandals")
    gloves = @("channeler_gloves")
    cape   = @("sage_cloak", "cash_mage_cloak_lv10")
}

$warriorBaseTemplates = @{
    weapon  = @("iron_sword")
    offhand = @("wooden_shield", "bone_shield")
    chest   = @("leather_armor")
}

foreach ($slot in $archerBaseTemplates.Keys) {
    foreach ($name in $archerBaseTemplates[$slot]) {
        Move-IfExists "data/item_templates/$name.json" "data/item_templates/archer/base/$slot/$name.json"
    }
}
foreach ($slot in $mageBaseTemplates.Keys) {
    foreach ($name in $mageBaseTemplates[$slot]) {
        Move-IfExists "data/item_templates/$name.json" "data/item_templates/mage/base/$slot/$name.json"
    }
}
foreach ($slot in $warriorBaseTemplates.Keys) {
    foreach ($name in $warriorBaseTemplates[$slot]) {
        Move-IfExists "data/item_templates/$name.json" "data/item_templates/warrior/base/$slot/$name.json"
    }
}

$lineTemplates = @(
    @{ prefix = "a_hunter"; base = "archer/hunter"; weapon = "hunter_trackers_bow"; quiver = "hunter_field_quiver" },
    @{ prefix = "a_hunter_bounty_hunter"; base = "archer/hunter/bounty_hunter"; weapon = "bounty_writ_bow" },
    @{ prefix = "a_hunter_assassin"; base = "archer/hunter/assassin"; weapon = "assassin_fang_bow" },
    @{ prefix = "a_ranger"; base = "archer/ranger"; weapon = "ranger_longwatch_bow"; quiver = "ranger_wayfarer_quiver" },
    @{ prefix = "a_ranger_sharpshooter"; base = "archer/ranger/sharpshooter"; weapon = "sharpshooter_siegebow"; quiver = "sharpshooter_siege_quiver" },
    @{ prefix = "a_ranger_shadow_hunter"; base = "archer/ranger/shadow_hunter"; weapon = "shadow_hunter_gloombow"; quiver = "shadow_hunter_night_quiver" },
    @{ prefix = "m_arcanist"; base = "mage/arcanist"; weapon = "arcanist_rune_staff" },
    @{ prefix = "m_arcanist_archmage"; base = "mage/arcanist/archmage"; weapon = "archmage_comet_staff" },
    @{ prefix = "m_arcanist_cleric"; base = "mage/arcanist/cleric"; weapon = "cleric_sanctified_crozier" },
    @{ prefix = "m_elementalist"; base = "mage/elementalist"; weapon = "elementalist_primal_staff" },
    @{ prefix = "m_elementalist_pyromancer"; base = "mage/elementalist/pyromancer"; weapon = "pyromancer_cinder_staff" },
    @{ prefix = "m_elementalist_elemental_master"; base = "mage/elementalist/elemental_master"; weapon = "elemental_master_conflux_staff" },
    @{ prefix = "s_warrior"; base = "warrior/warrior"; weapon = "warrior_guardblade"; offhand = "warrior_guard_shield" },
    @{ prefix = "s_warrior_paladin"; base = "warrior/warrior/paladin"; weapon = "paladin_sunblade"; offhand = "paladin_aegis_shield" },
    @{ prefix = "s_warrior_elite_guard"; base = "warrior/warrior/elite_guard"; weapon = "elite_guard_bastion_blade"; offhand = "elite_guard_wall_shield" },
    @{ prefix = "s_barbarian"; base = "warrior/barbarian"; weapon = "barbarian_raider_axe" },
    @{ prefix = "s_barbarian_predator"; base = "warrior/barbarian/predator"; weapon = "predator_ripper_axe" },
    @{ prefix = "s_barbarian_berserker"; base = "warrior/barbarian/berserker"; weapon = "berserker_frenzy_axe" }
)

foreach ($line in $lineTemplates) {
    Move-SetPieces $line.prefix $line.base
    if ($line.weapon) {
        Move-IfExists "data/item_templates/$($line.weapon).json" "data/item_templates/$($line.base)/weapon/$($line.weapon).json"
    }
    if ($line.quiver) {
        Move-IfExists "data/item_templates/$($line.quiver).json" "data/item_templates/$($line.base)/quiver/$($line.quiver).json"
    }
    if ($line.offhand) {
        Move-IfExists "data/item_templates/$($line.offhand).json" "data/item_templates/$($line.base)/offhand/$($line.offhand).json"
    }
}

$accessories = @(
    "amber_pendant", "apprentice_choker", "aether_prism", "bastion_sigil", "bloodroot_talisman",
    "copper_luck_ring", "deadeye_medallion", "foxbone_charm", "hunters_trophy", "iron_ring",
    "mercenary_token", "mist_runner_charm", "moonglass_earring", "oracle_eye", "runed_teardrop",
    "shadowstep_anklet", "silver_ring", "smuggler_seal", "staghide_brooch", "starforge_emblem",
    "swift_knot", "warded_loop"
)
foreach ($name in $accessories) {
    Move-IfExists "data/item_templates/$name.json" "data/item_templates/general/accessories/$name.json"
}

$baseItems = @(
    @{ slot = "weapon"; base = "archer/base"; names = @("simple_bow", "short_bow") },
    @{ slot = "quiver"; base = "archer/base"; names = @("training_quiver") },
    @{ slot = "weapon"; base = "mage/base"; names = @("basic_scepter", "novice_staff") },
    @{ slot = "weapon"; base = "warrior/base"; names = @("rusty_sword") },
    @{ slot = "offhand"; base = "warrior/base"; names = @("wooden_buckler") }
)
foreach ($group in $baseItems) {
    foreach ($name in $group.names) {
        Move-IfExists "data/items/$name.json" "data/items/$($group.base)/$($group.slot)/$name.json"
    }
}

Move-ClassRewardWeapons "a_hunter" "archer/hunter" -HasQuiver
Move-ClassRewardWeapons "a_hunter_bounty_hunter" "archer/hunter/bounty_hunter" -HasQuiver
Move-ClassRewardWeapons "a_hunter_assassin" "archer/hunter/assassin" -HasQuiver
Move-ClassRewardWeapons "a_ranger" "archer/ranger" -HasQuiver
Move-ClassRewardWeapons "a_ranger_sharpshooter" "archer/ranger/sharpshooter" -HasQuiver
Move-ClassRewardWeapons "a_ranger_shadow_hunter" "archer/ranger/shadow_hunter" -HasQuiver
Move-ClassRewardWeapons "m_arcanist" "mage/arcanist"
Move-ClassRewardWeapons "m_arcanist_archmage" "mage/arcanist/archmage"
Move-ClassRewardWeapons "m_arcanist_cleric" "mage/arcanist/cleric"
Move-ClassRewardWeapons "m_elementalist" "mage/elementalist"
Move-ClassRewardWeapons "m_elementalist_pyromancer" "mage/elementalist/pyromancer"
Move-ClassRewardWeapons "m_elementalist_elemental_master" "mage/elementalist/elemental_master"
Move-ClassRewardWeapons "s_warrior" "warrior/warrior"
Move-ClassRewardWeapons "s_warrior_paladin" "warrior/warrior/paladin"
Move-ClassRewardWeapons "s_warrior_elite_guard" "warrior/warrior/elite_guard"
Move-ClassRewardWeapons "s_barbarian" "warrior/barbarian"
Move-ClassRewardWeapons "s_barbarian_predator" "warrior/barbarian/predator"
Move-ClassRewardWeapons "s_barbarian_berserker" "warrior/barbarian/berserker"

$itemBuckets = @{
    "ammo/arrows" = @("arrow_simple", "arrow_iron", "arrow_steel", "arrow_mithril", "arrow_poison", "arrow_frost", "arrow_flame", "arrow_explosive")
    "consumables/potions" = @("antidote", "hp_potion_small", "hp_potion_medium", "hp_potion_large", "mp_potion_small", "mp_potion_medium", "mp_potion_large", "hybrid_potion_small", "hybrid_potion_medium", "hybrid_potion_large", "potion_small", "potion_strength", "potion_defense", "potion_speed", "potion_critical", "potion_regeneration", "potion_resistance")
    "consumables/food" = @("field_ration", "grilled_fish", "grilled_meat", "meat_stew", "nutritious_broth", "rice_meat", "seasoned_fish", "simple_banquet", "simple_soup", "smoked_fish")
    "cash" = @("cash_bag_small", "cash_bag_medium", "cash_bag_large", "cash_full_restore_potion", "cash_immunity_potion", "cash_room_buff_10", "cash_room_buff_25", "cash_room_buff_50", "cash_run_buff_10", "cash_run_buff_25", "cash_run_buff_50", "cash_weapon_lv5", "cash_weapon_lv10", "cash_weapon_lv50", "cash_weapon_lv100")
    "materials/ore" = @("adamantite_ore", "copper_ore", "gold_ore", "iron_ore", "mithril_ore", "obsidian_ore", "silver_ore", "tin_ore", "titanium_ore")
    "materials/bar" = @("adamantite_bar", "copper_bar", "gold_bar", "iron_bar", "iron_ingot", "mithril_bar", "obsidian_bar", "reinforced_alloy", "runic_bar", "silver_bar", "titanium_bar")
    "materials/wood" = @("ancient_wood", "bright_wood", "dense_wood", "enchanted_wood", "fossil_wood", "giant_log", "shadow_wood", "sturdy_wood", "weak_wood", "wood_log")
    "materials/herb" = @("ancient_seed", "bitter_root", "blue_calm_flower", "crimson_flower", "crystal_plant", "golden_flower", "luminous_mushroom", "medicinal_herb", "shadow_herb", "thorny_vine", "toxic_herb", "wild_mint_leaf")
    "materials/fishing" = @("abyssal_fish", "catfish", "eel", "ghost_fish", "giant_carp", "golden_fish", "luminous_fish", "salmon", "sardine", "small_fish", "tilapia", "tuna")
    "materials/food" = @("beast_meat", "herb_meat", "rice")
    "materials/essence" = @("arcane_crystal", "arcane_fragment", "charcoal", "crystal_shard", "dark_essence", "enchanted_ash", "gunpowder", "heated_stone", "igneous_core", "living_ember", "magma_shard", "oil", "runic_core", "runic_stone", "slime_gel", "slime_piece", "stone_chunk", "unstable_crystal", "venom", "viscous_core")
    "materials/monster_parts" = @("broken_plate", "broken_skull", "cracked_bone", "damaged_gear", "dull_dagger", "empty_pouch", "old_rag", "predator_eye", "profaned_medallion", "rat_tail", "rough_hide", "rusted_coin", "torn_cape", "wolf_fang", "wolf_pelt", "worn_glove")
    "loot/flavor" = @("arcane_screw", "bent_insignia", "bone_dust", "charred_ash", "chipped_shiv", "cinder_flake", "cloudy_core", "cracked_lens", "frayed_mask", "greasy_whisker", "matted_fur", "murky_sludge", "rotten_shroud", "sewer_tooth", "splintered_claw", "tainted_hide")
    "system" = @("shop_potion_fixed")
}

foreach ($bucket in $itemBuckets.Keys) {
    foreach ($name in $itemBuckets[$bucket]) {
        Move-IfExists "data/items/$name.json" "data/items/$bucket/$name.json"
    }
}

if (Test-Path -LiteralPath "classes_padronizado.txt") {
    Ensure-Dir "docs/local_notes"
    Move-IfExists "classes_padronizado.txt" "docs/local_notes/classes_padronizado.txt"
}
if (Test-Path -LiteralPath "classes legivel.txt") {
    Ensure-Dir "docs/local_notes"
    Move-IfExists "classes legivel.txt" "docs/local_notes/classes_legivel.txt"
}
