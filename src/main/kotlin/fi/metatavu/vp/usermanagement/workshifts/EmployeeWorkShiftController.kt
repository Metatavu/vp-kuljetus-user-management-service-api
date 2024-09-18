package fi.metatavu.vp.usermanagement.workshifts

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.model.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@ApplicationScoped
class EmployeeWorkShiftController {

    @Inject
    lateinit var employeeWorkShiftRepository: EmployeeWorkShiftRepository

    @Inject
    lateinit var workShiftHoursController: WorkShiftHoursController

    @Inject
    lateinit var workEventController: WorkEventController

    /**
     * Creates a new employee work shift (unapproved)
     *
     * @param employeeId employee id
     * @param date date
     * @return created employee work shift
     */
    suspend fun create(
        employeeId: UUID,
        date: LocalDate
    ): EmployeeWorkShiftEntity {
        return employeeWorkShiftRepository.create(UUID.randomUUID(), employeeId, date, approved = false)
    }

    /**
     * Finds employee work shift by id
     *
     * @param shiftId employee work shift id
     * @return employee work shift or null if not found
     */
    suspend fun findEmployeeWorkShift(
        enployeeID: UUID? = null,
        shiftId: UUID
    ): EmployeeWorkShiftEntity? {
        val found = employeeWorkShiftRepository.findByIdSuspending(shiftId)
        if (enployeeID != null && found != null && found.employeeId != enployeeID) {
            return null
        }
        return found
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
    ): Pair<List<EmployeeWorkShiftEntity>, Long> {
        return employeeWorkShiftRepository.listEmployeeWorkShifts(UUID.fromString(employee.id), startedAfter, startedBefore, first, max)
    }

    /**
     * Updates employee work shift
     *
     * @param foundShift found shift
     * @param employeeWorkShift employee work shift
     * @return updated employee work shift
     */
    suspend fun updateEmployeeWorkShift(
        foundShift: EmployeeWorkShiftEntity,
        employeeWorkShift: EmployeeWorkShift
    ): EmployeeWorkShiftEntity {
        foundShift.approved = employeeWorkShift.approved
        return employeeWorkShiftRepository.persistSuspending(foundShift)
    }

    /**
     * Updates employee work shift date
     *
     * @param foundShift found shift
     * @param newTime new time
     * @return updated employee work shift
     */
    suspend fun updateEmployeeWorkShift(
        foundShift: EmployeeWorkShiftEntity,
        newTime: LocalDate
    ): EmployeeWorkShiftEntity {
        foundShift.date = newTime
        return employeeWorkShiftRepository.persistSuspending(foundShift)
    }

    /**
     * Deletes employee work shift and all related work shift hours and work events
     *
     * @param employeeWorkShift employee work shift
     */
    suspend fun deleteEmployeeWorkShift(employeeWorkShift: EmployeeWorkShiftEntity) {
        workShiftHoursController.listWorkShiftHours(workShiftFilter = employeeWorkShift).first.forEach {
            workShiftHoursController.deleteWorkShiftHours(it)
        }

        workEventController.list(employeeWorkShift = employeeWorkShift).first.forEach {
            workEventController.delete(it)
        }

        employeeWorkShiftRepository.deleteSuspending(employeeWorkShift)
    }
}