package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.AbsenceType
import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.test.client.models.PerDiemAllowanceType
import fi.metatavu.vp.test.client.models.WorkShiftChangeReason
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
class WorkShiftChangeLogTestsIT: AbstractFunctionalTest() {
    val now: OffsetDateTime = OffsetDateTime.now()

    @Test
    fun testListChangeSets() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val changeSetId = UUID.randomUUID()
        val changeSetId2 = UUID.randomUUID()
        it.manager.workShifts.createEmployeeWorkShift(employeeId = employee.id!!,
            workShift =
                EmployeeWorkShift(
                    date = now.toLocalDate().toString(),
                    employeeId = employee.id,
                    approved = false,
                    startedAt = now.toString(),
                    endedAt = now.plusHours(25).toString(),
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
                endedAt = now.plusHours(25).toString(),
                costCentersFromEvents = emptyArray()
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

    @Test
    fun testCreateWorkShiftChangelogFail() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val changeSetId = UUID.randomUUID()
        it.manager.workShifts.createEmployeeWorkShift(employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                startedAt = now.toString(),
                endedAt = now.plusHours(25).toString(),
                costCentersFromEvents = emptyArray()
            ),
            changeSetId = changeSetId
        )

        it.manager.workShifts.assertCreateFail(
            employeeId = employee.id,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                startedAt = now.toString(),
                endedAt = now.plusHours(25).toString(),
                costCentersFromEvents = emptyArray()
            ),
            changeSetId = changeSetId,
            expectedStatus = 400
        )

    }

    @Test
    fun testListChangeSetsFail() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        it.employee.workShiftChangeSets.assertListFail(employeeId = employee.id!!, expectedStatus = 403)
        it.driver1.workShiftChangeSets.assertListFail(employeeId = employee.id, expectedStatus = 403)
    }

    @Test
    fun testUpdateWorkShiftChangeLog() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val shift = it.manager.workShifts.createEmployeeWorkShift(employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                dayOffWorkAllowance = false,
                absence = AbsenceType.COMPENSATORY_LEAVE,
                perDiemAllowance = PerDiemAllowanceType.FULL,
                startedAt = now.toString(),
                endedAt = now.plusHours(25).toString(),
                costCentersFromEvents = emptyArray()
            )
        )

        val updatedShift = shift.copy(
            approved = true,
            dayOffWorkAllowance = true,
            absence = AbsenceType.VACATION,
            perDiemAllowance = PerDiemAllowanceType.PARTIAL,
            notes = "Notes 1"
        )

        val changeSetId = UUID.randomUUID()
        it.manager.workShifts.updateEmployeeWorkShift(
            id = updatedShift.id!!,
            employeeId = employee.id,
            workShift = updatedShift,
            changeSetId = changeSetId
        )

        val changeSet = it.manager.workShiftChangeSets.list(employeeId = employee.id).find { changeSet -> changeSet.id == changeSetId }
        assertEquals(5, changeSet!!.propertyEntries!!.size)

        val change1 = changeSet.propertyEntries!!.find { change ->
            change.reason == WorkShiftChangeReason.WORKSHIFT_UPDATED_APPROVED
        }

        assertNotNull(change1)
        assertEquals(updatedShift.id, change1!!.workShiftId)
        assertEquals("false", change1.oldValue)
        assertEquals("true", change1.newValue)

        val change2 = changeSet.propertyEntries.find { change ->
            change.reason == WorkShiftChangeReason.WORKSHIFT_UPDATED_DAYOFFWORKALLOWANCE
        }

        assertNotNull(change2)
        assertEquals(updatedShift.id, change2!!.workShiftId)
        assertEquals("false", change2.oldValue)
        assertEquals("true", change2.newValue)

        val change3 = changeSet.propertyEntries.find { change ->
            change.reason == WorkShiftChangeReason.WORKSHIFT_UPDATED_ABSENCE
        }

        assertNotNull(change3)
        assertEquals(updatedShift.id, change3!!.workShiftId)
        assertEquals(AbsenceType.COMPENSATORY_LEAVE.toString(), change3.oldValue)
        assertEquals(AbsenceType.VACATION.toString(), change3.newValue)

        val change4 = changeSet.propertyEntries.find { change ->
            change.reason == WorkShiftChangeReason.WORKSHIFT_UPDATED_PERDIEMALLOWANCE
        }

        assertNotNull(change4)
        assertEquals(updatedShift.id, change4!!.workShiftId)
        assertEquals(PerDiemAllowanceType.FULL.toString(), change4.oldValue)
        assertEquals(PerDiemAllowanceType.PARTIAL.toString(), change4.newValue)

        val change5 = changeSet.propertyEntries.find { change ->
            change.reason == WorkShiftChangeReason.WORKSHIFT_UPDATED_NOTES
        }

        assertNotNull(change5)
        assertEquals(updatedShift.id, change5!!.workShiftId)
        assertEquals("null", change5.oldValue)
        assertEquals("Notes 1", change5.newValue)

        it.manager.workShifts.updateEmployeeWorkShift(
            id = updatedShift.id,
            employeeId = employee.id,
            workShift = updatedShift,
            changeSetId = changeSetId
        )

        val changeSets = it.manager.workShiftChangeSets.list(employeeId = employee.id)
        assertEquals(2, changeSets.size)
        val changeSet2 = changeSets.find { it.id == changeSetId }
        assertEquals(5, changeSet2!!.propertyEntries!!.size)
    }
}