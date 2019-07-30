package AdminUtils

import screeps.api.Room
import screeps.api.RoomObject
import screeps.api.lookAtArea

object TestDumper {
    fun dumpRoom(room: Room){
        room.lookAtArea(0,0,50,50)
    }
}