package starter


import org.w3c.fetch.Body
import planning.Planner
import screeps.api.*
import screeps.api.structures.StructureSpawn
import screeps.utils.asSequence
import screeps.utils.isEmpty
import screeps.utils.unsafe.delete
import screeps.utils.unsafe.jsObject
import tasker.Tasker
import tasker.TaskerTask
import kotlin.math.max
import kotlin.math.min

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

    val rolecounts: Map<Role, Int> = Role.values().map { role -> role to creeps.count { it.memory.role == role } }.toMap()
    val role: Role = when {

        rolecounts.get(Role.HARVESTER)!! < spawn.room.find(FIND_SOURCES).size -> Role.HARVESTER

        rolecounts[Role.UPGRADER] == 0 -> Role.UPGRADER

        spawn.room.find(FIND_MY_CONSTRUCTION_SITES).isNotEmpty() &&
                rolecounts[Role.BUILDER]!! < 2 -> Role.BUILDER
        //fill up harvester
        creeps.sumBy { if (it.memory.role == Role.HARVESTER) it.body.count { it == WORK } else 0 } < spawn.room.find(FIND_SOURCES).size * 5 -> Role.HARVESTER

        else -> return
    }
    RoomVisual(spawn.room.name).text("Next: ${role.name}", spawn.pos.x, spawn.pos.y + 1);

    val body: Array<BodyPartConstant> = when (role) {
        Role.BUILDER -> if (spawn.room.energyAvailable >= 300) arrayOf<BodyPartConstant>(WORK, CARRY, CARRY, CARRY, MOVE) else arrayOf<BodyPartConstant>(WORK, CARRY, MOVE)
        Role.UNASSIGNED -> arrayOf<BodyPartConstant>(WORK, CARRY, MOVE)
        Role.HARVESTER -> if (spawn.room.energyAvailable >= 300 || rolecounts[Role.HARVESTER]!! >= 2) {
            //max 5 work parts, but if we have already two harvesters, build the biggest possible!
            if (min(600, spawn.room.energyCapacityAvailable - (spawn.room.energyCapacityAvailable % 100)) <= spawn.room.energyAvailable)
                Array(min(5, spawn.room.energyCapacityAvailable / 100 - 1)) { WORK } + arrayOf(CARRY, MOVE)
            else arrayOf()
        } else arrayOf<BodyPartConstant>(WORK, CARRY, MOVE)
        Role.UPGRADER -> if (spawn.room.energyAvailable >= 300) Array(min(5, spawn.room.energyAvailable / 100 - 1)) { WORK } + arrayOf(CARRY, MOVE) else arrayOf<BodyPartConstant>(WORK, CARRY, MOVE)
        Role.TRANSPORTER -> if (spawn.room.energyAvailable >= 300) arrayOf<BodyPartConstant>(CARRY, CARRY, CARRY, MOVE, MOVE, MOVE) else arrayOf<BodyPartConstant>(CARRY, CARRY, MOVE)
    } as Array<BodyPartConstant>

    if (body.isEmpty()) return
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
