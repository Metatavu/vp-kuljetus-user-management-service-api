package fi.metatavu.vp.usermanagement.payrollexports.utilities

import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workevents.WorkEventEntity
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.temporal.ChronoUnit

@ApplicationScoped
class PayrollExportCalculations {
    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var workShiftHoursController: WorkShiftHoursController


    suspend fun calculateEveningAllowanceForWorkShift(
        workShift: WorkShiftEntity
    ): Map<String, Float> {
        val workEvents = workEventController.list(
            employeeWorkShift = workShift
        ).first

        val (calculatedEveningAllowanceHoursSum, calculatedEveningAllowanceHours) = calculatePaidWorkFromEvents(
            workEvents = workEvents,
            workType = WorkType.EVENING_ALLOWANCE
        )

        val modifiedEveningAllowanceHours = modifyCalculatedHoursWithManuallyEnteredHours(
            calculatedHours = calculatedEveningAllowanceHours,
            calculatedHoursSum = calculatedEveningAllowanceHoursSum,
            workShift = workShift,
            workType = WorkType.EVENING_ALLOWANCE
        )

        return modifiedEveningAllowanceHours
    }

    suspend fun calculateNightAllowanceForWorkShift(
        workShift: WorkShiftEntity
    ): Map<String, Float> {
        val workEvents = workEventController.list(
            employeeWorkShift = workShift
        ).first

        val (calculatedNightAllowanceHoursSum, calculatedNightAllowanceHours) = calculatePaidWorkFromEvents(
            workEvents = workEvents,
            workType = WorkType.NIGHT_ALLOWANCE
        )

        val modifiedNightAllowanceHours = modifyCalculatedHoursWithManuallyEnteredHours(
            calculatedHours = calculatedNightAllowanceHours,
            calculatedHoursSum = calculatedNightAllowanceHoursSum,
            workShift = workShift,
            workType = WorkType.NIGHT_ALLOWANCE
        )

        return modifiedNightAllowanceHours
    }

    suspend fun calculatePaidWorkForWorkShift(
        workShift: WorkShiftEntity
    ): Map<String, Float> {
        val totalPaidWork = mutableMapOf<String, Float>()

        val workEvents = workEventController.list(
            employeeWorkShift = workShift
        ).first

        val (calculatedPaidHoursSum, calculatedPaidHours) = calculatePaidWorkFromEvents(
            workEvents = workEvents,
            workType = WorkType.PAID_WORK
        )

        val (calculatedBreakHoursSum, calculatedBreakHours) = calculatePaidWorkFromEvents(
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

        modifiedPaidHours.forEach { (costCenter, hours) ->
            totalPaidWork[costCenter] = (totalPaidWork[costCenter] ?: 0f) + hours
        }

        val breakIsPaid = 8 <= ChronoUnit.HOURS.between(workShift.startedAt!!, workShift.endedAt!!)

        if (breakIsPaid) {
            modifiedBreakHours.forEach { (costCenter, hours) ->
                totalPaidWork[costCenter] = (totalPaidWork[costCenter] ?: 0f) + hours
            }
        }

        val sickHours = workShiftHoursController.listWorkShiftHours(
            workShiftFilter = workShift,
            workType = WorkType.SICK_LEAVE
        ).first.firstOrNull()?.actualHours ?: 0f

        totalPaidWork[workShift.defaultCostCenter ?: ""] = (totalPaidWork[workShift.defaultCostCenter ?: ""] ?: 0f) + sickHours

        return totalPaidWork
    }

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


    private fun calculatePaidWorkFromEvents(
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