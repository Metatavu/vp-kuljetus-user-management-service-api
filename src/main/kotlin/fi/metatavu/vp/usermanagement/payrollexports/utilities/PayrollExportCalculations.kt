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

    enum class WorkTimeType {
        REGULAR,
        OVER_TIME_HALF,
        OVER_TIME_FULL
    }

    /**
     * Calculates the regular paid work or some type of overtime for a work shift depending on the workTimeType parameter.
     * Total paid work is calculated from events and/or manually entered hours for work types that are paid with the regular rate.
     * All these work types except TRAINING accumulate overtime.
     * Work types that are paid with the regular rate:
     * - PAID_WORK
     * - BREAK (up to 30 minutes for each shift that lasts at least 8 hours)
     * - SICK_LEAVE
     * - OFFICIAL_DUTIES
     * - TRAINING (training does not accumulate overtime)
     * - COMPENSATORY_LEAVE
     * - VACATION
     *
     * @param workShift
     * @param workTimeType
     * @param isDriver
     * @param driverRegularHoursSum
     * @param driverOverTimeHalfHoursSum
     * @param regularWorkingTime
     * @param vacationHours
     */
    suspend fun calculatePaidWorkForWorkShift(
        workShift: WorkShiftEntity,
        workTimeType: WorkTimeType,
        isDriver: Boolean,
        driverRegularHoursSum: Float?,
        driverOverTimeHalfHoursSum: Float?,
        regularWorkingTime: Float?,
        vacationHours: Float
    ): Pair<Map<String, Float>, Pair<Float, Float>> {
        val totalPaidWork = mutableMapOf<String, Float>()

        var regularHoursSumCurrent = if (isDriver) driverRegularHoursSum!! else 0f
        var overTimeHalfHoursSumCurrent = if (isDriver) driverOverTimeHalfHoursSum!! else 0f

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
            val result = calculateHoursForWorkTimeType(
                totalPaidWork = totalPaidWork,
                costCenterHours = it,
                workTimeType = workTimeType,
                regularHoursSum = regularHoursSumCurrent,
                overTimeHalfHoursSum = overTimeHalfHoursSumCurrent,
                isDriver = isDriver,
                regularWorkingTime = regularWorkingTime,
                vacationHours = vacationHours
            )

            regularHoursSumCurrent = result.first
            overTimeHalfHoursSumCurrent = result.second
        }

        val breakIsPaid = 8 <= ChronoUnit.HOURS.between(workShift.startedAt!!, workShift.endedAt!!)

        if (breakIsPaid) {
            modifiedBreakHours.forEach {
                val result = calculateHoursForWorkTimeType(
                    totalPaidWork = totalPaidWork,
                    costCenterHours = it,
                    workTimeType = workTimeType,
                    regularHoursSum = regularHoursSumCurrent,
                    overTimeHalfHoursSum = overTimeHalfHoursSumCurrent,
                    isDriver = isDriver,
                    regularWorkingTime = regularWorkingTime,
                    vacationHours = vacationHours
                )

                regularHoursSumCurrent = result.first
                overTimeHalfHoursSumCurrent = result.second
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

        val trainingHours = workShiftHoursController.listWorkShiftHours(
            workShiftFilter = workShift,
            workType = WorkType.TRAINING
        ).first.firstOrNull()?.actualHours ?: 0f

        val defaultCostCenter = workShift.defaultCostCenter ?: ""

        val isHoliday = workShift.startedAt?.dayOfWeek == DayOfWeek.SUNDAY || holidayController.list().first.find { it.date == workShift.startedAt?.toLocalDate() } != null

        if (workShift.absence == AbsenceType.COMPENSATORY_LEAVE) {
            val hours = mapOf(Pair(defaultCostCenter, 8f))
            val result = calculateHoursForWorkTimeType(
                totalPaidWork = totalPaidWork,
                costCenterHours = hours.entries.first(),
                workTimeType = workTimeType,
                regularHoursSum = regularHoursSumCurrent,
                overTimeHalfHoursSum = overTimeHalfHoursSumCurrent,
                isDriver = isDriver,
                regularWorkingTime = regularWorkingTime,
                vacationHours = vacationHours
            )

            regularHoursSumCurrent = result.first
            overTimeHalfHoursSumCurrent = result.second
        } else if (workShift.absence == AbsenceType.VACATION && !isHoliday) {
            val hours = mapOf(Pair(defaultCostCenter, 6.67f))

            val result = calculateHoursForWorkTimeType(
                totalPaidWork = totalPaidWork,
                costCenterHours = hours.entries.first(),
                workTimeType = workTimeType,
                regularHoursSum = regularHoursSumCurrent,
                overTimeHalfHoursSum = overTimeHalfHoursSumCurrent,
                isDriver = isDriver,
                regularWorkingTime = regularWorkingTime,
                vacationHours = vacationHours
            )

            regularHoursSumCurrent = result.first
            overTimeHalfHoursSumCurrent = result.second
        }

        val sickHoursMap = mapOf(Pair(defaultCostCenter, sickHours))
        val officialDutyHoursMap = mapOf(Pair(defaultCostCenter, officialDutyHours))

        val sickHoursResult = calculateHoursForWorkTimeType(
            totalPaidWork = totalPaidWork,
            costCenterHours = sickHoursMap.entries.first(),
            workTimeType = workTimeType,
            regularHoursSum = regularHoursSumCurrent,
            overTimeHalfHoursSum = overTimeHalfHoursSumCurrent,
            isDriver = isDriver,
            regularWorkingTime = regularWorkingTime,
            vacationHours = vacationHours
        )

        regularHoursSumCurrent = sickHoursResult.first
        overTimeHalfHoursSumCurrent = sickHoursResult.second

        calculateHoursForWorkTimeType(
            totalPaidWork = totalPaidWork,
            costCenterHours = officialDutyHoursMap.entries.first(),
            workTimeType = workTimeType,
            regularHoursSum = regularHoursSumCurrent,
            overTimeHalfHoursSum = overTimeHalfHoursSumCurrent,
            isDriver = isDriver,
            regularWorkingTime = regularWorkingTime,
            vacationHours = vacationHours
        )


        if (workTimeType == WorkTimeType.REGULAR) {
            totalPaidWork[defaultCostCenter] = (totalPaidWork[defaultCostCenter] ?: 0f) + trainingHours
        }

        return Pair(totalPaidWork, Pair(regularHoursSumCurrent, overTimeHalfHoursSumCurrent))
    }

    /**
     * This function filters the cost center hours that are within the workTimeType range, and adds the hours to totalPaidWork map.
     * Also the updated sums for regular hours and half overtime are returned.
     * These sums will be inputted when this function is called the next time so that the filtering works correctly at every stage.
     *
     * @param totalPaidWork already calculated paid work
     * @param costCenterHours hours to filter and add
     * @param workTimeType
     * @param regularHoursSum
     * @param overTimeHalfHoursSum
     * @param isDriver
     * @param regularWorkingTime
     * @param vacationHours
     */
    private suspend fun calculateHoursForWorkTimeType(
        totalPaidWork: MutableMap<String, Float>,
        costCenterHours: Map.Entry<String, Float>,
        workTimeType: WorkTimeType,
        regularHoursSum: Float,
        overTimeHalfHoursSum: Float,
        isDriver: Boolean,
        regularWorkingTime: Float?,
        vacationHours: Float
    ): Pair<Float, Float> {
        val costCenter = costCenterHours.key
        val hours = costCenterHours.value

        var regularHoursSumCurrent = regularHoursSum
        var overTimeHalfHoursSumCurrent = overTimeHalfHoursSum

        val overTimeHalfLimit = if (isDriver) regularWorkingTime else 8f
        val overTimeFullLimit = if (isDriver) { (if (vacationHours > 40) 10f else 12f) + (overTimeHalfLimit ?: 0f) } else 10f
        val currentCostCenterAmount = (totalPaidWork[costCenter] ?: 0f)

        val total = regularHoursSumCurrent + overTimeHalfHoursSumCurrent
        if (workTimeType == WorkTimeType.REGULAR && (overTimeHalfLimit == null || total < overTimeHalfLimit)) {
            if (overTimeHalfLimit == null || total + hours < overTimeHalfLimit) {
                totalPaidWork[costCenter] = currentCostCenterAmount + hours
                regularHoursSumCurrent += hours
            } else {
                val difference = overTimeHalfLimit - total
                totalPaidWork[costCenter] = currentCostCenterAmount + difference
                regularHoursSumCurrent += difference
            }

        } else if (overTimeHalfLimit != null && workTimeType == WorkTimeType.OVER_TIME_HALF  &&  overTimeHalfLimit <= total && total < overTimeFullLimit) {
            if (total + hours < overTimeFullLimit) {
                totalPaidWork[costCenter] = currentCostCenterAmount + hours
                overTimeHalfHoursSumCurrent += hours
            } else {
                val difference = overTimeFullLimit - total
                totalPaidWork[costCenter] = currentCostCenterAmount + difference
                overTimeHalfHoursSumCurrent += difference
            }
        } else if (overTimeHalfLimit != null && workTimeType == WorkTimeType.OVER_TIME_FULL && overTimeFullLimit <= total ) {
            totalPaidWork[costCenter] = total + hours
        }

        return Pair(regularHoursSumCurrent, overTimeHalfHoursSumCurrent)
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
                        val nightAllowanceHours = workShiftHoursController.getEveningAllowanceHours(
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

                    if(workType == WorkType.FROZEN_ALLOWANCE && it.workEventType == WorkEventType.FROZEN) {
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