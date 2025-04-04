package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.model.WorkShiftChangeReason
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Test work shift hours change sets
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class WorkShiftHoursChangeLogTestsIT: AbstractFunctionalTest() {
    val now: OffsetDateTime = OffsetDateTime.now()

    @Test
    fun testUpdateWorkShiftHoursChangelog() = createTestBuilder().use {
        val employeeId =it.manager.employees.createEmployee("01").id!!
        val changeSetId = UUID.randomUUID()
        val workShift = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employeeId,
            workShift = EmployeeWorkShift(
                employeeId = employeeId,
                date = LocalDate.now().plusDays(1).toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            ),
            changeSetId = changeSetId
        )
        val workShiftHours = it.manager.workShiftHours.listWorkShiftHours(employeeId = employeeId)[0]

        val updateData = workShiftHours.copy(actualHours = 5f)

        it.manager.workShiftHours.updateWorkShiftHours(
            id = updateData.id!!,
            workShiftChangeSetId = changeSetId,
            workShiftHours = updateData
        )
        val changeSet = it.manager.workShiftChangeSets.list(employeeId = employeeId).first()
        assertEquals(changeSetId, changeSet.id, "The changeSetId should be $changeSetId")
        assertEquals(2, changeSet.propertyEntries?.size, "There should be 2 property entries in the change set")

        val change = changeSet.propertyEntries!!.find { change -> change.workShiftHourId != null }
        assertNotNull(change, "There should be change with workShiftHourId")
        assertEquals(WorkShiftChangeReason.WORKSHIFTHOURS_UPDATED_ACTUALHOURS.toString(), change!!.reason.toString(), "The change reason should be WORKSHIFTHOURS_UPDATED_ACTUALHOURS")
        assertEquals(workShift.id, change.workShiftId, "Change should have workShiftId ${workShift.id}")
        assertNull(change.workEventId, "Change should not have a workEventId")
        assertEquals(workShiftHours.id, change.workShiftHourId, "WorkShiftHourId should be ${workShiftHours.id}")
        assertNotNull(change.newValue, "Change should have a new value")
        assertEquals(workShiftHours.actualHours.toString(), change.oldValue.toString(), "Change should have old value of ${workShiftHours.actualHours}")
        assertEquals(5f.toString(), change.newValue, "Change should have new value of '5f'")

        val changeSetId2 = UUID.randomUUID()
        it.manager.workShiftHours.updateWorkShiftHours(
            id = updateData.id,
            workShiftChangeSetId = changeSetId2,
            workShiftHours = updateData
        )

        val changeSets = it.manager.workShiftChangeSets.list(employeeId = employeeId)
        assertEquals(2, changeSets.size, "There should be 2 change sets")
    }

    @Test
    fun testCreateWorkShiftChangelogFail() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val changeSetId = UUID.randomUUID()
        val changeSetId2 = UUID.randomUUID()
        val workShift1 = it.manager.workShifts.createEmployeeWorkShift(employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
                date = LocalDate.now().plusDays(1).toString(),
                employeeId = employee.id,
                approved = false,
                costCentersFromEvents = emptyArray()
            ),
            changeSetId = changeSetId
        )

        it.manager.workShifts.createEmployeeWorkShift(employeeId = employee.id,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                startedAt = now.toString(),
                endedAt = now.plusHours(25).toString(),
                costCentersFromEvents = emptyArray()
            ),
            changeSetId = changeSetId2
        )

        val workShiftHours = it.manager.workShiftHours.listWorkShiftHours(employeeId = employee.id).find { hours ->
            hours.employeeWorkShiftId == workShift1.id
        }!!

        val updateData = workShiftHours.copy(actualHours = 5f)

        it.manager.workShiftHours.assertUpdateFail(
            workShiftHours.id!!,
            changeSetId2,
            updateData,
            400
        )

    }
}