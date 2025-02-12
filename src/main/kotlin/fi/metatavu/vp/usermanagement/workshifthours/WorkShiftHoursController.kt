package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.holidays.HolidayController
import fi.metatavu.vp.usermanagement.holidays.HolidayEntity
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.vertx.ConsumeEvent
import io.vertx.core.Vertx
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.max
import kotlin.math.min

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

    @Inject
    lateinit var logger: Logger

    /**
     * Event for calculating hours for work shift
     *
     * @param shiftId shift id
     */
    @ConsumeEvent(RECALCULATE_WORK_SHIFT_HOURS)
    @WithTransaction
    fun recalculateHours(
        shiftId: UUID
    ) = withCoroutineScope(20000L) {
        val workShift = workShiftController.findEmployeeWorkShift(shiftId = shiftId)
        if (workShift == null) {
            logger.warn("Did not find work shift $shiftId")
            return@withCoroutineScope
        }
        recalculateWorkShiftHours(workShift)
        logger.info("Recalculated work shift hours for shift $shiftId")
    }.replaceWithVoid()

    /**
     * Recalculates work shift hours for a work shift, saving the updated hours to the database.
     * Calculates all the hours but if the shift is still ongoing (latest event is not shift_end), ignores the last work
     * event.
     *
     * @param workShift work shift
     * @return recalculated work shift hours
     */
    suspend fun recalculateWorkShiftHours(workShift: WorkShiftEntity) {
        val publicHolidays = holidayController.list().first
        val updatableWorkShiftHours = listWorkShiftHours(workShiftFilter = workShift).first

        val temporaryHoursForTypes = WorkType.entries.associateWith { 0f }.toMutableMap()

        val workEvents = workEventController.list(employeeWorkShift = workShift).first.reversed()
        val daysOffDuringWorkShift = workEvents
            .distinctBy { it.time.toLocalDate() }
            .filter { isDayOffWork(it.time, workShift.employeeId) }
            .map { it.time.toLocalDate() }

        workEvents.forEachIndexed { index, workEvent ->
            // If this is the last event and the event type is not SHIFT_END, do not calculate the hours
            if (index == workEvents.lastIndex && workEvent.workEventType != WorkEventType.SHIFT_END) {
                return@forEachIndexed
            }

            val nextEventTime = workEvents.getOrNull(index + 1)?.time
            if (nextEventTime == null || nextEventTime < workEvent.time) {
                return@forEachIndexed
            }

            when (workEvent.workEventType) {
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
                WorkEventType.VEGETABLE-> {
                    addHours(
                        temporaryHoursForTypes = temporaryHoursForTypes,
                        workEventTime = workEvent.time,
                        nextWorkEventTime = nextEventTime,
                        type = WorkType.PAID_WORK,
                        publicHolidays = publicHolidays,
                        daysOff = daysOffDuringWorkShift
                    )
                }

                WorkEventType.FROZEN -> {
                    addHours(
                        temporaryHoursForTypes = temporaryHoursForTypes,
                        workEventTime = workEvent.time,
                        nextWorkEventTime = nextEventTime,
                        type = WorkType.PAID_WORK,
                        publicHolidays = publicHolidays,
                        daysOff = daysOffDuringWorkShift
                    )
                    addHours(
                        temporaryHoursForTypes = temporaryHoursForTypes,
                        workEventTime = workEvent.time,
                        nextWorkEventTime = nextEventTime,
                        type = WorkType.FROZEN_ALLOWANCE,
                        publicHolidays = publicHolidays,
                        daysOff = daysOffDuringWorkShift
                    )
                }

                WorkEventType.BREAK -> {
                    addHours(
                        temporaryHoursForTypes = temporaryHoursForTypes,
                        workEventTime = workEvent.time,
                        nextWorkEventTime = nextEventTime,
                        type = WorkType.BREAK,
                        publicHolidays = publicHolidays,
                        daysOff = daysOffDuringWorkShift
                    )
                }

                WorkEventType.DRIVER_CARD_INSERTED,
                WorkEventType.DRIVER_CARD_REMOVED,
                WorkEventType.LOGIN,
                WorkEventType.LOGOUT,
                WorkEventType.SHIFT_END,
                WorkEventType.SHIFT_START,
                WorkEventType.UNKNOWN -> {}
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
     * Creates a new work shift hours record for each work type
     *
     * @param workShiftEntity work shift entity
     * @return created work shift hours
     */
    suspend fun createWorkShiftHours(workShiftEntity: WorkShiftEntity): List<WorkShiftHoursEntity> {
        return WorkType.entries.map {
            workShiftHoursRepository.create(
                id = UUID.randomUUID(),
                workShiftEntity = workShiftEntity,
                workType = it
            )
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

    /**
     * Checks if the date is a day off work for employee
     *
     * @param date date
     * @param employeeId employee id
     * @return true if the work event is a day off work
     */
    private suspend fun isDayOffWork(date: OffsetDateTime, employeeId: UUID): Boolean {
        val shiftsDuringThisDay = workShiftController.listEmployeeWorkShifts(
            employeeId = employeeId,
            startedAfter = date.withHour(0).withMinute(0).withSecond(0),
            startedBefore = date.withHour(23).withMinute(59).withSecond(59),
            dateAfter = null,
            dateBefore = null
        ).first

        return shiftsDuringThisDay.any { it.dayOffWorkAllowance == true }
    }

    /**
     * Calculates evening allowance hours. Hours that are recorded as evening allowance are:
     * - hours between 18:00 and 22:00
     *
     * @param workEventTime work event time
     * @param nextWorkEventTime next work event time
     */
    private fun getEveningAllowanceHours(workEventTime: OffsetDateTime, nextWorkEventTime: OffsetDateTime): Float {
        var minutes = 0f

        var iteratedStartOfDay = workEventTime.withHour(0).withMinute(0).withSecond(0)
        while (iteratedStartOfDay.isBefore(nextWorkEventTime)) {
            val eveningStart = iteratedStartOfDay.withHour(18).withMinute(0).withSecond(0)
            val eveningEnd = iteratedStartOfDay.withHour(22).withMinute(0).withSecond(0)

            // if the next event ends before 22.00 the current day, use that as the end of the interval
            val start = if (workEventTime.isBefore(eveningStart)) eveningStart else workEventTime
            val end = if (nextWorkEventTime.isAfter(eveningEnd)) eveningEnd else nextWorkEventTime
            if (start.isBefore(end)) {
                minutes += ChronoUnit.MINUTES.between(start, end)
            }

            iteratedStartOfDay = iteratedStartOfDay.plusDays(1)
        }

        return minutes / 60f
    }

    /**
     * Calculates night allowance hours. Hours that are recorded as night allowance are:
     * - hours between 22:00 and 06:00
     *
     * @param workEventTime work event time
     * @param nextWorkEventTime next work event time
     */
    private fun getNightAllowanceHours(workEventTime: OffsetDateTime, nextWorkEventTime: OffsetDateTime): Float {
        var totalMinutes = 0f

        var iteratedStartOfDay = workEventTime.withHour(0).withMinute(0).withSecond(0)
        while (iteratedStartOfDay.isBefore(nextWorkEventTime)) {
            val currentNightStart = iteratedStartOfDay.withHour(0).withMinute(0).withSecond(0).withNano(0)
            val currentNightEnd = iteratedStartOfDay.withHour(6).withMinute(0).withSecond(0).withNano(0)

            val start1 = if (workEventTime.isBefore(currentNightStart)) currentNightStart else workEventTime
            val end1 = if (nextWorkEventTime.isAfter(currentNightEnd)) currentNightEnd else nextWorkEventTime
            if (start1.isBefore(end1)) {
                totalMinutes += ChronoUnit.MINUTES.between(start1, end1)
            }

            val nextNightStart = iteratedStartOfDay.withHour(22).withMinute(0).withSecond(0).withNano(0)
            val nextNightEnd = iteratedStartOfDay.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1)

            val start = if (workEventTime.isBefore(nextNightStart)) nextNightStart else workEventTime
            val end = if (nextWorkEventTime.isAfter(nextNightEnd)) nextNightEnd else nextWorkEventTime

            if (start.isBefore(end)) {
                totalMinutes += ChronoUnit.MINUTES.between(start, end)
            }

            iteratedStartOfDay = iteratedStartOfDay.plusDays(1)
        }

        return totalMinutes / 60f
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
     * @param daysOff list of days off work
     */
    private fun getHolidayAllowanceHours(
        workEventTime: OffsetDateTime,
        nextWorkEventTime: OffsetDateTime,
        publicHolidays: List<HolidayEntity>,
        daysOff: List<LocalDate>
    ): Float {
        var minutes = 0f

        var iteratedStartOfDay = workEventTime.withHour(0).withMinute(0).withSecond(0)
        while (iteratedStartOfDay.isBefore(nextWorkEventTime)) {
            val startOfNextDay = iteratedStartOfDay.plusDays(1)

            val start = if (workEventTime.isBefore(iteratedStartOfDay)) iteratedStartOfDay else workEventTime
            // if the next event ends before the next day, use that as the end of the interval
            val end = if (startOfNextDay.isAfter(nextWorkEventTime)) nextWorkEventTime else startOfNextDay

            val isSunday = start.dayOfWeek == DayOfWeek.SUNDAY
            val isPublicHoliday = publicHolidays.any { it.date == start.toLocalDate() }
            val isDayOff = daysOff.any { it == start.toLocalDate() }
            if (isSunday || isPublicHoliday || isDayOff) {
                minutes += ChronoUnit.MINUTES.between(start, end)
            }

            iteratedStartOfDay = startOfNextDay
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
     * @param daysOff days off work (based on the shifts) (for holiday allowance)
     */
    private fun addAllowance(
        temporaryHoursForTypes: MutableMap<WorkType, Float>,
        workEventTime: OffsetDateTime,
        nextWorkEventTime: OffsetDateTime,
        publicHolidays: List<HolidayEntity>,
        daysOff: List<LocalDate>
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
                daysOff = daysOff
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
     * @param daysOff days off (based on the work shifts this work shift) (for holiday allowance)
     */
    private fun addHours(
        temporaryHoursForTypes: MutableMap<WorkType, Float>,
        workEventTime: OffsetDateTime,
        nextWorkEventTime: OffsetDateTime,
        type: WorkType,
        publicHolidays: List<HolidayEntity>,
        daysOff: List<LocalDate>,
    ) {
        val hoursToNextEvent = workEventTime.until(nextWorkEventTime, ChronoUnit.SECONDS) / 60f / 60f
        val breakHoursBefore = temporaryHoursForTypes[WorkType.BREAK]!!
        temporaryHoursForTypes[type] = temporaryHoursForTypes[type]!! + hoursToNextEvent

        if (type == WorkType.PAID_WORK) {
            addAllowance(
                temporaryHoursForTypes = temporaryHoursForTypes,
                workEventTime = workEventTime,
                nextWorkEventTime = nextWorkEventTime,
                publicHolidays = publicHolidays,
                daysOff = daysOff
            )
        }

        if (type == WorkType.BREAK && breakHoursBefore < 0.5f) {
            val paidBreakHoursLeft = max(0.5f - breakHoursBefore, 0f)
            val paidBreakHoursToAdd = min(paidBreakHoursLeft, hoursToNextEvent)
            temporaryHoursForTypes[WorkType.PAID_WORK] = temporaryHoursForTypes[WorkType.PAID_WORK]!! + paidBreakHoursToAdd

            if (paidBreakHoursToAdd > 0f) {
                addAllowance(
                    temporaryHoursForTypes = temporaryHoursForTypes,
                    workEventTime = workEventTime,
                    nextWorkEventTime = workEventTime.plusSeconds((paidBreakHoursToAdd * 60f * 60f).toLong()),
                    publicHolidays = publicHolidays,
                    daysOff = daysOff
                )
            }
        }
    }

    companion object {
        const val RECALCULATE_WORK_SHIFT_HOURS = "RECALCULATE_WORK_SHIFT_HOURS"
    }


}