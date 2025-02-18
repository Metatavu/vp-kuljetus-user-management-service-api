package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.model.WorkShiftChangeReason
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
class WorkShiftChangeSetTestsIT: AbstractFunctionalTest() {
    val now: OffsetDateTime = OffsetDateTime.now()

    @Test
    fun testListChangeSets() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val changeSetId = UUID.randomUUID()
        val changeSetId2 = UUID.randomUUID()
        it.manager.workShifts.createEmployeeWorkShift(employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
            date = now.toLocalDate().toString(),
            employeeId = employee.id,
            approved = false,
            startedAt = now.toString(),
            endedAt = now.plusHours(25).toString()
            ),
            changeSetId = changeSetId
        )

        it.manager.workShifts.createEmployeeWorkShift(employeeId = employee.id,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                startedAt = now.toString(),
                endedAt = now.plusHours(25).toString()
            ),
            changeSetId = changeSetId2
        )

        val changeSets = it.manager.workShiftChangeSets.list(employeeId = employee.id)
        assertEquals(2, changeSets.size)
    }

    @Test
    fun testCreateWorkShiftChangelog() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val changeSetId = UUID.randomUUID()
        val shift = it.manager.workShifts.createEmployeeWorkShift(employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                startedAt = now.toString(),
                endedAt = now.plusHours(25).toString()
            ),
            changeSetId = changeSetId
        )
        val changeSet = it.manager.workShiftChangeSets.list(employeeId = employee.id).first()
        assertEquals(changeSetId, changeSet.id)
        assertEquals(1, changeSet.propertyEntries?.size)

        val change = changeSet.propertyEntries!!.first()
        assertEquals(WorkShiftChangeReason.WORKSHIFT_CREATED.toString(), change.reason.toString())
        assertNotNull(shift.id)
        assertEquals(shift.id, change.workShiftId)
        assertNull(change.workEventId)
        assertNull(change.workShiftHourId)
        assertNull(change.oldValue)
        assertNull(change.newValue)
    }
}