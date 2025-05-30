package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.*
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.*
import java.time.temporal.TemporalAdjusters

/**
 * Tests for Employee API
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class EmployeeTestIT : AbstractFunctionalTest() {

    @Test
    fun list() = createTestBuilder().use {
        val testEmployee = Employee(
            firstName = "Test",
            lastName = "Employee",
            type = EmployeeType.AH,
            office = Office.KOTKA,
            salaryGroup = SalaryGroup.DRIVER,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "001",
            regularWorkingHours = 8.0f,
            employeeNumber = "001"
        )
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "002"))
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "003"))
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "004", driverCardId = "002").copy(type = EmployeeType.AP, office = Office.RAUHA, salaryGroup = SalaryGroup.OFFICE))
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "005",firstName = "Timo", lastName = "Testaaja"))
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "006", firstName = "Mikko", lastName = "Testaaja"))
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "007", firstName = "Archived", lastName = "Archived", archivedAt = OffsetDateTime.now().toString()))

        val bySearch = it.manager.employees.listEmployees("Timo")
        assertEquals(1, bySearch.size)
        val bySearch2 = it.manager.employees.listEmployees("Testaaja")
        assertEquals(2, bySearch2.size)
        val bySearch3 = it.manager.employees.listEmployees("Test")
        assertEquals(5, bySearch3.size)

        val byType = it.manager.employees.listEmployees(type = EmployeeType.AH)
        assertEquals(4, byType.size)

        val byOffice = it.manager.employees.listEmployees(office = Office.KOTKA)
        assertEquals(4, byOffice.size)

        val bySalaryGroup = it.manager.employees.listEmployees(salaryGroup = SalaryGroup.OFFICE)
        assertEquals(1, bySalaryGroup.size)

        val byArchived = it.manager.employees.listEmployees(archived = true)
        assertEquals(1, byArchived.size)

        val byArchived2 = it.manager.employees.listEmployees()
        assertEquals(6, byArchived2.size)

        val paging = it.manager.employees.listEmployees(first = 0, max = 2)
        assertEquals(2, paging.size)
        val paging2 = it.manager.employees.listEmployees(first = 2, max = 2)
        assertEquals(2, paging2.size)
        val paging3 = it.manager.employees.listEmployees(first = 4, max = 2)
        assertEquals(2, paging3.size)

        val bySalaryPaging = it.manager.employees.listEmployees(salaryGroup = SalaryGroup.OFFICE, max = 1, first = 1)
        val bySalaryPaging2 = it.manager.employees.listEmployees(salaryGroup = SalaryGroup.OFFICE, max = 1, first = 0)
        assertEquals(0, bySalaryPaging.size)
        assertEquals(1, bySalaryPaging2.size)
    }

    @Test
    fun create() = createTestBuilder().use {
        val testEmployee = Employee(
            firstName = "Test",
            lastName = "Employee",
            type = EmployeeType.AH,
            office = Office.KOTKA,
            salaryGroup = SalaryGroup.DRIVER,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "001",
            regularWorkingHours = 8.0f,
            employeeNumber = "001",
            phoneNumber = "123456789",
            email = "text@example.com"
        )
        val created = it.manager.employees.createEmployee(testEmployee)
        assertNotNull(created.id)
        assertEquals(testEmployee.firstName, created.firstName)
        assertEquals(testEmployee.lastName, created.lastName)
        assertEquals(testEmployee.type, created.type)
        assertEquals(testEmployee.office, created.office)
        assertEquals(testEmployee.salaryGroup, created.salaryGroup)
        assertEquals(
            OffsetDateTime.parse(testEmployee.driverCardLastReadOut!!).toEpochSecond(),
            OffsetDateTime.parse(created.driverCardLastReadOut!!).toEpochSecond()
        )
        assertEquals(testEmployee.driverCardId, created.driverCardId)
        assertEquals(testEmployee.regularWorkingHours, created.regularWorkingHours)
        assertEquals(testEmployee.employeeNumber, created.employeeNumber)
        assertEquals(testEmployee.phoneNumber, created.phoneNumber)
        assertEquals(testEmployee.email, created.email)
    }

    @Test
    fun find() = createTestBuilder().use {
        val created = it.manager.employees.createEmployee(Employee(
            firstName = "Test",
            lastName = "Employee",
            type = EmployeeType.AH,
            office = Office.KOTKA,
            salaryGroup = SalaryGroup.DRIVER,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "001",
            regularWorkingHours = 8.0f,
            employeeNumber = "001",
            phoneNumber = "123456789",
            email = "text@example.com"
        ))

        val found = it.manager.employees.findEmployee(created.id!!)
        assertEquals(created.id, found.id)
        assertEquals(created.firstName, found.firstName)
        assertEquals(created.lastName, found.lastName)
        assertEquals(created.type, found.type)
        assertEquals(created.office, found.office)
        assertEquals(created.salaryGroup, found.salaryGroup)
        assertEquals(
            OffsetDateTime.parse(created.driverCardLastReadOut!!).toEpochSecond(),
            OffsetDateTime.parse(found.driverCardLastReadOut!!).toEpochSecond()
        )
        assertEquals(created.driverCardId, found.driverCardId)
        assertEquals(created.regularWorkingHours, found.regularWorkingHours)
    }

    @Test
    fun update() = createTestBuilder().use {
        val created = it.manager.employees.createEmployee(Employee(
            firstName = "Test",
            lastName = "Employee",
            type = EmployeeType.AH,
            office = Office.KOTKA,
            salaryGroup = SalaryGroup.DRIVER,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "001",
            regularWorkingHours = 8.0f,
            employeeNumber = "001"
        ))

        val updateData = created.copy(
            firstName = "Updated",
            lastName = "Updated",
            type = EmployeeType.AP,
            office = Office.RAUHA,
            salaryGroup = SalaryGroup.OFFICE,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "002",
            regularWorkingHours = 7.5f,
            phoneNumber = "123456789",
            email = "text@example.com"
        )

        val updated = it.manager.employees.updateEmployee(created.id!!, updateData)

        assertEquals(created.id, updated.id)
        assertEquals(updateData.firstName, updated.firstName)
        assertEquals(updateData.lastName, updated.lastName)
        assertEquals(updateData.type, updated.type)
        assertEquals(updateData.office, updated.office)
        assertEquals(updateData.salaryGroup, updated.salaryGroup)
        assertEquals(
            OffsetDateTime.parse(updateData.driverCardLastReadOut!!).toEpochSecond(),
            OffsetDateTime.parse(updated.driverCardLastReadOut!!).toEpochSecond()
        )
        assertEquals(updateData.driverCardId, updated.driverCardId)
        assertEquals(updateData.regularWorkingHours, updated.regularWorkingHours)
        assertEquals(updateData.employeeNumber, updated.employeeNumber)
        assertEquals(updateData.phoneNumber, updated.phoneNumber)
        assertEquals(updateData.email, updated.email)

        // Test that the unique usernames are still generated no matter the first+last name updates
        assertNotNull(it.manager.employees.createEmployee(created.copy(employeeNumber = "003")))
        assertNotNull(it.manager.employees.updateEmployee(created.id, created.copy(firstName = "updated1", lastName = "updated1")))
        assertNotNull(it.manager.employees.createEmployee(created.copy(employeeNumber = "004")))
        assertNotNull(it.manager.employees.createEmployee(created.copy(employeeNumber = "005", firstName = "updated1", lastName = "updated1")))
        assertEquals(5, it.manager.employees.listEmployees().size)

        // Tests that updates/creations of already existing employee numbers is not allowed
        it.manager.employees.assertCreateFail(created, 400)
        val anotherCreatedUser = it.manager.employees.createEmployee(created.copy(employeeNumber = "006"))
        it.manager.employees.assertUpdateFail(anotherCreatedUser.id!!, anotherCreatedUser.copy(employeeNumber = "003"),400)

    }

    @Test
    fun testDriverSalaryPeriodWorkHoursAggregation() = createTestBuilder().use {
        val now = OffsetDateTime.now()
        val time2 = if (now.dayOfWeek == DayOfWeek.SUNDAY) {
            now.plusDays(1)
        } else {
            now.minusDays(1)
        }

        val employee = it.manager.employees.createEmployee(Employee(
            firstName = "Test",
            lastName = "Employee",
            type = EmployeeType.AH,
            office = Office.KOTKA,
            salaryGroup = SalaryGroup.DRIVER,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "001",
            regularWorkingHours = 12.0f,
            employeeNumber = "001"
        ))

        val workShift1 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                startedAt = now.toString(),
                costCentersFromEvents = arrayOf()
            )
        )

        val workShift2 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id,
            workShift = EmployeeWorkShift(
                date = time2.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                startedAt = time2.toString(),
                costCentersFromEvents = arrayOf(),
                dayOffWorkAllowance = true,
                endedAt = time2.withHour(23).withMinute(59).withSecond(59).withNano(999999).toString()
            )
        )

        val workShiftHours1 = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift1.id!!,
        )

        val workShiftHours2 = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift2.id!!,
        )

        val paidHours1 = workShiftHours1.first { hours -> hours.workType == WorkType.PAID_WORK }
        val paidHours2 = workShiftHours2.first { hours -> hours.workType == WorkType.PAID_WORK }
        it.manager.workShiftHours.updateWorkShiftHours(
            id = paidHours1.id!!,
            workShiftHours = paidHours1.copy(
                actualHours = 12f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = paidHours2.id!!,
            workShiftHours = paidHours2.copy(
                actualHours = 9f
            )
        )

        val salaryPeriodTotalWorkHours = it.manager.employees.getSalaryPeriodTotalWorkHours(
            employeeId = employee.id,
            dateInSalaryPeriod = now
        )
        assertEquals(21f.toBigDecimal(), salaryPeriodTotalWorkHours.workingHours, "Working hours should be 21")
        assertEquals(0.toBigDecimal(), salaryPeriodTotalWorkHours.overTimeFull, "Overtime full should be 0")
        assertEquals(9f.toBigDecimal(), salaryPeriodTotalWorkHours.overTimeHalf, "Overtime half should be 9")

        val (salaryPeriodStart, salaryPeriodEnd) = getDriverSalaryPeriod(now)

        assertEquals(salaryPeriodStart.toString(), salaryPeriodTotalWorkHours.salaryPeriodStartDate, "Salary period start should be $salaryPeriodStart")
        assertEquals(salaryPeriodEnd.toString(), salaryPeriodTotalWorkHours.salaryPeriodEndDate, "Salary period end should be $salaryPeriodEnd")

        it.manager.employees.updateEmployee(
            employeeId = employee.id,
            employee = employee.copy(
                regularWorkingHours = 5.0f,
            )
        )

        val salaryPeriodTotalWorkHours2 = it.manager.employees.getSalaryPeriodTotalWorkHours(
            employeeId = employee.id,
            dateInSalaryPeriod = now
        )

        assertEquals(4f.toBigDecimal(), salaryPeriodTotalWorkHours2.overTimeFull, "Overtime full should be 4")
        assertEquals(12.toBigDecimal(), salaryPeriodTotalWorkHours2.overTimeHalf, "Overtime half should be 12")

    }

    @Test
    fun testSalaryPeriodWorkHoursAggregationForOfficeWorkers() = createTestBuilder().use {
        val now = OffsetDateTime.now().atZoneSameInstant(ZoneId.of("Europe/Helsinki")).toOffsetDateTime()
        val time2 = if (now.dayOfMonth == 16 || now.dayOfMonth == 1) {
            now.plusDays(1)
        } else {
            now.minusDays(1)
        }

        val employee = it.manager.employees.createEmployee(Employee(
            firstName = "Test",
            lastName = "Employee",
            type = EmployeeType.AH,
            office = Office.KOTKA,
            salaryGroup = SalaryGroup.OFFICE,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "001",
            regularWorkingHours = 10.0f,
            employeeNumber = "001"
        ))

        val workShift1 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                startedAt = now.toString(),
                costCentersFromEvents = arrayOf(),
                absence = AbsenceType.VACATION,
                perDiemAllowance = PerDiemAllowanceType.FULL
            )
        )

        val workShift2 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id,
            workShift = EmployeeWorkShift(
                date = time2.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                startedAt = time2.toString(),
                costCentersFromEvents = arrayOf(),
                dayOffWorkAllowance = true,
                endedAt = time2.withHour(23).withMinute(59).withSecond(59).withNano(999999).toString(),
                absence = AbsenceType.VACATION,
                perDiemAllowance = PerDiemAllowanceType.FULL
            )
        )

        val workShiftHours1 = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift1.id!!,
        )

        val workShiftHours2 = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift2.id!!,
        )

        val paidHours1 = workShiftHours1.first { hours -> hours.workType == WorkType.PAID_WORK }
        val paidHours2 = workShiftHours2.first { hours -> hours.workType == WorkType.PAID_WORK }

        val unpaidHours = workShiftHours1.first { hours -> hours.workType == WorkType.UNPAID }

        val standbyHours = workShiftHours1.first { hours -> hours.workType == WorkType.STANDBY }

        val eveningHours = workShiftHours1.first { hours -> hours.workType == WorkType.EVENING_ALLOWANCE }
        val nightHours = workShiftHours1.first { hours -> hours.workType == WorkType.NIGHT_ALLOWANCE }
        val holidayHours = workShiftHours1.first { hours -> hours.workType == WorkType.HOLIDAY_ALLOWANCE }
        val sickHours = workShiftHours1.first { hours -> hours.workType == WorkType.SICK_LEAVE }
        val trainingHours = workShiftHours1.first { hours -> hours.workType == WorkType.TRAINING }
        val breakHours = workShiftHours1.first { hours -> hours.workType == WorkType.BREAK }
        val frozenAllowanceHours = workShiftHours1.first { hours -> hours.workType == WorkType.FROZEN_ALLOWANCE }
        val jobSpecificAllowanceHours = workShiftHours1.first { hours -> hours.workType == WorkType.JOB_SPECIFIC_ALLOWANCE }

        it.manager.workShiftHours.updateWorkShiftHours(
            id = breakHours.id!!,
            workShiftHours = breakHours.copy(
                actualHours = 1f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = frozenAllowanceHours.id!!,
            workShiftHours = frozenAllowanceHours.copy(
                actualHours = 1f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = jobSpecificAllowanceHours.id!!,
            workShiftHours = jobSpecificAllowanceHours.copy(
                actualHours = 1f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = sickHours.id!!,
            workShiftHours = sickHours.copy(
                actualHours = 8f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = trainingHours.id!!,
            workShiftHours = trainingHours.copy(
                actualHours = 8f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = paidHours1.id!!,
            workShiftHours = paidHours1.copy(
                actualHours = 11f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = paidHours2.id!!,
            workShiftHours = paidHours2.copy(
                actualHours = 9f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = unpaidHours.id!!,
            workShiftHours = unpaidHours.copy(
                actualHours = 2f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = standbyHours.id!!,
            workShiftHours = standbyHours.copy(
                actualHours = 1f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = eveningHours.id!!,
            workShiftHours = eveningHours.copy(
                actualHours = 6f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = nightHours.id!!,
            workShiftHours = nightHours.copy(
                actualHours = 6f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = holidayHours.id!!,
            workShiftHours = holidayHours.copy(
                actualHours = 6f
            )
        )

        it.manager.workShifts.updateEmployeeWorkShift(
            id = workShift1.id!!,
            employeeId = employee.id!!,
            workShift = workShift1.copy(
                approved = true
            )
        )

        val salaryPeriodTotalWorkHours = it.manager.employees.getSalaryPeriodTotalWorkHours(
            employeeId = employee.id,
            dateInSalaryPeriod = now
        )

        assertEquals(20f.toBigDecimal(), salaryPeriodTotalWorkHours.workingHours, "Working hours should be 20")
        assertEquals(6f.toBigDecimal(), salaryPeriodTotalWorkHours.workingTime, "Working time should be 8")
        assertEquals(1f.toBigDecimal(), salaryPeriodTotalWorkHours.overTimeFull, "Overtime full should be 1")
        assertEquals(3f.toBigDecimal(), salaryPeriodTotalWorkHours.overTimeHalf, "Overtime half should be 3")
        assertEquals(1f.toBigDecimal(), salaryPeriodTotalWorkHours.waitingTime, "Waiting time should be 1")
        assertEquals(6f.toBigDecimal(), salaryPeriodTotalWorkHours.eveningWork, "Evening work should be 6")
        assertEquals(6f.toBigDecimal(), salaryPeriodTotalWorkHours.nightWork, "Night work should be 6")
        assertEquals(6f.toBigDecimal(), salaryPeriodTotalWorkHours.holiday, "Holiday work should be 6")
        assertEquals(9f.toBigDecimal(), salaryPeriodTotalWorkHours.dayOffBonus, "Day off bonus should be 9")
        assertEquals(2f.toBigDecimal(), salaryPeriodTotalWorkHours.unpaid, "Unpaid hours should be 2")
        assertEquals(8f.toBigDecimal(), salaryPeriodTotalWorkHours.sickHours, "Sick leave should be 8")
        assertEquals(8f.toBigDecimal(), salaryPeriodTotalWorkHours.trainingDuringWorkTime, "Training should be 8")
        assertEquals(1, salaryPeriodTotalWorkHours.amountOfApprovedWorkshifts)
        assertEquals(1f.toBigDecimal(), salaryPeriodTotalWorkHours.breakHours, "Breaks should be 1")
        assertEquals(1f.toBigDecimal(), salaryPeriodTotalWorkHours.frozenAllowance, "Frozen allowance should be 1")
        assertEquals(1f.toBigDecimal(), salaryPeriodTotalWorkHours.jobSpecificAllowance, "Job specific allowance should be 1")

        val (salaryPeriodStart, salaryPeriodEnd) = getOfficeWorkerSalaryPeriod(now)

        assertEquals(salaryPeriodStart.toString(), salaryPeriodTotalWorkHours.salaryPeriodStartDate, "Salary period start should be $salaryPeriodStart")
        assertEquals(salaryPeriodEnd.toString(), salaryPeriodTotalWorkHours.salaryPeriodEndDate, "Salary period end should be $salaryPeriodEnd")

        it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id,
            workShift = EmployeeWorkShift(
                date = if (time2.isBefore(now)) time2.toLocalDate().toString() else now.toLocalDate().toString(),
                employeeId = employee.id,
                approved = false,
                startedAt = if (time2.isBefore(now)) time2.toString() else now.toString(),
                costCentersFromEvents = arrayOf(),
                endedAt = if (time2.isBefore(now)) now.toString() else time2.toString(),
                dayOffWorkAllowance = true,
                absence = AbsenceType.COMPENSATORY_LEAVE,
                perDiemAllowance = PerDiemAllowanceType.PARTIAL
            )
        )

        val salaryPeriodTotalWorkHours2 = it.manager.employees.getSalaryPeriodTotalWorkHours(
            employeeId = employee.id,
            dateInSalaryPeriod = now
        )

        val time2AsDecimalHour = time2.hour +
                (time2.minute / 60.0) +
                (time2.second / 3600.0)
        val newDayOffBonus = 9f + (24-(time2AsDecimalHour + 1))

        assertEquals(newDayOffBonus.toBigDecimal(), salaryPeriodTotalWorkHours2.dayOffBonus, "Day off bonus should be $newDayOffBonus")
        assertEquals(8f.toBigDecimal(), salaryPeriodTotalWorkHours2.compensatoryLeave, "Compensatory leave should be 8")
        assertEquals(13.34.toBigDecimal(), salaryPeriodTotalWorkHours2.vacation, "Vacation should be 13.34")
        assertEquals(2.toBigDecimal(), salaryPeriodTotalWorkHours2.fullDailyAllowance, "Full daily allowance should be 2")
        assertEquals(1.toBigDecimal(), salaryPeriodTotalWorkHours2.partialDailyAllowance, "Partial daily allowance should be 1")

        it.manager.employees.updateEmployee(
            employeeId = employee.id,
            employee = employee.copy(
                regularWorkingHours = 60.0f,
            )
        )

        assertEquals(0.toBigDecimal(), salaryPeriodTotalWorkHours2.fillingHours, "Filling hours should be 0")

        val salaryPeriodTotalWorkHours3 = it.manager.employees.getSalaryPeriodTotalWorkHours(
            employeeId = employee.id,
            dateInSalaryPeriod = now
        )
        assertEquals(6.66.toBigDecimal(), salaryPeriodTotalWorkHours3.fillingHours, "Filling hours should be 6.66")
    }

    /**
     * Get the start and end dates of a salary period for office workers.
     * This is used by salary period working hours aggregation.
     *
     * @param dateTimeInSalaryPeriod a date that exists in a salary period
     */
    private fun getOfficeWorkerSalaryPeriod(
        dateTimeInSalaryPeriod: OffsetDateTime
    ): Pair<LocalDate, LocalDate> {
        val dateInSalaryPeriod = dateTimeInSalaryPeriod.toLocalDate()

        if (dateInSalaryPeriod.dayOfMonth < 16) {
            val start = LocalDate.of(
                dateInSalaryPeriod.year,
                dateInSalaryPeriod.monthValue,
                1,
            )

            val end = LocalDate.of(
                dateInSalaryPeriod.year,
                dateInSalaryPeriod.monthValue,
                15
            )
            return Pair(start, end)
        } else {
            val start = LocalDate.of(
                dateInSalaryPeriod.year,
                dateInSalaryPeriod.monthValue,
                16
            )

            val end = LocalDate.of(
                dateInSalaryPeriod.year,
                dateInSalaryPeriod.monthValue,
                dateInSalaryPeriod.month.length(dateInSalaryPeriod.isLeapYear)
            )

            return Pair(start, end)
        }
    }

    /**
     * Get the start and end dates of a salary period for drivers.
     * This is used by salary period working hours aggregation.
     *
     * @param dateTimeInSalaryPeriod a date that exists in a salary period
     */
    private fun getDriverSalaryPeriod(
        dateTimeInSalaryPeriod: OffsetDateTime
    ): Pair<LocalDate, LocalDate> {
        val dateInSalaryPeriod = dateTimeInSalaryPeriod.toLocalDate()
        /**
         * 7.1.2024 was Sunday
         */
        val workingTimePeriodStartDate = LocalDate.of(
            2024,
            1,
            7
        )

        val fullWeeks = (Duration.between(
            workingTimePeriodStartDate.atStartOfDay(),
            dateInSalaryPeriod.atStartOfDay()
        ).toDays() / 7)

        val isStartingWeek = fullWeeks % 2 == 0L

        if (isStartingWeek) {
            val start = dateInSalaryPeriod.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val end = dateInSalaryPeriod.plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            return Pair(start, end)
        } else {
            val start = dateInSalaryPeriod.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val end = dateInSalaryPeriod.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            return Pair(start, end)
        }

    }

}