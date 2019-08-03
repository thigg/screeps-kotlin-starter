package starter

import Monkeys.findClosestByPathMulti
import screeps.api.*
import screeps.api.structures.Structure
import screeps.api.structures.StructureController
import screeps.api.structures.StructureExtension
import screeps.api.structures.StructureSpawn
import kotlin.math.min


enum class Role {
    UNASSIGNED,
    HARVESTER,
    BUILDER,
    UPGRADER,
    TRANSPORTER
}

fun Creep.beTransporter() {
    energyFSM()
    if (memory.refillEnergy) {
        goForEnergy(excludeSources = true)
    } else {
        val target: RoomObject? = pos.findClosestByPathMulti(arrayOf(FIND_MY_STRUCTURES, FIND_MY_CREEPS) as Array<FindConstant<RoomObject>>, options {
            filter = {
                when (it) {
                    is Creep -> it.memory.refillEnergy
                    is StructureSpawn -> true
                    is StructureExtension -> true
                    else -> false
                }
            }
        })
        if (target != null) moveTo(target)
        else {
            console.log("Target for transporter @ $pos was null ")
        }
    }
}


fun Creep.upgrade(controller: StructureController) {
    energyFSM()
    if (memory.refillEnergy) {
        goForEnergy()
    } else {
        if (pos.inRangeTo(controller.pos, 2))
            upgradeController(controller)
        else
            moveTo(controller.pos)
    }
}

private fun Creep.energyFSM() {
    if (carry.energy == carryCapacity)
        memory.refillEnergy = false
    if (carry.energy == 0) memory.refillEnergy = true
}

private fun Creep.goForEnergy(excludeSources: Boolean = false) {
    getClosestEnergySource(excludeSources = excludeSources)
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
            LOOK_CREEPS -> {
                if (e.creep?.memory?.role == Role.HARVESTER) {
                    e.creep?.transfer(this, RESOURCE_ENERGY, min(e.creep!!.carry!!.energy, freeStorage()))
                }
            }
            LOOK_ENERGY -> {
                if (e.resource != null) {
                    val ret = this.pickup(target.lookFor(LOOK_ENERGY)!![0])
                    if (ret == OK) return
                    else console.log("Pickup returned $ret)")
                }
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

fun Creep.getClosestEnergySource(excludeSources: Boolean = false) {
    val targets: Array<RoomObject> = ((if (excludeSources) arrayOf() else room.find(FIND_SOURCES)) +
            room.find(FIND_DROPPED_RESOURCES) +
            room.find(FIND_STRUCTURES).filter { s ->
                when (s.structureType) {
                    STRUCTURE_CONTAINER, STRUCTURE_STORAGE -> true
                    else -> false
                }
            } +
            room.find(FIND_MY_CREEPS, options { filter = { it.memory.role == Role.HARVESTER && it.carry.energy / it.carryCapacity > 0.9 } })).map { a -> a as RoomObject }.toTypedArray()
    val target: RoomObject? = pos.findClosestByPath<RoomObject>(targets)
    if (target == null) return
    memory.move.target = target.pos
    memory.move.lastUpd = Game.time
}


fun Creep.build(assignedRoom: Room = this.room) {
    energyFSM()

    if (!memory.refillEnergy) {
        if (repairClose()) return
        val targets = assignedRoom.find(FIND_MY_CONSTRUCTION_SITES)
        if (targets.isNotEmpty()) {
            if (build(targets[0]) == ERR_NOT_IN_RANGE) {
                moveTo(targets[0].pos)
            }
        } else {
            room.controller?.run(::upgrade)
        }
    } else {
        goForEnergy()
    }
}

fun Room.isOurs(): Boolean {
    return Game.spawns.values.find { spawn -> spawn.room == this } != null
}

fun Creep.freeStorage(): Int {
    return carryCapacity - carry.energy
}

fun Creep.transferEnergy(creep: Creep): ScreepsReturnCode {
    return transfer(creep, RESOURCE_ENERGY, min(freeStorage(), creep.freeStorage()))
}

/**
 * repairs close structures with <80%hits
 */
fun Creep.repairClose(): Boolean {
    val targets = pos.findInRange(FIND_MY_STRUCTURES, 2, options { filter = { !(it is StructureController) && it.hits / it.hitsMax <= 0.8 } })
    if (targets.isNotEmpty())
        return this.repair(targets[0]) == OK
    return false
}

fun Creep.harvest(fromRoom: Room = this.room, toRoom: Room = this.room): Unit {
    //if(!room.isOurs())harvest(room,Game.spawns)
    if (carry.energy > 0) {//transfer to close creeps which we can fill up
        val targets = pos.findInRange(FIND_MY_CREEPS, 1, options {
            filter = { other ->
                other.memory.role != Role.HARVESTER && other.memory.refillEnergy &&
                        other.freeStorage() > 4 && carry.energy >= other.freeStorage()
            }
        })
        if (targets.size > 0)
            if (transferEnergy(targets[0]) == OK)
                return
            else console.log("Error! Could not transfer Energy")
    }
    if (carry.energy < carryCapacity) {
        val res = pos.findInRange(FIND_SOURCES, 1)
        if (res.isNotEmpty())
            harvest(res[0])
        else {
            val closest = pos.findClosestByPath(FIND_SOURCES, options { filter = { it.energy > 0 } })
            if (closest != null)
                moveTo(closest)
        }


    } else {
        if (repairClose()) return
        val targets = toRoom.find(FIND_MY_STRUCTURES)
                .filter { (it.structureType == STRUCTURE_EXTENSION || it.structureType == STRUCTURE_SPAWN || it.structureType == STRUCTURE_CONTAINER) }
                .filter { it.unsafeCast<EnergyContainer>().energy < it.unsafeCast<EnergyContainer>().energyCapacity }
        //choose near creeps as target, when they can take half of what we store and are not too far away
        val creepTargets = room.find(FIND_CREEPS).filter { c -> c.my && c.memory.role != Role.HARVESTER && c.freeStorage() >= carry.energy / 3 && c.pos.getRangeTo(pos) < c.freeStorage() / 2 }
        val closest: RoomObject? = pos.findClosestByPath<RoomObject>((targets + creepTargets).map { a -> a as RoomObject }.toTypedArray())
        if (closest != null) {
            if (closest is Creep) {
                // say("toCreep")
                if (transferEnergy(closest) == ERR_NOT_IN_RANGE)
                    moveTo(closest.pos)
            }
            if (closest is Structure) {
                //say("toStructure")
                if (transfer(closest, RESOURCE_ENERGY) == ERR_NOT_IN_RANGE)
                    moveTo(closest.pos)
            }
        } else {
            say(":(")
            console.log("Found no target from ${creepTargets.size} Creeps and ${targets.size} Structures = ${(targets + creepTargets).size}")
        }
    }
}