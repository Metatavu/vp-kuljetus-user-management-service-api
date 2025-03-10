package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.*
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Test work shift change sets
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class WorkEventChangeLogTestsIT: AbstractFunctionalTest() {
    val now: OffsetDateTime = OffsetDateTime.now()

    @Test
    fun testUpdateWorkEventChangeLog() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val shift = it.manager.workShifts.createEmployeeWorkShift(employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                startedAt = now.toString(),
                endedAt = now.plusHours(25).toString(),
                costCentersFromEvents = emptyArray()
            )
        )
        val time1 = now.toString()
        val time2 = now.plusMonths(1).toString()
        val event =  it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.MEAT_CELLAR,
                time = time1,
                employeeId = employee.id,
                id = UUID.randomUUID(),
                employeeWorkShiftId = shift.id
            )
        )

        val updated = event.copy(
            costCenter = "centre2",
            time = time2,
            workEventType = WorkEventType.DRIVE
        )
        val changeSetId = UUID.randomUUID()
        it.manager.workEvents.updateWorkEvent(
            employee.id,
            event.id!!,
            changeSetId,
            updated
        )

        val changeSet = it.manager.workShiftChangeSets.list(employeeId = employee.id).find { changeSet -> changeSet.id == changeSetId }
        assertEquals(3, changeSet!!.propertyEntries!!.size)

        val change1 = changeSet.propertyEntries!!.find { change ->
            change.reason == WorkShiftChangeReason.WORKEVENT_UPDATED_COSTCENTER
        }

        assertNotNull(change1)
        assertEquals(shift.id, change1!!.workShiftId)
        assertEquals(null, change1.oldValue)
        assertEquals("centre2", change1.newValue)

        val change2 = changeSet.propertyEntries.find { change ->
            change.reason == WorkShiftChangeReason.WORKEVENT_UPDATED_TIMESTAMP
        }

        assertNotNull(change2)
        assertEquals(shift.id, change2!!.workShiftId)
        assertEquals(time1.split("T").first(), change2.oldValue!!.split("T").first())
        assertEquals(time2.split("T").first(), change2.newValue!!.split("T").first())

        val change3 = changeSet.propertyEntries.find { change ->
            change.reason == WorkShiftChangeReason.WORKEVENT_UPDATED_TYPE
        }

        assertNotNull(change3)
        assertEquals(shift.id, change3!!.workShiftId)
        assertEquals(WorkEventType.MEAT_CELLAR.toString(), change3.oldValue)
        assertEquals(WorkEventType.DRIVE.toString(), change3.newValue)

        val changeSets = it.manager.workShiftChangeSets.list(employeeId = employee.id)
        assertEquals(2, changeSets.size)
        val changeSet2 = changeSets.find { it.id == changeSetId }
        assertEquals(3, changeSet2!!.propertyEntries!!.size)
    }
}