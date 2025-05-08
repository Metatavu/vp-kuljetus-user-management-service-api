package fi.metatavu.vp.usermanagement.workshifts

import fi.metatavu.vp.usermanagement.model.AbsenceType
import fi.metatavu.vp.usermanagement.model.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.model.PerDiemAllowanceType
import fi.metatavu.vp.usermanagement.payrollexports.PayrollExportController
import fi.metatavu.vp.usermanagement.payrollexports.PayrollExportEntity
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets.WorkShiftChangeSetController
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
    
    @Inject
    lateinit var workShiftChangeSetController: WorkShiftChangeSetController

    /**
     * Creates a new employee work shift (unapproved)
     * and creates work shift hours for it
     *
     * @param employeeId employee id
     * @param date date
     * @param absenceType absence type
     * @param perDiemAllowanceType per diem allowance type
     * @param startedAt started at
     * @param endedAt ended at
     * @param dayOffWorkAllowance day off work allowance
     * @param notes
     * @param defaultCostCenter
     * @return created employee work shift
     */
    suspend fun create(
        employeeId: UUID,
        date: LocalDate,
        absenceType: AbsenceType? = null,
        perDiemAllowanceType: PerDiemAllowanceType? = null,
        startedAt: OffsetDateTime? = null,
        endedAt: OffsetDateTime? = null,
        dayOffWorkAllowance: Boolean? = null,
        notes: String? = null,
        defaultCostCenter: String? = null
    ): WorkShiftEntity {
        val shift = workShiftRepository.create(
            id = UUID.randomUUID(),
            employeeId = employeeId,
            date = date,
            approved = false,
            absence = absenceType,
            perDiemAllowance = perDiemAllowanceType,
            startedAt = startedAt,
            endedAt = endedAt,
            dayOffWorkAllowance = dayOffWorkAllowance,
            notes = notes,
            defaultCostCenter = defaultCostCenter

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
     * @param employeeId employee ID
     * @param startedAfter started after
     * @param startedBefore started before
     * @param dateAfter date after
     * @param dateBefore date before
     * @param first first
     * @param max max
     * @return pair of list of employee work shifts and count
     */
    suspend fun listEmployeeWorkShifts(
        employeeId: UUID,
        startedAfter: OffsetDateTime?,
        startedBefore: OffsetDateTime?,
        dateAfter: LocalDate?,
        dateBefore: LocalDate?,
        first: Int? = null,
        max: Int? = null,
        payrollExport: PayrollExportEntity? = null
    ): Pair<List<WorkShiftEntity>, Long> {
        return workShiftRepository.listEmployeeWorkShifts(
            employeeId,
            startedAfter,
            startedBefore,
            dateAfter,
            dateBefore,
            first,
            max,
            payrollExport
        )
    }

    /**
     * Lists work shifts that do not have their hours calculated yet (and which are supposed to be calculated)
     *
     * @param first first
     * @param last last
     */
    suspend fun listUnfinishedWorkShifts(first: Int, last: Int) : List<WorkShiftEntity> {
        return workShiftRepository.listsNotCalculatedWorkShifts(first, last)
    }

    /**
     * Updates employee work shift
     *
     * @param existingWorkShift existing work shift
     * @param updatedWorkShift updated work shift
     * @return updated employee work shift
     */
    suspend fun updateEmployeeWorkShift(
        existingWorkShift: WorkShiftEntity,
        updatedWorkShift: EmployeeWorkShift
    ): WorkShiftEntity {
        return workShiftRepository.update(
            workShiftEntity = existingWorkShift,
            absence = updatedWorkShift.absence,
            dayOffWorkAllowance = updatedWorkShift.dayOffWorkAllowance,
            perDiemAllowance = updatedWorkShift.perDiemAllowance,
            notes = updatedWorkShift.notes,
            approved = updatedWorkShift.approved,
            defaultCostCenter = updatedWorkShift.defaultCostCenter
        )
    }

    /**
     * Deletes employee work shift and all related work shift hours and work events
     *
     * @param employeeWorkShift employee work shift
     */
    suspend fun deleteEmployeeWorkShift(employeeWorkShift: WorkShiftEntity) {
        workShiftChangeSetController.listByWorkShift(employeeWorkShift).forEach {
            workShiftChangeSetController.delete(it)
        }

        workShiftHoursController.listWorkShiftHours(workShiftFilter = employeeWorkShift).first.forEach {
            workShiftHoursController.deleteWorkShiftHours(it)
        }

        workEventController.list(employeeWorkShift = employeeWorkShift).first.forEach {
            workEventController.deleteWithNoSideEffects(it)
        }

        workShiftRepository.deleteSuspending(employeeWorkShift)
    }

    /**
     * Sets a payroll export for work shift.
     * Work shift can be part only of one payroll export at a time.
     *
     * @param workShift
     * @param payrollExport
      */
    suspend fun setPayrollExport(
        workShift: WorkShiftEntity,
        payrollExport: PayrollExportEntity?
    ) {
        if (workShift.payrollExport == null) {
            workShiftRepository.setPayrollExport(
                workShiftEntity = workShift,
                payrollExportEntity = payrollExport
            )
        } else if (payrollExport == null) {
            workShiftRepository.setPayrollExport(
                workShiftEntity = workShift,
                payrollExportEntity = null
            )
        }
    }
}