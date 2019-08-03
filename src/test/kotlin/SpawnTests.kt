import screeps.api.*
import starter.Role
import starter.planScreepBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


internal class SpawnTests {

    @Test
    fun bodyPartTest() {
        js("""
var _toglob = require('@screeps/common/lib/constants.js');
var key;
for (key in _toglob) {
  if (_toglob.hasOwnProperty(key)) {
    global[key] = _toglob[key];
  }
}           
        """)
        compare(arrayOf(WORK, WORK, CARRY, MOVE) as Array<BodyPartConstant>,    planScreepBody(Role.HARVESTER, 300, 300, mapOf(Role.HARVESTER to 2)))
        compare(arrayOf(WORK, WORK, CARRY, MOVE) as Array<BodyPartConstant>, planScreepBody(Role.HARVESTER, 300, 350, mapOf(Role.HARVESTER to 2)))
        compare(Array<BodyPartConstant>(0) { WORK }, planScreepBody(Role.HARVESTER, 300, 400, mapOf(Role.HARVESTER to 2)))
        compare(arrayOf(WORK, WORK, WORK, CARRY, MOVE) as Array<BodyPartConstant>, planScreepBody(Role.HARVESTER, 400, 400, mapOf(Role.HARVESTER to 2)))

    }

    fun compare(a1: Array<BodyPartConstant>,a2:Array<BodyPartConstant>){
        assertTrue(a1.contentDeepEquals(a2))
    }
}