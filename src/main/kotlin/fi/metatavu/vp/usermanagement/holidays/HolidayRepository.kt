package fi.metatavu.vp.usermanagement.holidays

import fi.metatavu.vp.usermanagement.model.CompensationType
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import java.time.LocalDate
import java.util.UUID

@ApplicationScoped
class HolidayRepository: AbstractRepository<HolidayEntity, UUID>() {

    /**
     * Lists holidays
     *
     * @param year year
     * @param first first result
     * @param max max results
     * @return pair of holidays and count
     */
    suspend fun list(year: Int? = null, first: Int? = null, max: Int? = null): Pair<List<HolidayEntity>, Long> {
        return if (year != null) {
            queryWithCount(
                find("year(date) = ?1", year),
                first,
                max
            )
        } else {
            listAllSuspending(first, max, null)
        }
    }

    /**
     * Finds holiday by date
     *
     * @param date date
     * @return found holiday or null if not found
     */
    suspend fun findByDate(date: LocalDate): HolidayEntity? {
        return find("date = ?1", date).firstResult<HolidayEntity>().awaitSuspending()
    }

    /**
     * Creates a new holiday
     *
     * @param id id
     * @param name name
     * @param date date
     * @param compensationType compensation type
     * @param creatorId creator id
     * @return created holiday
     */
    suspend fun create(
        id: UUID,
        name: String,
        date: LocalDate,
        compensationType: CompensationType,
        creatorId: UUID
    ): HolidayEntity {
        val holidayEntity = HolidayEntity()
        holidayEntity.id = id
        holidayEntity.name = name
        holidayEntity.date = date
        holidayEntity.compensationType = compensationType
        holidayEntity.creatorId = creatorId
        holidayEntity.lastModifierId = creatorId

        return persistSuspending(holidayEntity)
    }

    /**
     * Updates holiday
     *
     * @param holidayEntity holiday entity
     * @param newName new name
     * @param newDate new date
     * @param newCompensationType new compensation type
     * @param lastModifierId last modifier id
     * @return updated holiday
     */
    suspend fun update(
        holidayEntity: HolidayEntity,
        newName: String,
        newDate: LocalDate,
        newCompensationType: CompensationType,
        lastModifierId: UUID
    ): HolidayEntity {
        holidayEntity.name = newName
        holidayEntity.date = newDate
        holidayEntity.compensationType = newCompensationType
        holidayEntity.lastModifierId = lastModifierId

        return persistSuspending(holidayEntity)
    }
}