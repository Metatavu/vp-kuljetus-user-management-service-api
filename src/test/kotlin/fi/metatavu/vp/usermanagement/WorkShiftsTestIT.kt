package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.WorkEvent
import fi.metatavu.vp.test.client.models.WorkEventType
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*

/**
 * Tests for WorkShifts and WorkShiftHours
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class WorkShiftsTestIT : AbstractFunctionalTest() {

    /**
     * Tests:
     * - WorkShift creation as the result of WorkEvents: (SHIFT_END, long BREAK or null) +
     * - Work shift listing and filtering, sorting +
     * - Work shift hours listing and filtering, sorting+
     */
    @Test
    fun testWorkShiftListing() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val employee2 = it.manager.employees.createEmployee("2")

        // employee 1 events
        val now = OffsetDateTime.now()
        createWorkEvent(it, employee1.id!!, WorkEventType.MEAT_CELLAR, now) // first event triggers new shift
        createWorkEvent(it, employee1.id, WorkEventType.BREAK, now.plusHours(1))    //Long break triggers new shift
        createWorkEvent(it, employee1.id, WorkEventType.BREWERY, now.plusHours(5))
        createWorkEvent(it, employee1.id, WorkEventType.SHIFT_END, now.plusHours(20))
        createWorkEvent(it, employee1.id, WorkEventType.OTHER_WORK, now.plusHours(25)) // ended shift triggers new shift

        // employee 2 events
        createWorkEvent(it, employee2.id!!, WorkEventType.MEAT_CELLAR, now)
        createWorkEvent(it, employee2.id, WorkEventType.BREAK, now.plusHours(1))
        createWorkEvent(it, employee2.id, WorkEventType.BREWERY, now.plusHours(5))

        val employee1AllWorkShifts = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee1.id
        )
        assertEquals(3, employee1AllWorkShifts.size)
        assertEquals(now.plusHours(25).toLocalDate().toString(), employee1AllWorkShifts[0].date)
        assertEquals(now.plusHours(5).toLocalDate().toString(), employee1AllWorkShifts[1].date)
        assertEquals(now.toLocalDate().toString(), employee1AllWorkShifts[2].date)

        val workShiftHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id
        )
        assertEquals(0, workShiftHours.size)    // No work shift hours created because there is not enough parameters

        val workShiftHoursParams = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id,
            employeeWorkShiftId = employee1AllWorkShifts[0].id,
            workType = WorkEventType.OTHER_WORK
        )
        assertEquals(1, workShiftHoursParams.size)

        val workShifthoursOneShift = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id,
        )
        assertEquals(1, workShifthoursOneShift.size)
    }

    /**
     * Tests:
     * - how dynamically work shift hours are calculated
     */
    @Test
    fun testWorkShiftHoursCalculation() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val now = OffsetDateTime.now()

        // first even created a shif
        val shift1Id =
            createWorkEvent(it, employee1.id!!, WorkEventType.MEAT_CELLAR, now).employeeWorkShiftId  // 1 hr
        createWorkEvent(it, employee1.id, WorkEventType.BREWERY, now.plusHours(1))      // 1 hr
        createWorkEvent(it, employee1.id, WorkEventType.BREAK, now.plusHours(2))        // Last event in the shift -> no time calculated

        //Long break triggers new shift
        val shift2Id = createWorkEvent(it, employee1.id, WorkEventType.SHIFT_END, now.plusHours(6)).employeeWorkShiftId    // One event -> no time calculated

        // ended shift triggers new shift
        val shift3Id = createWorkEvent(it, employee1.id, WorkEventType.OTHER_WORK, now.plusHours(7)).employeeWorkShiftId   // One event -> no time calculated

        val workShifts = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee1.id
        )
        assertEquals(3, workShifts.size)    // all 3 shifts belong to the same day -> no sorting

        val shift0 = workShifts.find { s -> s.id == shift3Id }!!
        val workShift3HoursOther = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id,
            employeeWorkShiftId = shift0.id,
            workType = WorkEventType.OTHER_WORK
        )
        assertEquals(1, workShift3HoursOther.size)
        assertEquals(0f, workShift3HoursOther[0].calculatedHours)

        val workShift2 = workShifts.find { s -> s.id == shift2Id }!!
        val workShift2HoursEND = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id,
            employeeWorkShiftId = workShift2.id,
            workType = WorkEventType.SHIFT_END
        )
        assertEquals(1, workShift2HoursEND.size)
        assertEquals(0f, workShift2HoursEND[0].calculatedHours)

        val shift3 = workShifts.find { s -> s.id == shift1Id }!!
        val workShift1HoursBreak = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id,
            employeeWorkShiftId = shift3.id,
            workType = WorkEventType.BREAK
        )
        assertEquals(1, workShift1HoursBreak.size)
        assertEquals(0f, workShift1HoursBreak[0].calculatedHours)
        val workShift1HoursBrew = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id,
            employeeWorkShiftId = shift3.id,
            workType = WorkEventType.BREWERY
        )
        assertEquals(1, workShift1HoursBrew.size)
        assertEquals(1f, workShift1HoursBrew[0].calculatedHours)

        val listWorkShiftHoursMeat = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id,
            employeeWorkShiftId = shift3.id,
            workType = WorkEventType.MEAT_CELLAR
        )
        assertEquals(1, listWorkShiftHoursMeat.size)
        assertEquals(1f, listWorkShiftHoursMeat[0].calculatedHours)

        // List all total work shift hours
        val alLTotalHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id
        )
        assertEquals(5, alLTotalHours.size)
    }

    /**
     * Tests:
     * - WorkShift time updates as the result of WorkEvents creation
     * - Work shift hours creation fails (and intead existing one is updated ) if houes exist for the same work shift and work type exists
     */
    @Test
    fun testWorkShiftCreate() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val now = OffsetDateTime.now()

        createWorkEvent(it, employee1.id!!, WorkEventType.MEAT_CELLAR, now)
        val workShift = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee1.id
        )[0]
        assertEquals(now.toLocalDate().toString(), workShift.date)

        // Work Shift time updates as the result of WorkEvents creation
        createWorkEvent(it, employee1.id, WorkEventType.BREWERY, now.minusDays(1))
        val updatedShift = it.manager.workShifts.findEmployeeWorkShift(employee1.id, workShift.id!!)
        assertEquals(now.minusDays(1).toLocalDate().toString(), updatedShift.date)

        // Work shift hours creation fails (and instead existing one is updated ) if same work shift and work type exists
        val workShiftHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id,
            employeeWorkShiftId = workShift.id,
            workType = WorkEventType.MEAT_CELLAR
        )
        assertEquals(1, workShiftHours.size)
        it.manager.workShiftHours.create(
            hours = workShiftHours[0].copy(actualHours = 3f)
        )
        val allHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id,
            employeeWorkShiftId = workShift.id,
            workType = WorkEventType.MEAT_CELLAR
        )
        assertEquals(1, allHours.size)
        assertEquals(3f, allHours[0].actualHours)

    }

    /**
     * Tests:
     *  - finding work shift by id                  +
     *  - finding work shift hours by id            +
     *  - work events get assigned to work shifts   +
     *  - work shift initially is not approved      +
     */
    @Test
    fun testWorkShiftFind() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val dayAgo = OffsetDateTime.now().minusDays(1)
        createWorkEvent(it, employee1.id!!, WorkEventType.MEAT_CELLAR, dayAgo)

        val workShifts = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee1.id
        )

        val foundWorkShift = it.manager.workShifts.findEmployeeWorkShift(employee1.id, workShifts[0].id!!)
        assertEquals(workShifts[0].id, foundWorkShift.id)
        assertEquals(workShifts[0].date, foundWorkShift.date)
        assertEquals(workShifts[0].employeeId, foundWorkShift.employeeId)
        assertEquals(false, foundWorkShift.approved)

        val workevents = it.manager.workEvents.listWorkEvents(
            employeeId = employee1.id
        )
        workevents.forEach { event ->
            assertEquals(foundWorkShift.id, event.employeeWorkShiftId)
        }

        val hours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id,
            employeeWorkShiftId = workShifts[0].id,
            workType = WorkEventType.MEAT_CELLAR
        )

        val foundWorkShiftHours = it.manager.workShiftHours.findWorkShiftHours(hours[0].id!!)
        assertEquals(hours[0].id, foundWorkShiftHours.id)
        assertEquals(hours[0].workEventType, foundWorkShiftHours.workEventType)
        assertEquals(hours[0].actualHours, foundWorkShiftHours.actualHours)

    }

    /**
     * Tests:
     * Work shifts:
     *  - approving work shift                                      +
     *  - work shift time is moved if work first event is updated   +
     * Work shift hours:
     *  - updating is impossible if work shift is apporved          +
     *  - employeeId, work ShiftId, work type cannot be updated     +
     *  - test that actual hours are updated by the supervisor      +
     */
    @Test
    fun testWorkShiftUpdate() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val now = OffsetDateTime.now()
        val workEvent = createWorkEvent(it, employee1.id!!, WorkEventType.MEAT_CELLAR, now)

        val workShift = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee1.id
        )[0]

        // Approving work shift
        val approved = it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee1.id,
            id = workShift.id!!,
            workShift = workShift.copy(approved = true)
        )
        assertEquals(true, approved.approved)

        // work shift time is moved if work first event is updated
        it.manager.workEvents.updateWorkEvent(
            employeeId = employee1.id,
            id = workEvent.id!!,
            workEvent = workEvent.copy(time = now.plusDays(1).toString())
        )
        val updatedShift = it.manager.workShifts.findEmployeeWorkShift(employee1.id, workShift.id!!)
        assertEquals(now.plusDays(1).toLocalDate().toString(), updatedShift.date)

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee1.id,
             id = workEvent.id,
            workEvent = workEvent.copy(time = now.minusDays(1).toString())
        )
        val updatedShift2 = it.manager.workShifts.findEmployeeWorkShift(employee1.id, workShift.id!!)
        assertEquals(now.minusDays(1).toLocalDate().toString(), updatedShift2.date)

        val workShiftHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee1.id,
            employeeWorkShiftId = workShift.id,
            workType = WorkEventType.MEAT_CELLAR
        )

        // updating is impossible if work shift is apporved
        it.manager.workShiftHours.assertUpdateFail(
            expectedStatus = 400,
            id = workShiftHours[0].id!!,
            workEvent = workShiftHours[0].copy(actualHours = 5f)
        )

        // employeeId, work ShiftId, work type cannot be updated
        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee1.id,
            id = workShift.id,
            workShift = workShift.copy(approved = false)
        )
        it.manager.workShiftHours.assertUpdateFail(
            expectedStatus = 400,
            id = workShiftHours[0].id!!,
            workEvent = workShiftHours[0].copy(employeeWorkShiftId = UUID.randomUUID())
        )

        // test that actual hours are updated by the supervisor
        val updatedHours = it.manager.workShiftHours.updateWorkShiftHours(
            id = workShiftHours[0].id!!,
            workEvent = workShiftHours[0].copy(actualHours = 5f)
        )
        assertEquals(5f, updatedHours.actualHours)
    }

    /**
     * Tests:
     *  - deleting work shift as the result of last work event removal  +
     *  - work shift time is moved if work first event is removed       +
     */
    @Test
    fun testWorkShiftDelete() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val now = OffsetDateTime.now()
        val workEvent1 = createWorkEvent(it, employee1.id!!, WorkEventType.MEAT_CELLAR, now)
        val workEvent2 = createWorkEvent(it, employee1.id!!, WorkEventType.MEAT_CELLAR, now.plusDays(1))

        val workShift = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee1.id
        )[0]

        // work shift time is moved if work first event is removed
        it.manager.workEvents.deleteWorkEvent(employee1.id, workEvent1.id!!)
        val updatedShift = it.manager.workShifts.findEmployeeWorkShift(employee1.id, workShift.id!!)
        assertEquals(now.plusDays(1).toLocalDate().toString(), updatedShift.date)

        // deleting work shift as the result of last work event removal
        it.manager.workEvents.deleteWorkEvent(employee1.id, workEvent2.id!!)
        val workShifts = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee1.id
        )
        assertEquals(0, workShifts.size)
    }

    private fun createWorkEvent(
        testBuilder: TestBuilder,
        employeeId: UUID,
        workEventType: WorkEventType,
        time: OffsetDateTime
    ): WorkEvent {
        return testBuilder.manager.workEvents.createWorkEvent(
            employeeId = employeeId,
            workEvent = WorkEvent(
                workEventType = workEventType,
                time = time.toString(),
                employeeId = employeeId,
                id = UUID.randomUUID()
            )
        )
    }
}