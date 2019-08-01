package starter


import planning.Planner
import screeps.api.*
import screeps.api.structures.StructureSpawn
import screeps.utils.asSequence
import screeps.utils.isEmpty
import screeps.utils.unsafe.delete
import screeps.utils.unsafe.jsObject
import tasker.Tasker
import tasker.TaskerTask

fun gameLoop() {
    val mainSpawn: StructureSpawn = Game.spawns.values.firstOrNull() ?: return

    //delete memories of creeps that have passed away
    Tasker.addTask(TaskerTask("housekeeping", 21.0) { houseKeeping(Game.creeps) })

    //make sure we have at least some creeps
    Tasker.addTask(TaskerTask("spawnCreeps", 1.0) { spawnCreeps(Game.creeps.values, mainSpawn) })

    Tasker.addTask(TaskerTask("planner", 10.0) {
       Planner.run(Game.spawns["Spawn1"]!!.room)
    })

    Tasker.addTask(TaskerTask("spawnBigCreeps", 15.0) {
        //spawn a big creep if we have plenty of energy
        for ((_, room) in Game.rooms) {
            if (room.energyAvailable >= 550) {
                mainSpawn.spawnCreep(
                        arrayOf(
                                WORK,
                                WORK,
                                WORK,
                                WORK,
                                CARRY,
                                MOVE,
                                MOVE
                        ),
                        "HarvesterBig_${Game.time}",
                        options {
                            memory = jsObject<CreepMemory> {
                                this.role = Role.HARVESTER
                            }
                        }
                )
                console.log("hurray!")
            }
        }
    })
    Tasker.addTask(TaskerTask("runCreeps", 20.0) {
        for ((_, creep) in Game.creeps) {
            when (creep.memory.role) {
                Role.HARVESTER -> creep.harvest()
                Role.BUILDER -> creep.build()
                Role.UPGRADER -> creep.upgrade(mainSpawn.room.controller!!)
                else -> creep.pause()
            }
        }
    })
    Tasker.runUntilLimit()

}

private fun spawnCreeps(
        creeps: Array<Creep>,
        spawn: StructureSpawn
) {

    val body = arrayOf<BodyPartConstant>(WORK, CARRY, MOVE)

    val role: Role = when {
        creeps.count { it.memory.role == Role.HARVESTER } < 2 -> Role.HARVESTER

        creeps.none { it.memory.role == Role.UPGRADER } -> Role.UPGRADER

        spawn.room.find(FIND_MY_CONSTRUCTION_SITES).isNotEmpty() &&
                creeps.count { it.memory.role == Role.BUILDER } < 2 -> Role.BUILDER

        else -> return
    }
    RoomVisual(spawn.room.name).text("Next: ${role.name}", spawn.pos.x, spawn.pos.y+1);

    if (spawn.room.energyAvailable < body.sumBy { BODYPART_COST[it]!! }) {
        return
    }



    val newName = "${role.name}_${Game.time}"
    val code = spawn.spawnCreep(body, newName, options {
        memory = jsObject<CreepMemory> { this.role = role }
    })

    when (code) {
        OK -> console.log("spawning $newName with body $body")
        ERR_BUSY, ERR_NOT_ENOUGH_ENERGY -> run { } // do nothing
        else -> console.log("unhandled error code $code")
    }
}

private fun houseKeeping(creeps: Record<String, Creep>) {
    if (Game.creeps.isEmpty()) return  // this is needed because Memory.creeps is undefined

    for ((creepName, _) in Memory.creeps) {
        if (creeps[creepName] == null) {
            console.log("deleting obsolete memory entry for creep $creepName")
            delete(Memory.creeps[creepName])
        }
    }
}
