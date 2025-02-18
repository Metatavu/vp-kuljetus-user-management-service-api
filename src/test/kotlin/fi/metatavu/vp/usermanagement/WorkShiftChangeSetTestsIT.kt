package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals

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
            changeSetId = changeSetId
        )

        val changeSets = it.manager.workShiftChangeSets.list(employeeId = employee.id)
        assertEquals(2, changeSets.size)
    }
}