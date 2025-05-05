package fi.metatavu.vp.usermanagement.payrollexports.utilities

import fi.metatavu.vp.usermanagement.holidays.HolidayController
import fi.metatavu.vp.usermanagement.model.AbsenceType
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workevents.WorkEventEntity
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

@ApplicationScoped
class PayrollExportCalculations {
    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var workShiftHoursController: WorkShiftHoursController

    @Inject
    lateinit var holidayController: HolidayController

    /**
     * Calculates the allowance for a work shift for a given allowance type.
     *
     * @param workShift
     * @param workType
     */
    suspend fun calculateAllowanceForWorkShift(
        workShift: WorkShiftEntity,
        workType: WorkType
    ): Map<String, Float> {
        val workEvents = workEventController.list(
            employeeWorkShift = workShift
        ).first.reversed()

        val (calculatedAllowanceHoursSum, calculatedAllowanceHours) = calculateWorkHoursFromEvents(
            workEvents = workEvents,
            workType = workType
        )

        val modifiedAllowanceHours = modifyCalculatedHoursWithManuallyEnteredHours(
            calculatedHours = calculatedAllowanceHours,
            calculatedHoursSum = calculatedAllowanceHoursSum,
            workShift = workShift,
            workType = workType
        )

        return modifiedAllowanceHours
    }

    enum class OfficeWorkerOverTimeType {
        OVERTIME_HALF,
        OVERTIME_FULL
    }

    suspend fun calculatePaidWorkForWorkShift(
        workShift: WorkShiftEntity,
        isDriver: Boolean,
        regularWorkingTime: Float?,
        vacationHours: Float,
        officeWorkerOverTimeType: OfficeWorkerOverTimeType? = null
    ): Map<String, Float> {
        val totalPaidWork = mutableMapOf<String, Float>()

        val workEvents = workEventController.list(
            employeeWorkShift = workShift
        ).first.reversed()

        val (calculatedPaidHoursSum, calculatedPaidHours) = calculateWorkHoursFromEvents(
            workEvents = workEvents,
            workType = WorkType.PAID_WORK
        )

        val (calculatedBreakHoursSum, calculatedBreakHours) = calculateWorkHoursFromEvents(
            workEvents = workEvents,
            workType = WorkType.BREAK
        )

        val modifiedPaidHours = modifyCalculatedHoursWithManuallyEnteredHours(
            calculatedHours = calculatedPaidHours,
            calculatedHoursSum = calculatedPaidHoursSum,
            workShift = workShift,
            workType = WorkType.PAID_WORK
        )

        val modifiedBreakHours = modifyCalculatedHoursWithManuallyEnteredHours(
            calculatedHours = calculatedBreakHours,
            calculatedHoursSum = calculatedBreakHoursSum,
            workShift = workShift,
            workType = WorkType.BREAK
        )

        modifiedPaidHours.forEach {
            val currentCostCenterAmount = (totalPaidWork[it.key] ?: 0f)
            totalPaidWork[it.key] = currentCostCenterAmount + it.value
        }

        val breakIsPaid = 8f <= (modifiedPaidHours.entries.sumOf { it.value.toBigDecimal() } + modifiedBreakHours.entries.sumOf { it.value.toBigDecimal() }).toFloat()

        if (breakIsPaid) {
            modifiedBreakHours.forEach {
                val currentCostCenterAmount = (totalPaidWork[it.key] ?: 0f)
                totalPaidWork[it.key] = currentCostCenterAmount + it.value
            }
        }

        val sickHours = workShiftHoursController.listWorkShiftHours(
            workShiftFilter = workShift,
            workType = WorkType.SICK_LEAVE
        ).first.firstOrNull()?.actualHours ?: 0f

        val officialDutyHours = workShiftHoursController.listWorkShiftHours(
            workShiftFilter = workShift,
            workType = WorkType.OFFICIAL_DUTIES
        ).first.firstOrNull()?.actualHours ?: 0f

        val defaultCostCenter = workShift.defaultCostCenter ?: ""

        val isHoliday = workShift.startedAt?.dayOfWeek == DayOfWeek.SUNDAY || holidayController.list().first.find { it.date == workShift.startedAt?.toLocalDate() } != null

        if (workShift.absence == AbsenceType.COMPENSATORY_LEAVE) {
            val currentCostCenterAmount = (totalPaidWork[defaultCostCenter] ?: 0f)
            totalPaidWork[defaultCostCenter] = currentCostCenterAmount + 8f
        } else if (workShift.absence == AbsenceType.VACATION && !isHoliday) {
            val currentCostCenterAmount = (totalPaidWork[defaultCostCenter] ?: 0f)
            totalPaidWork[defaultCostCenter] = currentCostCenterAmount + 6.67f
        }

        val defaultCostCenterAmount = (totalPaidWork[defaultCostCenter] ?: 0f)

        totalPaidWork[defaultCostCenter] = defaultCostCenterAmount + sickHours + officialDutyHours

        if (!isDriver) {
            val overTimeHalfLimit = 8f
            val overTimeFullLimit = 10f

            var total = 0f
            val regularPaidHours = mutableMapOf<String, Float>()
            val overTimeHalfHours = mutableMapOf<String, Float>()
            val overTimeFullHours = mutableMapOf<String, Float>()

            totalPaidWork.entries.forEach { item ->
                val costCenter = item.key
                val hours = item.value

                if (total + hours < overTimeHalfLimit) {
                    regularPaidHours[costCenter] = (regularPaidHours[costCenter] ?: 0f) + hours
                } else {
                    var regularHoursPart = overTimeHalfLimit - total

                    if (regularHoursPart < 0) {
                        regularHoursPart = 0f
                    }

                    regularPaidHours[costCenter] = (regularPaidHours[costCenter] ?: 0f) + regularHoursPart

                    val overTime = hours - regularHoursPart

                    if (total + regularHoursPart + overTime < overTimeFullLimit) {
                        overTimeHalfHours[costCenter] = (overTimeHalfHours[costCenter] ?: 0f) + overTime
                    } else {
                        var overTimeHalfPart = overTimeFullLimit - total - regularHoursPart

                        if (overTimeHalfPart < 0) {
                            overTimeHalfPart = 0f
                        }

                        overTimeHalfHours[costCenter] = (overTimeHalfHours[costCenter] ?: 0f) + overTimeHalfPart

                        val overTimeFullPart = overTime - overTimeHalfPart
                        if (overTimeFullPart > 0) {
                            overTimeFullHours[costCenter] = (overTimeFullHours[costCenter] ?: 0f) + overTimeFullPart
                        }
                    }
                }

                total += hours
            }

            return when (officeWorkerOverTimeType) {
                OfficeWorkerOverTimeType.OVERTIME_HALF -> {
                    overTimeHalfHours
                }
                OfficeWorkerOverTimeType.OVERTIME_FULL -> {
                    overTimeFullHours
                }
                else -> {
                    regularPaidHours
                }
            }
        }

        return totalPaidWork
    }

    /**
     * Applies manually modified hours to calculated hours. If manually entered hours are greater than calculated hours, then the excess will be added to default or empty cost center.
     * If the modified hours are less than the manually entered hours, then the difference will be removed starting from the last cost center in the map.
     *
     * @param calculatedHours
     * @param calculatedHoursSum
     * @param workShift
     * @param workType
     */
    private suspend fun modifyCalculatedHoursWithManuallyEnteredHours(
        calculatedHours: MutableMap<String, Float>,
        calculatedHoursSum: Float,
        workShift: WorkShiftEntity,
        workType: WorkType
    ): Map<String, Float> {
        val paidWorkShiftHours = workShiftHoursController.listWorkShiftHours(
            workShiftFilter = workShift,
            workType = workType
        ).first.firstOrNull()
        val manuallyEnteredHours = paidWorkShiftHours?.actualHours
        if (manuallyEnteredHours != null) {
            if (manuallyEnteredHours > calculatedHoursSum) {
                val difference = if (manuallyEnteredHours > 0.5f && workType == WorkType.BREAK) {
                    0.5f - calculatedHoursSum
                } else {
                    manuallyEnteredHours - calculatedHoursSum
                }
                val costCenter = workShift.defaultCostCenter ?: ""

                calculatedHours[costCenter] = (calculatedHours[costCenter] ?: 0f) + difference
            } else {
                var currentDifference = calculatedHoursSum - manuallyEnteredHours

                for (index in calculatedHours.entries.indices.reversed()) {
                    if (currentDifference == 0f) {
                        break
                    }

                    val hours = calculatedHours.entries.elementAt(index).value
                    val costCenter = calculatedHours.keys.elementAt(index)
                    if (hours > currentDifference) {
                        calculatedHours[costCenter] = hours - currentDifference
                        break
                    } else {
                        currentDifference -= calculatedHours[costCenter]!!
                        calculatedHours[costCenter] = 0f
                    }

                }
            }
        }

        return calculatedHours
    }


    /**
     * Calculates the work hours from work events for a given work type.
     *
     * @param workEvents
     * @param workType
     */
    private suspend fun calculateWorkHoursFromEvents(
        workEvents: List<WorkEventEntity>,
        workType: WorkType
    ): Pair<Float, MutableMap<String, Float>> {
        val calculatedHours = mutableMapOf<String, Float>()
        var calculatedHoursSum = 0f

        workEvents.forEachIndexed { index, it ->
            val nextEventTime = workEvents.getOrNull(index + 1)?.time

            if (nextEventTime == null || nextEventTime < it.time) {
                return@forEachIndexed
            }

            val hoursToNextEvent = it.time.until(nextEventTime, ChronoUnit.SECONDS) / 60f / 60f
            val costCenter = it.costCenter ?: ""
            val currentAmountForCostCenter = calculatedHours[costCenter] ?: 0f

            when(it.workEventType) {
                WorkEventType.AVAILABILITY,
                WorkEventType.BREWERY,
                WorkEventType.DRIVE,
                WorkEventType.DRY,
                WorkEventType.GREASE,
                WorkEventType.LOADING,
                WorkEventType.MEAT_CELLAR,
                WorkEventType.MEIRA,
                WorkEventType.OFFICE,
                WorkEventType.OTHER_WORK,
                WorkEventType.PALTE,
                WorkEventType.UNLOADING,
                WorkEventType.VEGETABLE,
                WorkEventType.FROZEN -> {
                    if (workType == WorkType.PAID_WORK) {
                        calculatedHours[costCenter] = currentAmountForCostCenter + hoursToNextEvent
                        calculatedHoursSum += hoursToNextEvent
                    }

                    if (workType == WorkType.EVENING_ALLOWANCE) {
                        val eveningAllowanceHours = workShiftHoursController.getEveningAllowanceHours(
                            workEventTime = it.time,
                            nextWorkEventTime = nextEventTime
                        )

                        calculatedHours[costCenter] = currentAmountForCostCenter + eveningAllowanceHours
                        calculatedHoursSum += eveningAllowanceHours
                    }

                    if (workType == WorkType.NIGHT_ALLOWANCE) {
                        val nightAllowanceHours = workShiftHoursController.getNightAllowanceHours(
                            workEventTime = it.time,
                            nextWorkEventTime = nextEventTime
                        )
                        calculatedHours[costCenter] = currentAmountForCostCenter + nightAllowanceHours
                        calculatedHoursSum += nightAllowanceHours
                    }

                    if (workType == WorkType.HOLIDAY_ALLOWANCE) {
                        val publicHolidays = holidayController.list().first

                        val daysOffDuringWorkShift = workEvents
                            .distinctBy { it.time.toLocalDate() }
                            .filter { workShiftHoursController.isDayOffWork(it.time, it.workShift.employeeId) }
                            .map { it.time.toLocalDate() }

                        val holidayAllowanceHours = workShiftHoursController.getHolidayAllowanceHours(
                            workEventTime = it.time,
                            nextWorkEventTime = nextEventTime,
                            publicHolidays = publicHolidays,
                            daysOff = daysOffDuringWorkShift
                        )
                        calculatedHours[costCenter] = currentAmountForCostCenter + holidayAllowanceHours
                        calculatedHoursSum += holidayAllowanceHours
                    }

                    if (workType == WorkType.FROZEN_ALLOWANCE && it.workEventType == WorkEventType.FROZEN) {
                        calculatedHours[costCenter] = currentAmountForCostCenter + hoursToNextEvent
                        calculatedHoursSum += hoursToNextEvent
                    }
                }

                WorkEventType.BREAK -> {
                    if (workType == WorkType.BREAK && calculatedHoursSum < 0.5f) {
                        if (calculatedHoursSum + hoursToNextEvent >= 0.5f) {
                            val remainderToBeAdded = 0.5f - calculatedHoursSum
                            calculatedHours[costCenter] = currentAmountForCostCenter + remainderToBeAdded
                            calculatedHoursSum += remainderToBeAdded
                        } else {
                            calculatedHours[costCenter] = currentAmountForCostCenter + hoursToNextEvent
                            calculatedHoursSum += hoursToNextEvent
                        }

                    }
                }

                else -> {}
            }
        }

        return Pair(calculatedHoursSum, calculatedHours)
    }
}