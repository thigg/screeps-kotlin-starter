package starter

import screeps.api.*
import screeps.api.structures.Structure
import screeps.api.structures.StructureController
import screeps.api.structures.StructureSpawn


enum class Role {
    UNASSIGNED,
    HARVESTER,
    BUILDER,
    UPGRADER,
    TRANSPORTER
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
    if (target != null) {
        val distance = pos.getRangeTo(target)
        if (distance > 1)
            moveTo(target)
        else
            getEnergyFrom(target)
    }
}

fun Creep.getEnergyFrom(target: RoomPosition) {
    Game.rooms[target.roomName]!!.lookAt(target).forEach { e ->
        when (e.type) {
            LOOK_STRUCTURES -> {
                if (this.withdraw(e.structure!!, RESOURCE_ENERGY) == OK) return
            }
            LOOK_ENERGY -> {
                if (e.resource != null && this.pickup(e.resource!!) == OK) return
                else console.log("NPE while ${this.name} while trying to pickup $target (${e.resource})")
            }
            LOOK_SOURCES -> {
                try {
                    harvest(target.lookFor(LOOK_SOURCES)!![0])
                } catch (e: Exception) {
                    console.log("NPE on ${this.name} while trying to harvest $target")
                }
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
    if (target == null) return
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

fun Room.isOurs(): Boolean {
    return Game.spawns.values.find { spawn -> spawn.room == this } != null
}

fun Creep.harvest(fromRoom: Room = this.room, toRoom: Room = this.room): Unit {
    //if(!room.isOurs())harvest(room,Game.spawns)
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
        //choose near creeps as target, when they can take half of what we store and are not too far away
        val creepTargets = room.find(FIND_CREEPS).filter { c -> c.my && c.memory.role != Role.HARVESTER && c.carryCapacity-c.carry.energy <= carry.energy/2 && c.pos.getRangeTo(pos) < (c.carryCapacity - c.carry.energy) / 2 }
        val closest: RoomObject? = pos.findClosestByPath<RoomObject>((targets + creepTargets).map { a -> a as RoomObject }.toTypedArray())
        if (closest != null) {
            if (closest is Creep) {
                say("toCreep")
                if (transfer(closest, RESOURCE_ENERGY) == ERR_NOT_IN_RANGE)
                    moveTo(closest.pos)
            }
            if (closest is Structure) {
                say("toStructure")
                if (transfer(closest, RESOURCE_ENERGY) == ERR_NOT_IN_RANGE)
                    moveTo(closest.pos)
            }
        }else {
            say(":(")
            console.log("Found no target from ${creepTargets.size} Creeps and ${targets.size} Structures = ${(targets + creepTargets).size}")
        }
    }
}