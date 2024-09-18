package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.model.WorkShiftHours
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Duration

/**
 * Translator for WorkShiftHours
 */
@ApplicationScoped
class WorkShiftHoursTranslator: AbstractTranslator<WorkShiftHoursEntity, WorkShiftHours>() {

    @Inject
    lateinit var workEventController: WorkEventController

    override suspend fun translate(entity: WorkShiftHoursEntity): WorkShiftHours {
        return WorkShiftHours(
            id = entity.id,
            employeeId = entity.workShift.employeeId,
            workEventType = entity.workEventType,
            actualHours = entity.actualHours,
            employeeWorkShiftId = entity.workShift.id,
            calculatedHours = calculateHours(entity, entity.workEventType)
        )
    }

    /**
     * Calculates hours for work shift hours. If there are no work events or only one, returns 0.
     * If it is the last work event, returns 0.
     *
     * @param entity work shift hours entity
     * @param type work event type
     * @return hours
     */
    private suspend fun calculateHours(entity: WorkShiftHoursEntity, type: WorkEventType): Float {
        val (workEvents, _) = workEventController.list(
            employeeWorkShift = entity.workShift
        )
        if (workEvents.isEmpty() || workEvents.size == 1) {
            return 0f
        }
        var hours = 0f
        workEvents.reversed().forEachIndexed { index, workEvent ->
            if (workEvent.workEventType.value == type.value && index != workEvents.size-1) {
                val nextRecord = workEvents[index+1]
                val time = Duration.between(workEvent.time, nextRecord.time).abs()
                hours += time.toHours().toFloat()
            }
        }
        return hours
    }
}