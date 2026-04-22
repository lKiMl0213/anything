package rpg.registry

import rpg.model.DropTableDef

class DropTableRegistry(private val tables: Map<String, DropTableDef>) {
    fun get(id: String): DropTableDef? = tables[id]

    fun require(id: String): DropTableDef = tables[id] ?: error("Drop table nao encontrada: $id")
}
