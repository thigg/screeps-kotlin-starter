package tasker

import screeps.api.Game

object Tasker {

    var taskList : List<TaskerTask> = listOf()

    fun addTask(task: TaskerTask){
        taskList = (taskList + task).sortedBy { t->t.prio }
    }

    fun runTask(): Boolean {
        if (taskList.isEmpty()) {
            console.log("TaskList empty!!")
            return false
        }
        if(Game.cpu.getUsed() > Game.cpu.limit){
            console.log("Limit reached ${Game.cpu.getUsed()}/${Game.cpu.limit}")
            return false
        }
        val tasktorun = taskList[0]
        taskList = taskList.drop(1)
        val started = Game.cpu.getUsed()
        console.log("Running Task ${tasktorun.name} with prio ${tasktorun.prio} which was waiting for ${Game.time-tasktorun.added} @ ${Game.cpu.getUsed()}")

            tasktorun.action()

        console.log("Task ${tasktorun.name} took ${Game.cpu.getUsed()-started}cputime ")
        return true;
    }

    fun runUntilLimit(){
        while(runTask());
    }

}

data class TaskerTask (val name:String,val prio:Double,val action:()->Unit){
    val added: Int = Game.time
}