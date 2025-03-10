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
        assertEquals(changeSetId, changeSet.id)
        assertEquals(2, changeSet.propertyEntries?.size)

        val change = changeSet.propertyEntries!!.find { change -> change.workShiftHourId != null }
        assertNotNull(change)
        assertEquals(WorkShiftChangeReason.WORKSHIFTHOURS_UPDATED_ACTUALHOURS.toString(), change!!.reason.toString())
        assertEquals(workShift.id, change.workShiftId)
        assertNull(change.workEventId)
        assertEquals(workShiftHours.id, change.workShiftHourId)
        assertNotNull(change.newValue)
        assertEquals(workShiftHours.actualHours.toString(), change.oldValue.toString())
        assertEquals(5f.toString(), change.newValue)

        val changeSetId2 = UUID.randomUUID()
        it.manager.workShiftHours.updateWorkShiftHours(
            id = updateData.id,
            workShiftChangeSetId = changeSetId2,
            workShiftHours = updateData
        )

        val changeSets = it.manager.workShiftChangeSets.list(employeeId = employeeId)
        assertEquals(2, changeSets.size)
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