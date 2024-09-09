package fi.metatavu.vp.usermanagement.holidays

import fi.metatavu.vp.usermanagement.model.Holiday
import fi.metatavu.vp.usermanagement.spec.HolidaysApi
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import io.quarkus.hibernate.reactive.panache.common.WithSession
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.util.*

@RequestScoped
@WithSession
@Suppress("unused")
class HolidayApiImpl: HolidaysApi, AbstractApi() {

    @Inject
    lateinit var holidayController: HolidayController

    @Inject
    lateinit var holidayTranslator: HolidayTranslator

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun createHoliday(holiday: Holiday): Uni<Response> = withCoroutineScope {
        val userId = loggedUserId ?: return@withCoroutineScope createUnauthorized(UNAUTHORIZED)

        holidayController.findByDate(holiday.date)?.let {
            return@withCoroutineScope createBadRequest("Holiday with date ${holiday.date} already exists")
        }

        val createdHoliday = holidayController.create(holiday, userId)

        return@withCoroutineScope createOk(holidayTranslator.translate(createdHoliday))
    }

    @RolesAllowed(MANAGER_ROLE)
    override fun findHoliday(holidayId: UUID): Uni<Response> = withCoroutineScope {
        loggedUserId ?: return@withCoroutineScope createUnauthorized(UNAUTHORIZED)

        val foundHoliday = holidayController.find(holidayId)
            ?: return@withCoroutineScope createNotFound(NOT_FOUND_MESSAGE)

        return@withCoroutineScope createOk(holidayTranslator.translate(foundHoliday))
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun deleteHoliday(holidayId: UUID): Uni<Response> = withCoroutineScope {
        loggedUserId ?: return@withCoroutineScope createUnauthorized(UNAUTHORIZED)

        val foundHoliday = holidayController.find(holidayId)
            ?: return@withCoroutineScope createNotFound(NOT_FOUND_MESSAGE)

        holidayController.delete(foundHoliday)

        return@withCoroutineScope createNoContent()
    }

    @RolesAllowed(MANAGER_ROLE)
    override fun listHolidays(year: Int?, first: Int, max: Int): Uni<Response> = withCoroutineScope {
        loggedUserId ?: return@withCoroutineScope createUnauthorized(UNAUTHORIZED)

        val (holidays, count) = holidayController.list(
            year = year,
            first = first,
            max = max
        )

        return@withCoroutineScope createOk(holidayTranslator.translate(holidays), count)
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun updateHoliday(holidayId: UUID, holiday: Holiday): Uni<Response> = withCoroutineScope {
        val userId = loggedUserId ?: return@withCoroutineScope createUnauthorized(UNAUTHORIZED)

        val foundHoliday = holidayController.find(holidayId)
            ?: return@withCoroutineScope createNotFound(NOT_FOUND_MESSAGE)

        holidayController.findByDate(holiday.date)?.let {
            if (it.id != holidayId) {
                return@withCoroutineScope createBadRequest("Holiday with date ${holiday.date} already exists")
            }
        }

        val updatedHoliday = holidayController.update(
            holidayEntity = foundHoliday,
            updatedHoliday = holiday,
            lastModifierId = userId
        )

        return@withCoroutineScope createOk(holidayTranslator.translate(updatedHoliday))
    }
}