package fi.metatavu.vp.usermanagement.employees.utilities

import fi.metatavu.vp.usermanagement.model.AbsenceType
import fi.metatavu.vp.usermanagement.model.PerDiemAllowanceType
import fi.metatavu.vp.usermanagement.model.SalaryPeriodTotalWorkHours
import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.math.BigDecimal
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.*

@ApplicationScoped
class SalaryPeriodUtils {
    @Inject
    lateinit var workShiftController: WorkShiftController

    @Inject
    lateinit var workShiftHoursController: WorkShiftHoursController

    /**
     * Calculates total values for a salary period for a given employee.
     * This is used by the UI views that show the salary period summary.
     *
     * @param employeeId
     * @param regularWorkingHours
     * @param isDriver
     * @param dateInSalaryPeriod a date that is within the salary period being requested
     */
    suspend fun aggregateSalaryPeriodTotalWorkHours(
        employeeId: UUID,
        regularWorkingHoursFromAttributes: Float?,
        isDriver: Boolean,
        dateInSalaryPeriod: OffsetDateTime
    ): SalaryPeriodTotalWorkHours {
        val (startDate, endDate) = if (isDriver) {
            getDriverSalaryPeriod(dateInSalaryPeriod)
        } else {
            getOfficeWorkerSalaryPeriod(dateInSalaryPeriod)
        }

        val workShifts = workShiftController.listEmployeeWorkShifts(
            employeeId = employeeId,
            dateAfter = startDate,
            dateBefore = endDate,
            startedBefore = null,
            startedAfter = null
        ).first

        val unpaidHours = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.UNPAID
        )

        val regularWorkingHours = if (regularWorkingHoursFromAttributes != null) {
            regularWorkingHoursFromAttributes - unpaidHours.toFloat()
        } else {
            null
        }

        val workingHours = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.PAID_WORK
        )

        val trainingHours = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.TRAINING
        )

        val workingTime = if (regularWorkingHours != null) getRegularWorkingTime(
            workShifts = workShifts,
            regularWorkingHours = regularWorkingHours
        ) else BigDecimal.valueOf(0)

        val vacationHours = calculateTotalWorkHoursByAbsenceType(
            workShifts = workShifts,
            absenceType = AbsenceType.VACATION
        )

        val compensatoryLeaveHours = calculateTotalWorkHoursByAbsenceType(
            workShifts = workShifts,
            absenceType = AbsenceType.COMPENSATORY_LEAVE
        )

        val overTimeHalf = if (isDriver) {
            if (regularWorkingHours != null) calculateDriverOverTime(
                regularWorkingHours = workingTime,
                full = false,
                workingHours = workingHours,
                trainingHours = trainingHours,
                vacationHours = vacationHours,
                compensatoryLeaveHours = compensatoryLeaveHours
            ) else BigDecimal.valueOf(0)
        } else {
            calculateOfficeAndTerminalWorkerOverTime(
                workShifts = workShifts,
                full = false
            )
        }

        val overTimeFull = if (isDriver) {
            if (regularWorkingHours != null) calculateDriverOverTime(
                regularWorkingHours = workingTime,
                full = true,
                workingHours = workingHours,
                trainingHours = trainingHours,
                vacationHours = vacationHours,
                compensatoryLeaveHours = compensatoryLeaveHours
            ) else BigDecimal.valueOf(0)
        } else {
            calculateOfficeAndTerminalWorkerOverTime(
                workShifts = workShifts,
                full = true
            )
        }

        val standbyHours = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.STANDBY
        )

        val eveningHours = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.EVENING_ALLOWANCE
        )

        val nightHours = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.NIGHT_ALLOWANCE
        )

        val holidayHours = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.HOLIDAY_ALLOWANCE
        )

        val dayOffBonusHours = calculateDayOffBonus(
            workShifts = workShifts
        )

        val sickHours = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.SICK_LEAVE
        )

        val officialDutyHours = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.OFFICIAL_DUTIES
        )

        val frozenAllowance = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.FROZEN_ALLOWANCE
        )

        val jobSpecificAllowance = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.JOB_SPECIFIC_ALLOWANCE
        )

        val breakHours = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.BREAK
        )

        val fillingHours = if (workingTime != BigDecimal.valueOf(0) ) { calculateFillingHours(
            regularWorkingHours = workingTime,
            workingHours = workingHours,
            sickHours = sickHours,
            vacationHours = vacationHours,
            compensatoryLeaveHours = compensatoryLeaveHours,
            officialDutyHours = officialDutyHours
        ) } else BigDecimal.valueOf(0)

        val partialDailyAllowance = workShifts.filter { shift ->
            shift.perDiemAllowance == PerDiemAllowanceType.PARTIAL
        }.size

        val fullDailyAllowance = workShifts.filter { shift ->
            shift.perDiemAllowance == PerDiemAllowanceType.FULL
        }.size

        val amountOfApprovedWorkShifts = workShifts.count {
            it.approved
        }

        return SalaryPeriodTotalWorkHours(
            workingHours = workingHours,
            workingTime = workingTime,
            overTimeHalf = overTimeHalf,
            overTimeFull = overTimeFull,
            waitingTime = standbyHours,
            eveningWork = eveningHours,
            nightWork = nightHours,
            holiday = holidayHours,
            dayOffBonus = dayOffBonusHours,
            vacation = vacationHours,
            unpaid = unpaidHours,
            compensatoryLeave = compensatoryLeaveHours,
            sickHours = sickHours,
            responsibilities = officialDutyHours,
            trainingDuringWorkTime = trainingHours,
            fillingHours = fillingHours,
            partialDailyAllowance = partialDailyAllowance.toBigDecimal(),
            fullDailyAllowance = fullDailyAllowance.toBigDecimal(),
            salaryPeriodStartDate = startDate,
            salaryPeriodEndDate = endDate,
            frozenAllowance = frozenAllowance,
            jobSpecificAllowance = jobSpecificAllowance,
            breakHours = breakHours,
            amountOfApprovedWorkshifts = amountOfApprovedWorkShifts
        )
    }

    /**
     * Calculate filling hours.
     * This is used by salary period working hours aggregation.
     *
     * @param regularWorkingHours
     * @param workingHours
     * @param sickHours
     * @param vacationHours
     * @param compensatoryLeaveHours
     * @param officialDutyHours
     */
     suspend fun calculateFillingHours(
        regularWorkingHours: BigDecimal,
        workingHours: BigDecimal,
        sickHours: BigDecimal,
        vacationHours: BigDecimal,
        compensatoryLeaveHours: BigDecimal,
        officialDutyHours: BigDecimal
    ): BigDecimal {
        val totalPaidHours = workingHours + sickHours + officialDutyHours

        if (totalPaidHours >= regularWorkingHours) return BigDecimal.valueOf(0)

        val totalAbsenceHours = vacationHours + compensatoryLeaveHours

        val fillingHours = regularWorkingHours - totalPaidHours - totalAbsenceHours

        if (fillingHours < BigDecimal.valueOf(0)) return BigDecimal.valueOf(0)

        return fillingHours
    }

    /**
     * Calculate the day off bonus hours for a list of work shifts.
     * This is used by salary period working hours aggregation.
     *
     * @param workShifts
     */
    suspend fun calculateDayOffBonus(
        workShifts: List<WorkShiftEntity>
    ): BigDecimal {
        var totalHours = 0.0
        workShifts
            .filter { shift -> shift.dayOffWorkAllowance == true }
            .forEach { shift ->
                val startedAtOffsetDateTime = shift.startedAt
                val startedAt = startedAtOffsetDateTime?.toLocalDate() ?: shift.date
                val endedAt = shift.endedAt?.toLocalDate() ?: LocalDate.now()

                if (startedAt == endedAt || startedAtOffsetDateTime == null) {
                    workShiftHoursController.listWorkShiftHours(
                        workShiftFilter = shift,
                        workType = WorkType.PAID_WORK
                    ).first.forEach {
                        totalHours += it.actualHours ?: it.calculatedHours ?: 0f
                    }
                } else {
                    val finlandZone = ZoneId.of("Europe/Helsinki")
                    val startedAtZoned = startedAtOffsetDateTime.atZoneSameInstant(finlandZone)
                    val hour = startedAtZoned.hour
                    val minute = startedAtZoned.minute
                    val second = startedAtZoned.second

                    val timeAsDecimalHour = hour +
                            (minute / 60.0) +
                            (second / 3600.0)

                    val hoursOnFirstDay = 24 - (timeAsDecimalHour + 1)
                    totalHours += hoursOnFirstDay
                }
            }

        return totalHours.toBigDecimal()
    }

    /**
     * Calculate office and terminal worker overtime hours for a list of work shifts.
     * This is used by salary period working hours aggregation.
     *
     * @param workShifts
     * @param full if true returns full compensation overtime, otherwise half compensation
     */
    private suspend fun calculateOfficeAndTerminalWorkerOverTime(
        workShifts: List<WorkShiftEntity>,
        full: Boolean
    ): BigDecimal {
        var overTimeHalf = 0f
        var overTimeFull = 0f
        workShifts.forEach { shift ->
            workShiftHoursController.listWorkShiftHours(
                workShiftFilter = shift,
                workType = WorkType.PAID_WORK
            ).first.forEach { hours ->
                val dailyWorkHours = hours.actualHours ?: hours.calculatedHours ?: 0f
                val regularWorkTimeLimit = 8
                val halfOverTimeLimit = 2

                if (dailyWorkHours > regularWorkTimeLimit) {
                    val overTimeHours = dailyWorkHours - regularWorkTimeLimit
                    if (overTimeHours <= halfOverTimeLimit) {
                        overTimeHalf += overTimeHours
                    } else {
                        overTimeHalf += halfOverTimeLimit
                        overTimeFull += overTimeHours - halfOverTimeLimit
                    }
                }
            }
        }

        return if (full) {
            overTimeFull.toBigDecimal()
        } else {
            overTimeHalf.toBigDecimal()
        }
    }

    /**
     * Calculate driver overtime hours for a list of work shifts.
     * This is used by salary period working hours aggregation.
     * Other working hour parameters must be calculated from the same list.
     *
     * @param regularWorkingHours
     * @param full if true returns full compensation overtime, otherwise half compensation
     * @param workingHours
     * @param trainingHours
     * @param vacationHours
     * @param compensatoryLeaveHours
     */
    private suspend fun calculateDriverOverTime(
        regularWorkingHours: BigDecimal,
        full: Boolean,
        workingHours: BigDecimal,
        trainingHours: BigDecimal,
        vacationHours: BigDecimal,
        compensatoryLeaveHours: BigDecimal
    ): BigDecimal {
        val overtimeFullLimit = if (vacationHours > BigDecimal.valueOf(40)) BigDecimal.valueOf(10) else BigDecimal.valueOf(12)
        val paidWorkHoursFromWorkTypes = workingHours - trainingHours
        val paidVacationAndCompensatoryHours = vacationHours + compensatoryLeaveHours
        val paidWorkHours = paidWorkHoursFromWorkTypes + paidVacationAndCompensatoryHours

        if (paidWorkHours <= regularWorkingHours) {
            return BigDecimal.valueOf(0)
        }

        if (paidWorkHours > regularWorkingHours && paidWorkHours <= regularWorkingHours + overtimeFullLimit) {
            return if (full) {
                BigDecimal.valueOf(0)
            } else {
                paidWorkHours - regularWorkingHours
            }
        }

        if (paidWorkHours > regularWorkingHours + overtimeFullLimit) {
            return if (full) {
                paidWorkHours - regularWorkingHours - overtimeFullLimit
            } else {
                overtimeFullLimit
            }
        }

        return BigDecimal.valueOf(0)
    }

    /**
     * Calculate the total working hours for a given absence type from a list of work shifts
     * This is used by salary period working hours aggregation.
     *
     * @param workShifts
     * @param absenceType
     */
    suspend fun calculateTotalWorkHoursByAbsenceType(
        workShifts: List<WorkShiftEntity>,
        absenceType: AbsenceType
    ): BigDecimal {
        val filteredWorkShifts = workShifts.filter { workShift ->
            workShift.absence == absenceType
        }

        var totalHours = 0f

        filteredWorkShifts.forEach {
            if (absenceType == AbsenceType.COMPENSATORY_LEAVE) {
                totalHours += 8
                return@forEach
            }

            if (absenceType == AbsenceType.VACATION) {
                totalHours += 6.67f
                return@forEach
            }

            workShiftHoursController.listWorkShiftHours(
                workShiftFilter = it,
                workType = WorkType.PAID_WORK
            ).first.forEach { workShiftHour ->
                totalHours += workShiftHour.actualHours ?: workShiftHour.calculatedHours ?: 0f
            }
        }

        return totalHours.toBigDecimal()
    }

    /**
     * Calculate the regular working time from a list of work shifts.
     * This is used by salary period working hours aggregation.
     *
     * @param workShifts
     * @param regularWorkingHours
     */
    private suspend fun getRegularWorkingTime(
        workShifts: List<WorkShiftEntity>,
        regularWorkingHours: Float
    ): BigDecimal {
       val unpaidHours = calculateWorkingHoursByWorkType(
            workShifts = workShifts,
            workType = WorkType.UNPAID
        )

        return regularWorkingHours.toBigDecimal() - unpaidHours
    }

    /**
     * Calculate the total working hours from a list of work shifts.
     * This is used by salary period working hours aggregation.
     *
     * @param workShifts
     * @param workType
     */
    suspend fun calculateWorkingHoursByWorkType(workShifts: List<WorkShiftEntity>, workType: WorkType): BigDecimal {
        var totalHours = 0f

        for (workShift in workShifts) {
            workShiftHoursController.listWorkShiftHours(
                workShiftFilter = workShift,
                workType = workType
            ).first.forEach {
                totalHours += it.actualHours ?: it.calculatedHours ?: 0f
            }
        }

        return totalHours.toBigDecimal()
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
}