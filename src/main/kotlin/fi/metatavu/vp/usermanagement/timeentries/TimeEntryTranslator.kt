package fi.metatavu.vp.usermanagement.timeentries

import fi.metatavu.vp.api.model.TimeEntry
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import jakarta.enterprise.context.ApplicationScoped

/**
 * Translator for TimeEntry
 */
@ApplicationScoped
class TimeEntryTranslator : AbstractTranslator<TimeEntryEntity, TimeEntry>() {
    override suspend fun translate(entity: TimeEntryEntity): TimeEntry {
        return TimeEntry(
            id = entity.id,
            employeeId = entity.employeeId,
            startTime = entity.startTime,
            workEventType = entity.workEventType,
            endTime = entity.endTime
        )
    }
}