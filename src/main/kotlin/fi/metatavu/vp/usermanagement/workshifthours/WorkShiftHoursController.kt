package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.model.WorkShiftHours
import fi.metatavu.vp.usermanagement.workshifts.EmployeeWorkShiftEntity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.OffsetDateTime
import java.util.*

/**
 * Controller for Work Shift Hours
 */
@ApplicationScoped
class WorkShiftHoursController {

    @Inject
    lateinit var workShiftHoursRepository: WorkShiftHoursRepository

    /**
     * Lists work shift hours, creates a new one if none found
     *
     * @param employeeId employee id
     * @param workShiftFilter work shift filter
     * @param workType work type
     * @param employeeWorkShiftStartedAfter employee work shift started after
     * @param employeeWorkShiftStartedBefore employee work shift started before
     * @return pair of work shift hours and count
     */
    suspend fun listWorkShiftHours(
        employeeId: UUID? = null,
        workShiftFilter: EmployeeWorkShiftEntity? = null,
        workType: WorkEventType? = null,
        employeeWorkShiftStartedAfter: OffsetDateTime? = null,
        employeeWorkShiftStartedBefore: OffsetDateTime? = null
    ): Pair<List<WorkShiftHoursEntity>, Long> {

        val workShiftHours = workShiftHoursRepository.listWorkShiftHours(
            employeeId = employeeId,
            workShift = workShiftFilter,
            workType = workType,
            employeeWorkShiftStartedAfter = employeeWorkShiftStartedAfter,
            employeeWorkShiftStartedBefore = employeeWorkShiftStartedBefore
        )
        if (workShiftHours.first.isEmpty() && employeeId != null && workShiftFilter != null && workType != null) {
            val new = createWorkShiftHours(
                workShiftEntity = workShiftFilter,
                workEventType = workType
            )
            return listOf(new) to 1
        }
        return workShiftHours
    }

    /**
     * Creates a new work shift hours
     *
     * @param workShiftEntity work shift entity
     * @param workEventType work event type
     * @param actualHours actual hours
     * @return created work shift hours
     */
    suspend fun createWorkShiftHours(
        workShiftEntity: EmployeeWorkShiftEntity,
        workEventType: WorkEventType,
        actualHours: Float? = null
    ): WorkShiftHoursEntity {
        return workShiftHoursRepository.create(
            id = UUID.randomUUID(),
            workShiftEntity = workShiftEntity,
            workEventType = workEventType,
            actualHours = actualHours
        )
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
     * @param workShiftHours work shift hours
     * @return updated work shift hours
     */
    suspend fun updateWorkShiftHours(
        existingWorkShiftHours: WorkShiftHoursEntity,
        workShiftHours: WorkShiftHours
    ): WorkShiftHoursEntity {
        existingWorkShiftHours.actualHours = workShiftHours.actualHours
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