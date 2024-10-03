package fi.metatavu.vp.usermanagement.workshifts

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.model.AbsenceType
import fi.metatavu.vp.usermanagement.model.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.model.PerDiemAllowanceType
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/**
 * Controller for employee work shifts
 */
@ApplicationScoped
class WorkShiftController {

    @Inject
    lateinit var workShiftRepository: WorkShiftRepository

    @Inject
    lateinit var workShiftHoursController: WorkShiftHoursController

    @Inject
    lateinit var workEventController: WorkEventController

    /**
     * Creates a new employee work shift (unapproved)
     * and creates work shift hours for it
     *
     * @param employeeId employee id
     * @param date date
     * @return created employee work shift
     */
    suspend fun create(
        employeeId: UUID,
        date: LocalDate,
        absenceType: AbsenceType? = null,
        perDiemAllowanceType: PerDiemAllowanceType? = null
    ): WorkShiftEntity {
        val shift = workShiftRepository.create(
            id = UUID.randomUUID(),
            employeeId = employeeId,
            date = date,
            approved = false,
            absence = absenceType,
            perDiemAllowance = perDiemAllowanceType
        )
        workShiftHoursController.createWorkShiftHours(
            workShiftEntity = shift
        )
        return shift
    }

    /**
     * Finds employee work shift by id and optionally by employee id
     *
     * @param shiftId employee work shift id
     * @return employee work shift or null if not found
     */
    suspend fun findEmployeeWorkShift(
        employeeId: UUID? = null,
        shiftId: UUID
    ): WorkShiftEntity? {
        val found = workShiftRepository.findByIdSuspending(shiftId)
        return found?.takeIf { employeeId == null || it.employeeId == employeeId }
    }

    /**
     * Lists employee work shifts
     *
     * @param employee employee
     * @param startedAfter started after
     * @param startedBefore started before
     * @param first first
     * @param max max
     * @return pair of list of employee work shifts and count
     */
    suspend fun listEmployeeWorkShifts(
        employee: UserRepresentation,
        startedAfter: OffsetDateTime?,
        startedBefore: OffsetDateTime?,
        first: Int,
        max: Int
    ): Pair<List<WorkShiftEntity>, Long> {
        return workShiftRepository.listEmployeeWorkShifts(
            UUID.fromString(employee.id),
            startedAfter,
            startedBefore,
            first,
            max
        )
    }

    /**
     * Updates employee work shift status
     *
     * @param foundShift found shift
     * @param approved status
     * @return updated employee work shift
     */
    suspend fun updateEmployeeWorkShift(
        foundShift: WorkShiftEntity,
        approved: Boolean
    ): WorkShiftEntity {
        foundShift.approved = approved
        return workShiftRepository.persistSuspending(foundShift)
    }

    /**
     * Updates employee work shift date
     *
     * @param foundShift found shift
     * @param newTime new time
     * @return updated employee work shift
     */
    suspend fun updateEmployeeWorkShift(
        foundShift: WorkShiftEntity,
        newTime: LocalDate
    ): WorkShiftEntity {
        foundShift.date = newTime
        return workShiftRepository.persistSuspending(foundShift)
    }

    /**
     * Deletes employee work shift and all related work shift hours and work events
     *
     * @param employeeWorkShift employee work shift
     */
    suspend fun deleteEmployeeWorkShift(employeeWorkShift: WorkShiftEntity) {
        workShiftHoursController.listWorkShiftHours(workShiftFilter = employeeWorkShift).first.forEach {
            workShiftHoursController.deleteWorkShiftHours(it)
        }

        workEventController.list(employeeWorkShift = employeeWorkShift).first.forEach {
            workEventController.delete(it)
        }

        workShiftRepository.deleteSuspending(employeeWorkShift)
    }
}