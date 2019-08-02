package planning

import screeps.api.Room
import tasker.Tasker
import tasker.TaskerTask

object Planner {

    fun run(room: Room) {
        Tasker.addTask(TaskerTask("RoadPlanner", 18.1, { DumbPOIPlanner.planEnergyNet(room) }))
        Tasker.addTask(TaskerTask("ExtensionPlanner", 18.05, { DumbPOIPlanner.planEnergyStores(room) }))
        //Tasker.addTask(TaskerTask("ContainerPlanner", 18.15, { DumbPOIPlanner.plantContainersToSources(room) }))
    }
}