package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.TimeEntry
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*

/**
 * Work Types API tests
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class TimeEntryTestIT : AbstractFunctionalTest() {

    @Test
    fun testList() = createTestBuilder().use {
        val workType = it.manager.workTypes.createWorkType()

        val employee1 = it.manager.employees.createEmployee("1")
        val employee2 = it.manager.employees.createEmployee("2")

        val now = OffsetDateTime.now()
        it.manager.timeEntries.createTimeEntry(
            employee1.id!!, TimeEntry(
                workTypeId = workType.id!!,
                startTime = now.toString(),
                endTime = now.plusMinutes(1).toString(),
                employeeId = employee1.id,
                id = UUID.randomUUID()
            )
        )
        it.manager.timeEntries.createTimeEntry(
            employee1.id, TimeEntry(
                workTypeId = workType.id,
                startTime = now.plusDays(1).toString(),
                endTime = now.plusDays(2).toString(),
                employeeId = employee1.id,
                id = UUID.randomUUID()
            )
        )
        it.manager.timeEntries.createTimeEntry(
            employee2.id!!, TimeEntry(
                workTypeId = workType.id,
                startTime = now.toString(),
                endTime = now.plusMinutes(1).toString(),
                employeeId = employee2.id,
                id = UUID.randomUUID()
            )
        )

        val employee1Records = it.manager.timeEntries.listTimeEntries(employee1.id)
        assertEquals(2, employee1Records.size)
        val startFilter = it.manager.timeEntries.listTimeEntries(employee1.id, start = now.plusHours(1))
        assertEquals(1, startFilter.size)
        val endFilter = it.manager.timeEntries.listTimeEntries(employee1.id, end = now.plusHours(1))
        assertEquals(1, endFilter.size)

        val endFilter1 = it.manager.timeEntries.listTimeEntries(employee1.id, end = now.minusHours(1))
        assertEquals(0, endFilter1.size)
    }

    @Test
    fun testCreate() = createTestBuilder().use { tb ->
        val workType = tb.manager.workTypes.createWorkType()
        val employee1 = tb.manager.employees.createEmployee("1")

        val data = TimeEntry(
            workTypeId = workType.id!!,
            startTime = OffsetDateTime.now().toString(),
            employeeId = employee1.id!!
        )
        val created = tb.manager.timeEntries.createTimeEntry(
            employee1.id, data
        )

        assertNotNull(created.id)
        assertEquals(data.workTypeId, created.workTypeId)
        assertEquals(
            OffsetDateTime.parse(data.startTime).toEpochSecond(),
            OffsetDateTime.parse(created.startTime).toEpochSecond()
        )
        assertEquals(data.employeeId, created.employeeId)
    }

    @Test
    fun testCreateStartAfterEnd() = createTestBuilder().use {
        val workType = it.manager.workTypes.createWorkType()
        val employee1 = it.manager.employees.createEmployee("1")

        val now = OffsetDateTime.now()
        val created = it.manager.timeEntries.createTimeEntry(
            employee1.id!!, TimeEntry(
                workTypeId = workType.id!!,
                startTime = now.toString(),
                employeeId = employee1.id
            )
        )

        it.manager.timeEntries.assertCreateFail(
            employee1.id,
            created.copy(
                startTime = now.plusDays(1).toString(),
                endTime = now.plusHours(10).toString()
            ), 400
        )
    }

    @Test
    fun testCreateWithRunningTimer() = createTestBuilder().use {
        val workType = it.manager.workTypes.createWorkType()
        val employee1 = it.manager.employees.createEmployee("1")

        val now = OffsetDateTime.now()

        it.manager.timeEntries.createTimeEntry(
            employee1.id!!, TimeEntry(
                workTypeId = workType.id!!,
                startTime = now.toString(),
                employeeId = employee1.id
            )
        )

        it.manager.timeEntries.assertCreateFail(
            employeeId = employee1.id,
            timeEntry = TimeEntry(
                workTypeId = workType.id,
                startTime = now.toString(),
                employeeId = employee1.id
            ),
            expectedStatus = 400
        )
    }

    @Test
    fun testOverlapping() = createTestBuilder().use {
        //       1   2   3   4   5   6
        //          [|]         [|]
        //              [s] [e]            (case 1)
        //      [s]                 [e]    (case 2)
        //      [s]         [e]            (case 3)
        //              [s]         [e]    (case 4)

        // Intersecting time entries:
        //   1) Starts after new entry and ends before new entry ends
        //   2) Starts before new entry and ends after new entry ends
        //   3) Starts before new entry and ends before new entry ends
        //   4) Starts after new entry and ends after new entry ends

        val baseTime = OffsetDateTime.now().minusHours(8)
        val workType = it.manager.workTypes.createWorkType()
        val employee1 = it.manager.employees.createEmployee("1")

        val cases = arrayOf(
            Pair(3, 4),
            Pair(1, 6),
            Pair(1, 4),
            Pair(3, 6)
        )

        cases.forEach { case ->
            val existingEntry = it.manager.timeEntries.createTimeEntry(
                employee1.id!!, TimeEntry(
                    workTypeId = workType.id!!,
                    startTime = baseTime.plusHours(case.first.toLong()).toString(),
                    endTime = baseTime.plusHours(case.second.toLong()).toString(),
                    employeeId = employee1.id
                )
            )

            it.manager.timeEntries.assertCreateFail(
                employeeId = employee1.id,
                timeEntry = TimeEntry(
                    workTypeId = workType.id,
                    startTime = baseTime.plusHours(2).toString(),
                    endTime = baseTime.plusHours(5).toString(),
                    employeeId = employee1.id
                ),
                expectedStatus = 400
            )

            val activeEntry = it.manager.timeEntries.createTimeEntry(
                employeeId = employee1.id,
                timeEntry = TimeEntry(
                    workTypeId = workType.id,
                    startTime = baseTime.plusHours(2).toString(),
                    employeeId = employee1.id
                )
            )

            it.manager.timeEntries.assertUpdateFail(
                employeeId = employee1.id,
                id = activeEntry.id!!,
                timeEntry = activeEntry.copy(
                    endTime = baseTime.plusHours(5).toString()
                ),
                expectedStatus = 400
            )

            it.manager.timeEntries.deleteTimeEntry(employee1.id, existingEntry.id!!)
            it.manager.timeEntries.deleteTimeEntry(employee1.id, activeEntry.id)
        }
    }

    @Test
    fun testFind() = createTestBuilder().use {
        val workType = it.manager.workTypes.createWorkType()
        val employee1 = it.manager.employees.createEmployee("1")
        val created = it.manager.timeEntries.createTimeEntry(
            employee1.id!!, TimeEntry(
                workTypeId = workType.id!!,
                startTime = OffsetDateTime.now().toString(),
                endTime = OffsetDateTime.now().toString(),
                employeeId = employee1.id
            )
        )
        val found = it.manager.timeEntries.findTimeEntry(employee1.id, created.id!!)

        assertEquals(created.id, found.id)
        assertEquals(created.workTypeId, found.workTypeId)
        assertEquals(
            OffsetDateTime.parse(created.startTime).toEpochSecond(),
            OffsetDateTime.parse(found.startTime).toEpochSecond()
        )
        assertEquals(
            OffsetDateTime.parse(created.endTime!!).toEpochSecond(),
            OffsetDateTime.parse(found.endTime!!).toEpochSecond()
        )
        assertEquals(created.employeeId, found.employeeId)
    }

    @Test
    fun testUpdate() = createTestBuilder().use {
        val workType = it.manager.workTypes.createWorkType()
        val employee1 = it.manager.employees.createEmployee("1")
        val created = it.manager.timeEntries.createTimeEntry(
            employee1.id!!, TimeEntry(
                workTypeId = workType.id!!,
                startTime = OffsetDateTime.now().toString(),
                endTime = OffsetDateTime.now().toString(),
                employeeId = employee1.id
            )
        )
        val updateData = created.copy(
            workTypeId = workType.id,
            startTime = OffsetDateTime.now().toString(),
            endTime = OffsetDateTime.now().toString()
        )
        val updated = it.manager.timeEntries.updateTimeEntry(employee1.id, created.id!!, updateData)

        assertEquals(updateData.id, updated.id)
        assertEquals(updateData.workTypeId, updated.workTypeId)
        assertEquals(
            OffsetDateTime.parse(updateData.startTime).toEpochSecond(),
            OffsetDateTime.parse(updated.startTime).toEpochSecond()
        )
        assertEquals(
            OffsetDateTime.parse(updateData.endTime!!).toEpochSecond(),
            OffsetDateTime.parse(updated.endTime!!).toEpochSecond()
        )
        assertEquals(updateData.employeeId, updated.employeeId)
    }

    @Test
    fun testDelete() = createTestBuilder().use {
        val workType = it.manager.workTypes.createWorkType()
        val employee1 = it.manager.employees.createEmployee("1")
        val created = it.manager.timeEntries.createTimeEntry(
            employee1.id!!, TimeEntry(
                workTypeId = workType.id!!,
                startTime = OffsetDateTime.now().toString(),
                endTime = OffsetDateTime.now().toString(),
                employeeId = employee1.id
            )
        )
        it.manager.timeEntries.deleteTimeEntry(employee1.id, created.id!!)
        val all = it.manager.timeEntries.listTimeEntries(
            employee1.id,
            start = OffsetDateTime.now().minusDays(1),
            end = OffsetDateTime.now().plusDays(1)
        )
        assertEquals(0, all.size)
    }

}