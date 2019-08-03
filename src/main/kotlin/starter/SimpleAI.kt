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

    Tasker.addTask(TaskerTask("runCreeps", 20.0) {
        for ((_, creep) in Game.creeps) {
            when (creep.memory.role) {
                Role.HARVESTER -> creep.harvest()
                Role.BUILDER -> creep.build()
                Role.UPGRADER -> creep.upgrade(mainSpawn.room.controller!!)
                Role.TRANSPORTER -> creep.beTransporter()
                else -> run {}
            }
        }
    })
    Tasker.runUntilLimit()

}

private fun spawnCreeps(
        creeps: Array<Creep>,
        spawn: StructureSpawn
) {
    if (spawn.spawning != null) return
    val rolecounts: Map<Role, Int> = Role.values().map { role -> role to creeps.count { it.memory.role == role } }.toMap()
    //rolecounts.forEach { console.log("${it.key}->${it.value}") }
    val role: Role = when {

        rolecounts.get(Role.HARVESTER)!! < spawn.room.find(FIND_SOURCES).size -> Role.HARVESTER

        rolecounts.get(Role.TRANSPORTER)!! < 1 -> Role.TRANSPORTER

        rolecounts[Role.UPGRADER] == 0 -> Role.UPGRADER

        spawn.room.find(FIND_MY_CONSTRUCTION_SITES).isNotEmpty() &&
                rolecounts[Role.BUILDER]!! < 2 -> Role.BUILDER
        //fill up harvester
        creeps.sumBy { if (it.memory.role == Role.HARVESTER) it.body.count { it.type == WORK } else 0 } < spawn.room.find(FIND_SOURCES).size * 5 -> {
            val workBodys = creeps.sumBy { if (it.memory.role == Role.HARVESTER) it.body.count { it.type == WORK } else 0 }
            val req = spawn.room.find(FIND_SOURCES).size * 5
            console.log("$workBodys from Harvesters exist, require $req, therefore build harvester")
            Role.HARVESTER
        }

        rolecounts[Role.UPGRADER]!! < 2 -> Role.UPGRADER

        else -> {//kill weak creeps HAHAHAHHA
            if(spawn.room.energyAvailable <= 300)return
            val assaultable = spawn.room.find(FIND_CREEPS, options { filter= {it.body.size == 3} })
            if(assaultable.isEmpty()) return
            assaultable[0].suicide()
            return

        }
    }
    RoomVisual(spawn.room.name).text("Next: ${role.name}", spawn.pos.x.toDouble(), (spawn.pos.y + 1).toDouble());

    val eAV = spawn.room.energyAvailable
    val eCap = spawn.room.energyCapacityAvailable
    val body: Array<BodyPartConstant> = planScreepBody(role, eAV, eCap, rolecounts)

    if (body.isEmpty()) return
    if (eAV < body.sumBy { BODYPART_COST[it]!! }) {
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

fun planScreepBody(role: Role, eAV: Int, eCap: Int, rolecounts: Map<Role, Int>): Array<BodyPartConstant> {
    return when (role) {
        Role.BUILDER -> if (eAV >= 300) arrayOf<BodyPartConstant>(WORK, CARRY, CARRY, CARRY, MOVE) else arrayOf<BodyPartConstant>(WORK, CARRY, MOVE)
        Role.UNASSIGNED -> arrayOf<BodyPartConstant>()
        Role.HARVESTER ->
            if (rolecounts[Role.HARVESTER]!! < 2) arrayOf<BodyPartConstant>(WORK, CARRY, MOVE)
            else if (eAV >= 300 || rolecounts[Role.HARVESTER]!! >= 2) {
                //max 5 work parts, but if we have already two harvesters, build the biggest possible with a max of 5 works!
                if (min(600, eCap - (eCap % 100)) <= eAV)
                    Array(min(5, eCap / 100 - 1)) { WORK } + arrayOf(CARRY, MOVE)
                else arrayOf()
            } else arrayOf()
        Role.UPGRADER -> if (eAV >= 300) Array(min(5, eAV / 100 - 1)) { WORK } + arrayOf(CARRY, MOVE) else arrayOf<BodyPartConstant>(WORK, CARRY, MOVE)
        Role.TRANSPORTER -> if (eAV >= 300) arrayOf<BodyPartConstant>(CARRY, CARRY, CARRY, MOVE, MOVE, MOVE) else arrayOf<BodyPartConstant>(CARRY, CARRY, MOVE)
    } as Array<BodyPartConstant>
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
