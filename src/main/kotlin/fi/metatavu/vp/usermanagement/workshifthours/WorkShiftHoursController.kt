package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import io.quarkus.scheduler.Scheduled
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.asUni
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.dispatcher
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import org.jboss.logging.Logger
import java.time.OffsetDateTime
import java.util.*

/**
 * Controller for Work Shift Hours
 */
@ApplicationScoped
class WorkShiftHoursController {

    @Inject
    lateinit var workShiftHoursRepository: WorkShiftHoursRepository

    @Inject
    lateinit var vertx: Vertx

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
        val allFoundHours = workShiftHoursRepository.listWorkShiftHours(
            employeeId = employeeId,
            workShift = workShiftFilter,
            workType = workType,
            employeeWorkShiftStartedAfter = employeeWorkShiftStartedAfter,
            employeeWorkShiftStartedBefore = employeeWorkShiftStartedBefore
        )

        // Calculate the missing records
        val finalHours = allFoundHours.first.map {
            if (it.calculatedHours == null) {
                it.calculatedHours = calculateHours(it)
                workShiftHoursRepository.persistSuspending(it)
            } else {
                it
            }
        }
        return Pair(finalHours, allFoundHours.second)
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
     * Recalculates work shift hours for a work shift
     *
     * @param workShift work shift
     */
    suspend fun recalculateWorkShiftHours(workShift: WorkShiftEntity) {
        workShiftHoursRepository.listWorkShiftHours(workShift = workShift).first.forEach {
            it.calculatedHours = calculateHours(it)
            workShiftHoursRepository.persistSuspending(it)
        }
    }

    /**
     * TODO: calculates work shift hours
     *
     * @param it work shift hours entity
     * @return calculated hours
     */
    private fun calculateHours(it: WorkShiftHoursEntity): Float? {
        return null
    }

    /**
     * TODO: calculates null work shift hours
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Scheduled(
        every = "\${fillWorkShiftHours.every.expr}",
        delayed = "\${fillWorkShiftHours.delay.expr}",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    fun calculateWorkShiftHours(): Uni<Void> {
        return CoroutineScope(vertx.dispatcher()).async {
        }.asUni().replaceWithVoid()
    }
}