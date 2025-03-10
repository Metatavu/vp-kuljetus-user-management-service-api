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
        assertEquals(2, changeSets.size, "There should be 2 change sets")
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
        assertEquals(changeSetId, changeSet.id, "Change set id should be $changeSetId")
        assertEquals(1, changeSet.propertyEntries?.size, "Change set should contain 1 property entry")

        val change = changeSet.propertyEntries!!.first()
        assertEquals(WorkShiftChangeReason.WORKSHIFT_CREATED.toString(), change.reason.toString(), "Change reason should be WORKSHIFT_CREATED")
        assertNotNull(shift.id, "Shift should have an id")
        assertEquals(shift.id, change.workShiftId, "Change workShiftId should be ${shift.id}")
        assertNull(change.workEventId, "This change should not have a workEventId")
        assertNull(change.workShiftHourId,"This change should not have a workShiftHourId")
        assertNull(change.oldValue, "This change should not have an oldValue")
        assertNull(change.newValue, "This change should not have a newValue")
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
        assertEquals(5, changeSet!!.propertyEntries!!.size, "Change set should have 5 property entries")

        val change1 = changeSet.propertyEntries!!.find { change ->
            change.reason == WorkShiftChangeReason.WORKSHIFT_UPDATED_APPROVED
        }

        assertNotNull(change1, "Change set should contain change of type WORKSHIFT_UPPDATED_APPROVED")
        assertEquals(updatedShift.id, change1!!.workShiftId, "Change workShiftId should be ${updatedShift.id}")
        assertEquals("false", change1.oldValue, "Old value should be 'false'")
        assertEquals("true", change1.newValue, "Old value should be 'true'")

        val change2 = changeSet.propertyEntries.find { change ->
            change.reason == WorkShiftChangeReason.WORKSHIFT_UPDATED_DAYOFFWORKALLOWANCE
        }

        assertNotNull(change2, "Change set should contain change of type WORKSHIFT_UPDATED_DAYOFFWORKALLOWANCE")
        assertEquals(updatedShift.id, change2!!.workShiftId, "Change workShiftId should be ${updatedShift.id}")
        assertEquals("false", change2.oldValue, "Old value should be 'false'")
        assertEquals("true", change2.newValue, "New value should be 'true'")

        val change3 = changeSet.propertyEntries.find { change ->
            change.reason == WorkShiftChangeReason.WORKSHIFT_UPDATED_ABSENCE
        }

        assertNotNull(change3, "Change set should contain change of type WORKSHIFT_UPDATED_ABSENCE")
        assertEquals(updatedShift.id, change3!!.workShiftId, "Change workShiftId should be ${updatedShift.id}")
        assertEquals(AbsenceType.COMPENSATORY_LEAVE.toString(), change3.oldValue, "Old value should be COMPENSATORY_LEAVE")
        assertEquals(AbsenceType.VACATION.toString(), change3.newValue, "New value should be VACATION")

        val change4 = changeSet.propertyEntries.find { change ->
            change.reason == WorkShiftChangeReason.WORKSHIFT_UPDATED_PERDIEMALLOWANCE
        }

        assertNotNull(change4, "Change set should contain change of type WORKSHIFT_UPDATED_PERDIEMALLOWANCE")
        assertEquals(updatedShift.id, change4!!.workShiftId, "Change workShiftId should be ${updatedShift.id}")
        assertEquals(PerDiemAllowanceType.FULL.toString(), change4.oldValue, "Old value should be FULL")
        assertEquals(PerDiemAllowanceType.PARTIAL.toString(), change4.newValue, "New value should be PARTIAL")

        val change5 = changeSet.propertyEntries.find { change ->
            change.reason == WorkShiftChangeReason.WORKSHIFT_UPDATED_NOTES
        }

        assertNotNull(change5, "Change set should contain change of type WORKSHIFT_UPDATED_NOTES")
        assertEquals(updatedShift.id, change5!!.workShiftId, "Change workShiftId should be ${updatedShift.id}")
        assertEquals("null", change5.oldValue, "Old value should be 'null'")
        assertEquals("Notes 1", change5.newValue, "New value should be 'Notes 1'")

        it.manager.workShifts.updateEmployeeWorkShift(
            id = updatedShift.id,
            employeeId = employee.id,
            workShift = updatedShift,
            changeSetId = changeSetId
        )

        val changeSets = it.manager.workShiftChangeSets.list(employeeId = employee.id)
        assertEquals(2, changeSets.size, "There should be 2 change sets")
        val changeSet2 = changeSets.find { it.id == changeSetId }
        assertEquals(5, changeSet2!!.propertyEntries!!.size, "The second change set should have 5 property entries")
    }
}