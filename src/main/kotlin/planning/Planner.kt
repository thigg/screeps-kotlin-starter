package planning

import screeps.api.Room

object Planner {

    fun run(room: Room){
        DumbPOIPlanner.planEnergyNet(room)
    }
}