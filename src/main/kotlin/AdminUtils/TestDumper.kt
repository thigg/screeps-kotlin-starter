package AdminUtils

import screeps.api.*

typealias LookAtAreaResult = Record<Int, Record<Int, Array<RoomPosition.Look>>>


object TestDumper {


    fun dumpRoom(room: Room){
        room.lookAtArea(0,0,50,50)
    }
}