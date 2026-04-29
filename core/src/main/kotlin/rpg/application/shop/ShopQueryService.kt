package rpg.application.shop

import kotlin.math.ceil
import rpg.classsystem.RaceBonusSupport
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.model.ItemType
import rpg.model.ShopCurrency
import rpg.progression.PermanentUpgradeService

class ShopQueryService(
    private val engine: GameEngine,
    private val repo: DataRepository,
    private val permanentUpgradeService: PermanentUpgradeService
) {
    fun categories(player: rpg.model.PlayerState, currency: ShopCurrency): List<ShopCategorySummary> {
        val entries = currencyEntries(currency)
        return ShopCategory.entries.map { category ->
            val scoped = if (category == ShopCategory.WEAPONS) {
                entries.filter { classifyCategory(it) == category }
            } else {
                entries.filter { classifyCategory(it) == category }
            }
            ShopCategorySummary(category, scoped.size)
        }
    }

    fun entries(
        player: rpg.model.PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        currency: ShopCurrency,
        category: ShopCategory,
        weaponClass: WeaponClassCategory? = null
    ): List<ShopDisplayEntry> {
        val scoped = currencyEntries(currency)
            .filter { classifyCategory(it) == category }
            .sortedWith(compareBy({ it.minPlayerLevel }, { it.price }, { it.name }))
        return scoped
            .filter { entry ->
                if (category != ShopCategory.WEAPONS || weaponClass == null || weaponClass == WeaponClassCategory.GENERAL) {
                    true
                } else {
                    val resolved = classifyWeaponClass(entry)
                    resolved == weaponClass || resolved == WeaponClassCategory.GENERAL
                }
            }
            .map { entry ->
                val specialOffer = false
                ShopDisplayEntry(
                    id = entry.id,
                    itemId = entry.itemId,
                    name = shopEntryItemName(entry),
                    description = shopEntryDescription(entry),
                    quantity = entry.quantity.coerceAtLeast(1),
                    requiredLevel = requiredLevelForShopEntry(entry),
                    finalPrice = effectivePrice(player, entry, specialOffer),
                    currency = entry.currency,
                    category = classifyCategory(entry),
                    weaponClassCategory = classifyWeaponClass(entry),
                    inStock = true,
                    specialOffer = specialOffer
                )
            }
    }

    fun upgradeCategories(currency: ShopCurrency): List<UpgradeCategorySummary> {
        val grouped = permanentUpgradeService.definitions().groupBy(::resolveUpgradeCategory)
        return UpgradeMenuCategory.entries.map { category ->
            val count = grouped[category].orEmpty().size
            UpgradeCategorySummary(category, count)
        }
    }

    fun upgrades(
        player: rpg.model.PlayerState,
        currency: ShopCurrency,
        category: UpgradeMenuCategory
    ): List<UpgradeDisplayEntry> {
        return permanentUpgradeService.definitions()
            .asSequence()
            .filter { resolveUpgradeCategory(it) == category }
            .sortedBy { it.name }
            .map { def ->
                val level = permanentUpgradeService.currentLevel(player, def.id)
                val currentValue = permanentUpgradeService.currentEffectValue(player, def.id)
                val nextValue = permanentUpgradeService.nextEffectValue(player, def.id)
                UpgradeDisplayEntry(
                    id = def.id,
                    name = def.name,
                    description = def.description,
                    level = level,
                    maxLevel = def.maxLevel,
                    currentLabel = if (level > 0) permanentUpgradeService.effectLabel(def, currentValue) else "0",
                    nextLabel = nextValue?.let { permanentUpgradeService.effectLabel(def, it) } ?: "MAX",
                    costs = permanentUpgradeService.nextLevelCosts(player, def.id, currency),
                    atMaxLevel = level >= def.maxLevel
                )
            }
            .toList()
    }

    fun classifyCategory(entry: rpg.model.ShopEntryDef): ShopCategory {
        val lowerTags = entry.tags.map { it.trim().lowercase() }
        if ("accessory" in lowerTags || "ring" in lowerTags || "amulet" in lowerTags) return ShopCategory.ACCESSORIES
        if ("armor" in lowerTags || "armadura" in lowerTags) return ShopCategory.ARMORS
        if ("weapon" in lowerTags || "sword" in lowerTags || "axe" in lowerTags || "bow" in lowerTags || "staff" in lowerTags || "shield" in lowerTags) {
            return ShopCategory.WEAPONS
        }
        val resolved = engine.itemRegistry.entry(entry.itemId) ?: return ShopCategory.ITEMS
        if (resolved.type != ItemType.EQUIPMENT) return ShopCategory.ITEMS
        return when (resolved.slot) {
            rpg.model.EquipSlot.WEAPON_MAIN,
            rpg.model.EquipSlot.WEAPON_OFF,
            rpg.model.EquipSlot.ALJAVA -> ShopCategory.WEAPONS
            rpg.model.EquipSlot.ACCESSORY -> ShopCategory.ACCESSORIES
            else -> ShopCategory.ARMORS
        }
    }

    fun classifyWeaponClass(entry: rpg.model.ShopEntryDef): WeaponClassCategory {
        val tags = entry.tags.map { it.trim().lowercase() }.toSet()
        if ("archer" in tags || "{archer}" in tags) return WeaponClassCategory.ARCHER
        if ("mage" in tags || "{mage}" in tags) return WeaponClassCategory.MAGE
        if ("warrior" in tags || "swordman" in tags || "{swordman}" in tags) return WeaponClassCategory.WARRIOR

        val itemId = entry.itemId.lowercase()
        val name = shopEntryItemName(entry).lowercase()
        return when {
            listOf("bow", "arco", "crossbow", "quiver", "aljava", "arrow", "flecha").any { it in itemId || it in name } ->
                WeaponClassCategory.ARCHER
            listOf("staff", "cajado", "scepter", "rod", "grim", "focus", "orb").any { it in itemId || it in name } ->
                WeaponClassCategory.MAGE
            listOf("sword", "espada", "axe", "machado", "shield", "escudo", "mace", "hammer").any { it in itemId || it in name } ->
                WeaponClassCategory.WARRIOR
            else -> WeaponClassCategory.GENERAL
        }
    }

    fun requiredLevelForShopEntry(entry: rpg.model.ShopEntryDef): Int {
        val itemReq = engine.itemRegistry.item(entry.itemId)?.minLevel
            ?: engine.itemRegistry.template(entry.itemId)?.minLevel
        return itemReq?.coerceAtLeast(1) ?: entry.minPlayerLevel.coerceAtLeast(1)
    }

    fun effectivePrice(player: rpg.model.PlayerState, entry: rpg.model.ShopEntryDef, specialOffer: Boolean): Int {
        val reqLevel = requiredLevelForShopEntry(entry)
        val base = entry.price.coerceAtLeast(1).toDouble()
        val levelCurve = if (reqLevel <= 1) 1.0 else 1.0 + ((reqLevel - 1) * 0.04).coerceAtMost(1.6)
        val rarityCurve = when (engine.itemRegistry.entry(entry.itemId)?.rarity) {
            rpg.item.ItemRarity.COMMON -> 1.00
            rpg.item.ItemRarity.UNCOMMON -> 1.06
            rpg.item.ItemRarity.RARE -> 1.12
            rpg.item.ItemRarity.EPIC -> 1.26
            rpg.item.ItemRarity.LEGENDARY -> 1.45
            rpg.item.ItemRarity.MYTHIC -> 1.65
            null -> 1.0
        }
        val categoryCurve = when (classifyCategory(entry)) {
            ShopCategory.ITEMS -> 0.95
            ShopCategory.ARMORS -> 1.08
            ShopCategory.ACCESSORIES -> 1.14
            ShopCategory.WEAPONS -> 1.12
        }
        val stockCurve = if (specialOffer) 1.32 else 1.0
        val raceDef = runCatching { engine.classSystem.raceDef(player.raceId) }.getOrNull()
        val raceDiscount = RaceBonusSupport.tradeBuyDiscountPct(raceDef)
        val raceMultiplier = (1.0 - raceDiscount / 100.0).coerceIn(0.75, 1.0)
        return ceil(base * levelCurve * rarityCurve * categoryCurve * stockCurve * raceMultiplier).toInt().coerceAtLeast(1)
    }

    fun shouldMarkSpecialOffer(player: rpg.model.PlayerState, entry: rpg.model.ShopEntryDef): Boolean {
        return false
    }

    fun isInStock(player: rpg.model.PlayerState, entry: rpg.model.ShopEntryDef, specialOffer: Boolean): Boolean {
        return true
    }

    fun shopEntryItemName(entry: rpg.model.ShopEntryDef): String {
        val resolvedName = engine.itemRegistry.item(entry.itemId)?.name ?: engine.itemRegistry.template(entry.itemId)?.name
        return if (resolvedName.isNullOrBlank()) entry.name else resolvedName
    }

    fun shopEntryDescription(entry: rpg.model.ShopEntryDef): String {
        if (entry.description.isNotBlank()) return entry.description
        return engine.itemRegistry.entry(entry.itemId)?.description ?: ""
    }

    private fun currencyEntries(currency: ShopCurrency): List<rpg.model.ShopEntryDef> {
        return repo.shopEntries.values.filter { it.enabled && it.currency == currency }
    }

    private fun resolveUpgradeCategory(def: rpg.model.PermanentUpgradeDef): UpgradeMenuCategory {
        return when (def.effectType) {
            rpg.model.PermanentUpgradeEffectType.PROFESSION_XP_BOOST,
            rpg.model.PermanentUpgradeEffectType.CRAFT_BATCH_BONUS,
            rpg.model.PermanentUpgradeEffectType.FISHING_DOUBLE_CHANCE,
            rpg.model.PermanentUpgradeEffectType.HERBALISM_DOUBLE_CHANCE,
            rpg.model.PermanentUpgradeEffectType.MINING_DOUBLE_CHANCE,
            rpg.model.PermanentUpgradeEffectType.WOODCUTTING_DOUBLE_CHANCE,
            rpg.model.PermanentUpgradeEffectType.FORGE_COST_REDUCTION,
            rpg.model.PermanentUpgradeEffectType.COOKING_COST_REDUCTION,
            rpg.model.PermanentUpgradeEffectType.ALCHEMY_COST_REDUCTION -> UpgradeMenuCategory.PRODUCTION

            rpg.model.PermanentUpgradeEffectType.COMBAT_XP_BOOST,
            rpg.model.PermanentUpgradeEffectType.MONSTER_RARITY_BONUS -> UpgradeMenuCategory.BATTLE

            rpg.model.PermanentUpgradeEffectType.QUEST_ITEM_KEEP_CHANCE,
            rpg.model.PermanentUpgradeEffectType.TAVERN_COST_REDUCTION,
            rpg.model.PermanentUpgradeEffectType.QUIVER_CAPACITY_BONUS -> UpgradeMenuCategory.UTILITY
        }
    }
}
