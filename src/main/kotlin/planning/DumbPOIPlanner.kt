package planning

import MyGameConstants.RCLConstants
import planning.building.DumbStreetBuilder
import screeps.api.*
import screeps.api.structures.StructureExtension
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
        room.find(FIND_MY_CONSTRUCTION_SITES, options { filter = { t -> t.structureType == STRUCTURE_EXTENSION } }).map { s -> DumbPOI(DumbPOIType.STORE, s.pos, s) }
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
        console.log("${projects.size} Road planning projects")

        val withStreetCosts = projects.map { p: StreetProject -> Pair(p, p.path.sumBy { a -> if (room.lookForAt(LOOK_STRUCTURES, a.x, a.y)!!.isEmpty()) 1 else 0 }) }.filter { p -> p.second != 0 }

        if (withStreetCosts.isNotEmpty())
            withStreetCosts[0].first.run {
                console.log("Selected Project $this with cost ${withStreetCosts.get(0)?.second}")
                DumbStreetBuilder.planStreet(room, a.pos, b.pos)
            }
    }

    fun planEnergyStores(room: Room) {
        if (room.controller!!.level <= 2) return
        if (room.find(FIND_MY_CONSTRUCTION_SITES, options { filter = { c -> c.structureType == STRUCTURE_EXTENSION } }).size > 0) return
        if (room.find(FIND_MY_STRUCTURES).count { it.structureType == STRUCTURE_EXTENSION } == RCLConstants.extensions_allowed[room.controller!!.level]) return
        //else if (room.find(FIND_MY_STRUCTURES).count { it.structureType == STRUCTURE_CONTAINER } < room.find(FIND_SOURCES).size) plantContainersToSources()
        val pois = getPOIs(room)
        val stores = pois.filter { a -> a.type == DumbPOIType.STORE }
        var preffered: List<RoomPosition> = listOf()
        stores.forEach { p ->
            for (dx in -3..3) for (dy in -3..3)
            //no buildings
                if (room.lookAt(p.pos.x + dx, p.pos.y + dy).filter { t ->
                            t.type == LOOK_CONSTRUCTION_SITES || t.type == LOOK_STRUCTURES
                        }.size == 0)
                //swamp
                    if (room.lookAt(p.pos.x + dx, p.pos.y + dy).filter { t -> t.type == LOOK_TERRAIN && t.terrain == TERRAIN_SWAMP }.size > 0)
                        preffered += room.getPositionAt(p.pos.x + dx, p.pos.y + dy)!!
        }
        preffered = preffered.filter { p -> room.findPath(p, room.controller!!.pos).isNotEmpty() }
        console.log("Found ${preffered.size} locations for an extension")
        //preffered.sortedBy { p-> room. }
        val ret = preffered[0].createConstructionSite(STRUCTURE_EXTENSION)
        if (ret == OK) console.log("Created Construction Site on ${preffered[0]}!!")
        else console.log("Got return code $ret while creating extension at ${preffered[0]}")
    }

    private fun isFreeForBuilding(pos: RoomPosition, room: Room): Boolean {
        return room.lookAt(pos.x, pos.y).filter { t ->
            t.type == LOOK_CONSTRUCTION_SITES || t.type == LOOK_STRUCTURES || (t.type == LOOK_TERRAIN && t.terrain == TERRAIN_WALL)
        }.isEmpty()
    }

    /*private fun plantContainersToSources(room: Room) {
        val stores = getPOIs(room).filter { a -> a.type == DumbPOIType.STORE }//, options { filter = { it.structureType == STRUCTURE_CONTAINER }
        val targets = room.find(FIND_SOURCES).filter { it.pos.findInRange(FIND_MY_CONSTRUCTION_SITES or FIND_MY_STRUCTURES, 4 ).isEmpty() }
        targets.forEach { it ->
            connectToClosest(DumbPOI(DumbPOIType.SOURCE, it.pos, it), stores, room).path.find {
                val pos = room.getPositionAt(it.x, it.y)!! + it.direction.turnLeft()
                if (isFreeForBuilding(pos,room)){
                    pos.createConstructionSite(STRUCTURE_CONTAINER)
                    true
                }
                else false
            }
        }
    }*/
}

fun DirectionConstant.getDelta(): RoomPositionDelta = when (this) {
    TOP -> RoomPositionDelta(0, -1)
    TOP_RIGHT -> RoomPositionDelta(1, -1)
    RIGHT -> RoomPositionDelta(1, 0)
    BOTTOM_RIGHT -> RoomPositionDelta(1, 1)
    BOTTOM -> RoomPositionDelta(0, 1)
    BOTTOM_LEFT -> RoomPositionDelta(-1, 1)
    LEFT -> RoomPositionDelta(-1, 0)
    TOP_LEFT ->  RoomPositionDelta(-1, -1)
    else -> RoomPositionDelta(0, 0)
}

data class RoomPositionDelta(val dx: Int, val dy: Int)

operator fun RoomPosition.plus(delta: RoomPositionDelta): RoomPosition = RoomPosition(x + delta.dx, y + delta.dy, roomName)


operator fun RoomPosition.plus(dir: DirectionConstant): RoomPosition {
    val delta = dir.getDelta()
    return RoomPosition(x + delta.dx, y + delta.dy, roomName)
}


fun DirectionConstant.turnLeft(): DirectionConstant =
        when (this) {
            TOP -> TOP_RIGHT
            TOP_RIGHT -> RIGHT
            RIGHT -> BOTTOM_RIGHT
            BOTTOM_RIGHT -> BOTTOM
            BOTTOM -> BOTTOM_LEFT
            BOTTOM_LEFT -> LEFT
            LEFT -> TOP_LEFT
            TOP_LEFT -> TOP
            else -> throw IllegalArgumentException("Trying to switch an unexpected direction! $this")
        }

fun DirectionConstant.turnRight(): DirectionConstant = turnLeft().turnLeft().turnLeft().turnLeft().turnLeft().turnLeft().turnLeft()
