package planning

import MyGameConstants.RCLConstants
import planning.building.DumbStreetBuilder
import screeps.api.*
import screeps.api.structures.StructureSpawn

enum class DumbPOIType {
    SOURCE, STORE, SINK
}

data class DumbPOI(val type: DumbPOIType, val pos: RoomPosition, val obj: RoomObject)

data class StreetProject(val a: DumbPOI, val b: DumbPOI, val path: Array<Room.PathStep>)

object DumbPOIPlanner {

    fun getPOIs(room: Room): List<DumbPOI> {
        var pois = room.find(FIND_SOURCES).map { s: Source -> DumbPOI(DumbPOIType.SOURCE, s.pos, s) } +
                room.find(FIND_MY_SPAWNS).map { s: StructureSpawn -> DumbPOI(DumbPOIType.STORE, s.pos, s) }
        room.controller?.run {
            pois += listOf(DumbPOI(DumbPOIType.SINK, pos, this))
        }
        return pois
    }

    private fun connectToClosest(poi: DumbPOI, others: List<DumbPOI>, room: Room): StreetProject {
        val paths = others.map { sto -> Pair(sto, room.findPath(poi.pos, sto.pos, options { ignoreCreeps = true })) }.sortedBy { a -> a.second.size }
        return StreetProject(poi, paths[0].first, paths[0].second)
    }

    fun planEnergyNet(room: Room) {
        if (room.find(FIND_MY_CONSTRUCTION_SITES, options { filter = { c -> c.structureType == STRUCTURE_ROAD } }).size > 2) return
        val pois = getPOIs(room)
        //connect sources to stores and sinks to closest source/store
        val sources = pois.filter { a -> a.type == DumbPOIType.SOURCE }
        val stores = pois.filter { a -> a.type == DumbPOIType.STORE }
        val sinks = pois.filter { a -> a.type == DumbPOIType.SINK }
        val projects = sources.map { src -> connectToClosest(src, stores, room) } +
                sinks.map { sink -> connectToClosest(sink, sources + stores, room) }


        val withStreetCosts = projects.map { p: StreetProject -> Pair(p, p.path.sumBy { a -> if (room.lookForAt(LOOK_STRUCTURES, a.x, a.y)!!.size > 0) 1 else 0 }) }.filter { p -> p.second != 0 }
        if (withStreetCosts.isNotEmpty())
            withStreetCosts[0].first.run {
                DumbStreetBuilder.planStreet(room, a.pos, b.pos)
            }
    }

    fun planEnergyStores(room: Room) {
        if (room.find(FIND_MY_CONSTRUCTION_SITES, options { filter = { c -> c.structureType == STRUCTURE_EXTENSION } }).size > 0) return
        if (room.find(FIND_MY_STRUCTURES).count { it.structureType == STRUCTURE_EXTENSION } == RCLConstants.extensions_allowed[room.controller!!.level]) return

        val pois = getPOIs(room)
        val stores = pois.filter { a -> a.type == DumbPOIType.STORE }
        var preffered: List<RoomPosition> = listOf()
        stores.forEach { p ->
            for (dx in -3..3) for (dy in -3..3)
                //no buildings
                if (room.lookAt(p.pos.x + dx, p.pos.y + dy).filter { t ->
                            !(t.type == LOOK_CONSTRUCTION_SITES || t.type == LOOK_STRUCTURES)
                        }.size != null)
                    //swamp
                    if (room.lookAt(p.pos.x + dx, p.pos.y + dy).filter { t -> t.type == LOOK_TERRAIN && t.terrain == TERRAIN_SWAMP }.size > 0)
                        preffered += room.getPositionAt(p.pos.x + dx, p.pos.y + dy)!!
        }
        preffered = preffered.filter { p -> room.findPath(p, room.controller!!.pos).isNotEmpty() }
        console.log("Found ${preffered.size} locations for an extension")
        //preffered.sortedBy { p-> room. }
        preffered[0].createConstructionSite(STRUCTURE_EXTENSION)
        console.log("Created Construction Site!!")
    }
}