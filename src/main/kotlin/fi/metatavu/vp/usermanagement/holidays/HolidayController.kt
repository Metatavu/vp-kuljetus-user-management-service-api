package fi.metatavu.vp.usermanagement.holidays

import fi.metatavu.vp.usermanagement.model.Holiday
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.LocalDate
import java.util.*

@ApplicationScoped
class HolidayController {

    @Inject
    lateinit var holidayRepository: HolidayRepository

    /**
     * Finds holiday by id
     *
     * @param id holiday id
     * @return found holiday or null if not found
     */
    suspend fun find(id: UUID): HolidayEntity? {
        return holidayRepository.findByIdSuspending(id)
    }

    /**
     * Finds holiday with date
     *
     * @param date date
     * @return found holiday or null if not found
     */
    suspend fun findByDate(date: LocalDate): HolidayEntity? {
        return holidayRepository.findByDate(date)
    }

    /**
     * Lists holidays
     *
     * @param year year
     * @param first first result
     * @param max max results
     * @return pair of holidays and count
     */
    suspend fun list(year: Int? = null, first: Int? = null, max: Int? = null): Pair<List<HolidayEntity>, Long> {
        return holidayRepository.list(
            year = year,
            first = first,
            max = max
        )
    }

    /**
     * Creates a new holiday
     *
     * @param newHoliday new holiday
     * @param creatorId creator id
     * @return created holiday
     */
    suspend fun create(newHoliday: Holiday, creatorId: UUID): HolidayEntity {
        return holidayRepository.create(
            id = UUID.randomUUID(),
            name = newHoliday.name,
            date = newHoliday.date,
            compensationType = newHoliday.compensationType,
            creatorId = creatorId
        )
    }

    /**
     * Updates holiday
     *
     * @param holidayEntity holiday entity
     * @param updatedHoliday updated holiday
     * @param lastModifierId last modifier id
     * @return updated holiday
     */
    suspend fun update(holidayEntity: HolidayEntity, updatedHoliday: Holiday, lastModifierId: UUID): HolidayEntity {
        return holidayRepository.update(
            holidayEntity = holidayEntity,
            newName = updatedHoliday.name,
            newDate = updatedHoliday.date,
            newCompensationType = updatedHoliday.compensationType,
            lastModifierId = lastModifierId
        )
    }

    /**
     * Deletes holiday
     *
     * @param holidayEntity holiday entity
     */
    suspend fun delete(holidayEntity: HolidayEntity) {
        holidayRepository.deleteSuspending(holidayEntity)
    }
}