package starter

import screeps.api.CreepMemory
import screeps.api.RoomPosition
import screeps.utils.memory.memory

data class MovementMem(var target: RoomPosition?, var lastUpd: Int)


var CreepMemory.refillEnergy: Boolean by memory { true }
var CreepMemory.role by memory(Role.UNASSIGNED)
var CreepMemory.move: MovementMem by memory(defaultValue = { MovementMem(null, -1) })