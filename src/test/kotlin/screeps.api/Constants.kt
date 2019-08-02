package screeps.api


open class Constant<T>(open val value:T)
open class BodyPartConstant(override val value:String): Constant<String>(value)
val WORK = BodyPartConstant("work")
val CARRY = BodyPartConstant("carry")
val MOVE = BodyPartConstant("move")