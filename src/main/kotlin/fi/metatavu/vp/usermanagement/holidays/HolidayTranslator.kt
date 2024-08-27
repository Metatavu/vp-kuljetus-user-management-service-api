package fi.metatavu.vp.usermanagement.holidays

import fi.metatavu.vp.api.model.Holiday
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import jakarta.enterprise.context.ApplicationScoped

/**
 * Translator for Holidays
 */
@ApplicationScoped
class HolidayTranslator: AbstractTranslator<HolidayEntity, Holiday>() {
    override suspend fun translate(entity: HolidayEntity): Holiday = Holiday(
        id = entity.id,
        name = entity.name,
        date = entity.date,
        compensationType = entity.compensationType,
        creatorId = entity.creatorId,
        lastModifierId = entity.lastModifierId,
        createdAt = entity.createdAt,
        modifiedAt = entity.modifiedAt
    )
}