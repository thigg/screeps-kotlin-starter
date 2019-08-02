package Monkeys

import screeps.api.*

/**
 * Find with multiple flags
 */
fun RoomPosition.findClosestByPathMulti(
        types: Array<FindConstant<RoomObject>>,
        opts: FindClosestByPathOptions<RoomObject>
): RoomObject? {
    val objects: Array<RoomObject> = types.map { Game.rooms[this.roomName]?.find(it) }.foldRight(arrayOf<RoomObject>(), { l, r -> l!! + r })
    return findClosestByPath(objects, opts)
}

/**
 * Checks if there is no structure constructionsite or wall
 */
fun RoomPosition.isFreeForBuilding( room: Room): Boolean {
    return room.lookAt(x, y).filter { t ->
        t.type == LOOK_CONSTRUCTION_SITES || t.type == LOOK_STRUCTURES || (t.type == LOOK_TERRAIN && t.terrain == TERRAIN_WALL)
    }.isEmpty()
}