package starter

import screeps.api.*
import screeps.api.structures.StructureController


enum class Role {
    UNASSIGNED,
    HARVESTER,
    BUILDER,
    UPGRADER
}

fun Creep.upgrade(controller: StructureController) {
    energFSM()
    if (memory.refillEnergy) {
        goForEnergy()
    } else {
        if (pos.inRangeTo(controller.pos, 2))
            upgradeController(controller)
        else
            moveTo(controller.pos)
    }
}

private fun Creep.energFSM() {
    if (carry.energy == carryCapacity)
        memory.refillEnergy = false
    if (carry.energy == 0) memory.refillEnergy = true
}

private fun Creep.goForEnergy() {
    getClosestEnergySource()
    val target = memory.move.target
    if (target != null)
        if (target.getRangeTo(pos) > 1)
            moveTo(target)
        else
            getEnergyFrom(target)
}

fun Creep.getEnergyFrom(target: RoomPosition) {
    target.look().forEach { e ->
        when (e.type) {
            LOOK_STRUCTURES -> {
                this.withdraw(e.structure!!, RESOURCE_ENERGY); return
            }
            LOOK_ENERGY -> {
                this.pickup(e.resource!!); return
            }
            LOOK_SOURCES -> {
                e.resource.let { a -> if (a is Source) harvest(a) };return
            }
        }
    }
}

fun Creep.getClosestEnergySource() {
    val targets: Array<RoomObject> = (room.find(FIND_SOURCES) +
            room.find(FIND_DROPPED_RESOURCES) +
            room.find(FIND_STRUCTURES).filter { s ->
                when (s.structureType) {
                    STRUCTURE_CONTAINER, STRUCTURE_STORAGE -> true
                    else -> false
                }
            }).map { a -> a as RoomObject }.toTypedArray()
    val target: RoomObject? = pos.findClosestByPath<RoomObject>(targets)
    if(target == null) return
    memory.move.target = target.pos
    memory.move.lastUpd = Game.time
}


fun Creep.pause() {
    if (memory.pause < 10) {
        //blink slowly
        if (memory.pause % 3 != 0) say("\uD83D\uDEAC")
        memory.pause++
    } else {
        memory.pause = 0
        memory.role = Role.HARVESTER
    }
}

fun Creep.build(assignedRoom: Room = this.room) {
   energFSM()

    if (!memory.refillEnergy) {
        val targets = assignedRoom.find(FIND_MY_CONSTRUCTION_SITES)
        if (targets.isNotEmpty()) {
            if (build(targets[0]) == ERR_NOT_IN_RANGE) {
                moveTo(targets[0].pos)
            }
        }
    } else {
        goForEnergy()
    }
}

fun Creep.harvest(fromRoom: Room = this.room, toRoom: Room = this.room): Unit {
    if (carry.energy < carryCapacity) {
        val res = pos.findInRange(FIND_SOURCES, 1)
        if (res.isNotEmpty())
            harvest(res[0])
        else {
            val closest = pos.findClosestByPath(FIND_SOURCES)
            if (closest != null)
                moveTo(closest)
        }


    } else {
        val targets = toRoom.find(FIND_MY_STRUCTURES)
                .filter { (it.structureType == STRUCTURE_EXTENSION || it.structureType == STRUCTURE_SPAWN) }
                .filter { it.unsafeCast<EnergyContainer>().energy < it.unsafeCast<EnergyContainer>().energyCapacity }

        if (targets.isNotEmpty()) {
            if (transfer(targets[0], RESOURCE_ENERGY) == ERR_NOT_IN_RANGE) {
                moveTo(targets[0].pos)
            }
        }
    }
}