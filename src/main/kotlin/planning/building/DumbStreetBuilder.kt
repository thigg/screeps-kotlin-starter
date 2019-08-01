package planning.building

import screeps.api.Room
import screeps.api.RoomPosition
import screeps.api.STRUCTURE_ROAD
import screeps.api.options

object DumbStreetBuilder {
    fun planStreet(room: Room, a: RoomPosition, b: RoomPosition) {
        room.findPath(a, b, options {
            ignoreCreeps = true
        }).forEach { step -> room.getPositionAt(step.x, step.y)?.createConstructionSite(STRUCTURE_ROAD) }

    }
}