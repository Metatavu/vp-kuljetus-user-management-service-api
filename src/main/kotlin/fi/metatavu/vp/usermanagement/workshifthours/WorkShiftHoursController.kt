package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.holidays.HolidayController
import fi.metatavu.vp.usermanagement.holidays.HolidayEntity
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workevents.WorkEventEntity
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.vertx.ConsumeEvent
import io.vertx.core.Vertx
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Controller for Work Shift Hours
 */
@ApplicationScoped
class WorkShiftHoursController: WithCoroutineScope() {

    @Inject
    lateinit var workShiftHoursRepository: WorkShiftHoursRepository

    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var holidayController: HolidayController

    @Inject
    lateinit var workShiftController: WorkShiftController

    @Inject
    lateinit var vertx: Vertx

    /**
     * Event for calculating hours for work shift
     *
     * @param shiftId shift id
     */
    @ConsumeEvent("workShiftHours.calculate")
    @WithTransaction
    fun recalculateHours(
        shiftId: UUID
    ) = withCoroutineScope {
        val workShift = workShiftController.findEmployeeWorkShift(shiftId = shiftId) ?: return@withCoroutineScope
        recalculateWorkShiftHours(workShift)
    }.replaceWithVoid()

    /**
     * Recalculates work shift hours for a work shift, saving the updated hours to the database.
     * Calculates all the hours but if the shift is still ongoing (latest event is not shift_end), ignores the last work
     * event.
     *
     * @param workShift work shift
     * @return recalculated work shift hours
     */
    suspend fun recalculateWorkShiftHours(
        workShift: WorkShiftEntity,
    ) {
        val publicHolidays = holidayController.list().first
        val updatableWorkShiftHours = listWorkShiftHours(workShiftFilter = workShift).first

        val temporaryHoursForTypes = WorkType.entries.associateWith { 0f }.toMutableMap()

        val workEvents = workEventController.list(employeeWorkShift = workShift).first.reversed()
        workEvents.forEachIndexed { index, workEvent ->
            val isShiftOffWork = isDayOffWork(workEvent)

            val nextTime = workEvents.getOrNull(index + 1)?.time
            if (index == workEvents.size - 1 && workEvent.workEventType != WorkEventType.SHIFT_END) {
                //if this is the last event and it is not a shift end then do not calculate
                return@forEachIndexed
            }

            if (nextTime == null || nextTime < workEvent.time) return@forEachIndexed

            when (workEvent.workEventType) {
                WorkEventType.OFFICE, WorkEventType.VEGETABLE, WorkEventType.DRY, WorkEventType.MEAT_CELLAR, WorkEventType.MEIRA,
                WorkEventType.PALTE, WorkEventType.BREWERY, WorkEventType.GREASE, WorkEventType.OTHER_WORK,
                WorkEventType.DRIVE, WorkEventType.LOADING, WorkEventType.UNLOADING, WorkEventType.AVAILABILITY -> {
                    addHours(
                        temporaryHoursForTypes = temporaryHoursForTypes, workEventTime = workEvent.time,
                        nextWorkEventTime = nextTime, type = WorkType.PAID_WORK, publicHolidays = publicHolidays,
                        isShiftOffWork = isShiftOffWork
                    )
                }

                WorkEventType.FROZEN -> {
                    addHours(
                        temporaryHoursForTypes = temporaryHoursForTypes, workEventTime = workEvent.time,
                        nextWorkEventTime = nextTime, type = WorkType.PAID_WORK,
                        publicHolidays = publicHolidays, isShiftOffWork = isShiftOffWork
                    )
                    addHours(
                        temporaryHoursForTypes = temporaryHoursForTypes, workEventTime = workEvent.time,
                        nextWorkEventTime = nextTime, type = WorkType.FROZEN_ALLOWANCE,
                        publicHolidays = publicHolidays, isShiftOffWork = isShiftOffWork
                    )
                }

                WorkEventType.BREAK -> {
                    addHours(
                        temporaryHoursForTypes = temporaryHoursForTypes, workEventTime = workEvent.time,
                        nextWorkEventTime = nextTime, type = WorkType.BREAK, publicHolidays = publicHolidays,
                        isShiftOffWork = isShiftOffWork
                    )
                }

                WorkEventType.LOGIN, WorkEventType.LOGOUT, WorkEventType.SHIFT_START, WorkEventType.SHIFT_END,
                WorkEventType.UNKNOWN, WorkEventType.DRIVER_CARD_INSERTED, WorkEventType.DRIVER_CARD_REMOVED -> {
                }
            }
        }

        updatableWorkShiftHours.forEach {
            it.calculatedHours = temporaryHoursForTypes[it.workType]
            workShiftHoursRepository.persistSuspending(it)
        }
    }

    /**
     * Lists work shift hours
     *
     * @param employeeId employee id
     * @param workShiftFilter work shift filter
     * @param workType work type
     * @param employeeWorkShiftStartedAfter employee work shift started after
     * @param employeeWorkShiftStartedBefore employee work shift started before
     * @return pair of list of work shift hours and count
     */
    suspend fun listWorkShiftHours(
        employeeId: UUID? = null,
        workShiftFilter: WorkShiftEntity? = null,
        workType: WorkType? = null,
        employeeWorkShiftStartedAfter: OffsetDateTime? = null,
        employeeWorkShiftStartedBefore: OffsetDateTime? = null
    ): Pair<List<WorkShiftHoursEntity>, Long> {
        return workShiftHoursRepository.listWorkShiftHours(
            employeeId = employeeId,
            workShift = workShiftFilter,
            workType = workType,
            employeeWorkShiftStartedAfter = employeeWorkShiftStartedAfter,
            employeeWorkShiftStartedBefore = employeeWorkShiftStartedBefore
        )
    }

    /**
     * Checks if the work event is a day off work
     *
     * @param workEvent work event
     * @return true if the work event is a day off work
     */
    private suspend fun isDayOffWork(workEvent: WorkEventEntity): Boolean {
        val shiftsDuringThisDay = workShiftController.listEmployeeWorkShifts(
            employeeId = workEvent.employeeId,
            startedAfter = workEvent.time.withHour(0).withMinute(0).withSecond(0),
            startedBefore = workEvent.time.withHour(23).withMinute(59).withSecond(59)
        ).first

        return shiftsDuringThisDay.any { it.dayOffWorkAllowance == true }
    }

    /**
     * Calculates evening allowance hours. Hours that are recorded as evening allowance are:
     * - hours between 18:00 and 20:00
     *
     * @param workEventTime work event time
     * @param nextWorkEventTime next work event time
     */
    private fun getEveningAllowanceHours(workEventTime: OffsetDateTime, nextWorkEventTime: OffsetDateTime): Float {
        var minutes = 0f

        var now = workEventTime
        while (now.isBefore(nextWorkEventTime)) {
            val nightStart = now.withHour(18).withMinute(0).withSecond(0)
            val eveningEnd = now.withHour(20).withMinute(0).withSecond(0)

            // if the next event ends before the next day, use that as the end of the interval
            val start = if (workEventTime.isBefore(nightStart)) nightStart else workEventTime
            val end = if (nextWorkEventTime.isAfter(eveningEnd)) eveningEnd else nextWorkEventTime
            if (start.isBefore(end)) {
                minutes += ChronoUnit.MINUTES.between(start, end)
            }

            now = now.plusDays(1)
        }

        return minutes / 60f
    }

    /**
     * Calculates night allowance hours. Hours that are recorded as night allowance are:
     * - hours between 20:00 and 06:00
     *
     * @param workEventTime work event time
     * @param nextWorkEventTime next work event time
     */
    private fun getNightAllowanceHours(workEventTime: OffsetDateTime, nextWorkEventTime: OffsetDateTime): Float {
        var minutes = 0f
        var now = workEventTime

        while (now.isBefore(nextWorkEventTime)) {
            val currentNightStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0)
            val currentNightEnd = now.withHour(6).withMinute(0).withSecond(0).withNano(0)

            val start1 = if (workEventTime.isBefore(currentNightStart)) currentNightStart else workEventTime
            val end1 = if (nextWorkEventTime.isAfter(currentNightEnd)) currentNightEnd else nextWorkEventTime
            if (start1.isBefore(end1)) {
                minutes += ChronoUnit.MINUTES.between(start1, end1)
            }

            val nextNightStart = now.withHour(20).withMinute(0).withSecond(0).withNano(0)
            val nextNightEnd = now.withHour(6).withMinute(0).withSecond(0).withNano(0).plusDays(1)

            val start = if (workEventTime.isBefore(nextNightStart)) nextNightStart else workEventTime
            val end = if (nextWorkEventTime.isAfter(nextNightEnd)) nextNightEnd else nextWorkEventTime

            if (start.isBefore(end)) {
                minutes += ChronoUnit.MINUTES.between(start, end)
            }

            now = now.plusDays(1)
        }

        return minutes / 60f
    }

    /**
     * Calculates holiday allowance hours. Hours that are recorded as holiday allowance are:
     * - hours during Sunday
     * - hours during public holidays
     * - hours during day off work (if the holiday is marked as day off work or if days shift is marked as day off work)     *
     *
     * @param workEventTime work event time
     * @param nextWorkEventTime next work event time
     * @param publicHolidays list of public holidays
     * @param isDayOffWork is day off work (based on the shifts)
     */
    private fun getHolidayAllowanceHours(
        workEventTime: OffsetDateTime,
        nextWorkEventTime: OffsetDateTime,
        publicHolidays: List<HolidayEntity>,
        isDayOffWork: Boolean
    ): Float {
        var minutes = 0f

        var now = workEventTime
        while (now.isBefore(nextWorkEventTime)) {
            val nextDay = now.plusDays(1).withHour(0).withMinute(0).withSecond(0)

            // if the next event ends before the next day, use that as the end of the interval
            val intervalEnd = if (nextDay.isAfter(nextWorkEventTime)) nextWorkEventTime else nextDay

            if (now.dayOfWeek == DayOfWeek.SUNDAY || publicHolidays.any { it.date == now.toLocalDate() } || isDayOffWork) {
                minutes += ChronoUnit.MINUTES.between(now, intervalEnd)
            }

            now = intervalEnd
        }

        return minutes / 60f
    }

    /**
     * Adds allowance hours to the temporary hours map
     *
     * @param temporaryHoursForTypes temporary hours map that contains the hours for each work type
     * @param workEventTime work event time
     * @param nextWorkEventTime next work event time
     * @param publicHolidays list of public holidays (for holiday allowance)
     * @param isShiftOffWork is day off work (based on the shifts) (for holiday allowance)
     */
    private fun addAllowance(
        temporaryHoursForTypes: MutableMap<WorkType, Float>,
        workEventTime: OffsetDateTime,
        nextWorkEventTime: OffsetDateTime,
        publicHolidays: List<HolidayEntity>,
        isShiftOffWork: Boolean
    ) {
        temporaryHoursForTypes[WorkType.EVENING_ALLOWANCE] =
            temporaryHoursForTypes[WorkType.EVENING_ALLOWANCE]!! + getEveningAllowanceHours(
                workEventTime = workEventTime,
                nextWorkEventTime = nextWorkEventTime
            )
        temporaryHoursForTypes[WorkType.NIGHT_ALLOWANCE] =
            temporaryHoursForTypes[WorkType.NIGHT_ALLOWANCE]!! + getNightAllowanceHours(
                workEventTime = workEventTime,
                nextWorkEventTime = nextWorkEventTime
            )
        temporaryHoursForTypes[WorkType.HOLIDAY_ALLOWANCE] =
            temporaryHoursForTypes[WorkType.HOLIDAY_ALLOWANCE]!! + getHolidayAllowanceHours(
                workEventTime = workEventTime,
                nextWorkEventTime = nextWorkEventTime,
                publicHolidays = publicHolidays,
                isDayOffWork = isShiftOffWork
            )
    }

    /**
     * Adds hours to the temporary hours map for the correct type
     * Adds allowances if needed
     *
     * @param temporaryHoursForTypes temporary hours map that contains the hours for each work type
     * @param workEventTime work event time
     * @param nextWorkEventTime next work event time
     * @param type work type
     * @param publicHolidays list of public holidays (for holiday allowance)
     * @param isShiftOffWork is day off work (based on the shifts) (for holiday allowance)
     */
    fun addHours(
        temporaryHoursForTypes: MutableMap<WorkType, Float>,
        workEventTime: OffsetDateTime,
        nextWorkEventTime: OffsetDateTime,
        type: WorkType,
        publicHolidays: List<HolidayEntity>,
        isShiftOffWork: Boolean
    ) {
        val distanceToNext = workEventTime.until(nextWorkEventTime, ChronoUnit.MINUTES) / 60f
        temporaryHoursForTypes[type] = temporaryHoursForTypes[type]!! + distanceToNext
        if (type == WorkType.PAID_WORK) {
            addAllowance(
                temporaryHoursForTypes = temporaryHoursForTypes,
                workEventTime = workEventTime,
                nextWorkEventTime = nextWorkEventTime,
                publicHolidays = publicHolidays,
                isShiftOffWork = isShiftOffWork
            )
        }
    }

    /**
     * Creates a new work shift hours record for each work type
     *
     * @param workShiftEntity work shift entity
     * @param actualHours actual hours
     * @return created work shift hours
     */
    suspend fun createWorkShiftHours(
        workShiftEntity: WorkShiftEntity,
        actualHours: Float? = null
    ): List<WorkShiftHoursEntity> {
        return WorkType.entries.map {
            val created = workShiftHoursRepository.create(
                id = UUID.randomUUID(),
                workShiftEntity = workShiftEntity,
                workType = it
            )

            created
        }
    }

    /**
     * Finds work shift hours by id
     *
     * @param workShiftHoursId work shift hours id
     * @return work shift hours or null if not found
     */
    suspend fun findWorkShiftHours(workShiftHoursId: UUID): WorkShiftHoursEntity? {
        return workShiftHoursRepository.findByIdSuspending(workShiftHoursId)
    }

    /**
     * Updates work shift hours (only actual hours)
     *
     * @param existingWorkShiftHours existing work shift hours
     * @param actualHours actualHours
     * @return updated work shift hours
     */
    suspend fun updateWorkShiftHours(
        existingWorkShiftHours: WorkShiftHoursEntity,
        actualHours: Float?
    ): WorkShiftHoursEntity {
        existingWorkShiftHours.actualHours = actualHours
        return workShiftHoursRepository.persistSuspending(existingWorkShiftHours)
    }

    /**
     * Deletes work shift hours
     *
     * @param workShiftHours work shift hours
     */
    suspend fun deleteWorkShiftHours(workShiftHours: WorkShiftHoursEntity) {
        workShiftHoursRepository.deleteSuspending(workShiftHours)
    }

}