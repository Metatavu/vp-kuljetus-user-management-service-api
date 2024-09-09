package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.CompensationType
import fi.metatavu.vp.test.client.models.Holiday
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class HolidayTestIT: AbstractFunctionalTest() {

    @Test
    fun testList() = createTestBuilder().use {
        for (i in 0..10) {
            val date = LocalDate.now().let { now ->
                if (i == 0) now.plusYears(1)
                else now.plusDays(i.toLong())
            }

            it.manager.holidays.create(
                holiday = Holiday(
                    name = "Holiday $i",
                    date = date.toString(),
                    compensationType = CompensationType.PUBLIC_HOLIDAY_ALLOWANCE
                )
            )
        }

        val foundHolidays = it.manager.holidays.listHolidays()

        assertEquals(10, foundHolidays.size)

        val foundHolidaysByYear = it.manager.holidays.listHolidays(year = LocalDate.now().plusYears(1).year)

        assertEquals(1, foundHolidaysByYear.size)

    }

    @Test
    fun testCreate() = createTestBuilder().use {
        val today = LocalDate.now()
        val holiday = it.manager.holidays.create(
            holiday = Holiday(
                name = "Test holiday",
                date = today.toString(),
                compensationType = CompensationType.PUBLIC_HOLIDAY_ALLOWANCE
            )
        )

        assertEquals("Test holiday", holiday.name)
        assertEquals(today.toString(), holiday.date)
        assertEquals(CompensationType.PUBLIC_HOLIDAY_ALLOWANCE, holiday.compensationType)

        it.manager.holidays.assertCreateFail(
            expectedStatus = 400,
            holiday = holiday.copy(id = null)
        )
    }

    @Test
    fun testUpdate() = createTestBuilder().use {
        val today = LocalDate.now()
        val createdHoliday1 = it.manager.holidays.create(
            holiday = Holiday(
                name = "Test holiday 1",
                date = today.plusDays(1).toString(),
                compensationType = CompensationType.PUBLIC_HOLIDAY_ALLOWANCE
            )
        )
        val createdHoliday2 = it.manager.holidays.create(
            holiday = Holiday(
                name = "Test holiday",
                date = today.toString(),
                compensationType = CompensationType.PUBLIC_HOLIDAY_ALLOWANCE
            )
        )

        assertEquals("Test holiday", createdHoliday2.name)
        assertEquals(today.toString(), createdHoliday2.date)
        assertEquals(CompensationType.PUBLIC_HOLIDAY_ALLOWANCE, createdHoliday2.compensationType)

        val updatedHoliday = it.manager.holidays.updateHoliday(
            holidayId = createdHoliday2.id!!,
            holiday = createdHoliday2.copy(
                date = today.plusMonths(1).toString(),
                name = "Updated holiday",
                compensationType = CompensationType.DAY_OFF_WORK_ALLOWANCE
            )
        )

        assertEquals("Updated holiday", updatedHoliday.name)
        assertEquals(today.plusMonths(1).toString(), updatedHoliday.date)
        assertEquals(CompensationType.DAY_OFF_WORK_ALLOWANCE, updatedHoliday.compensationType)

        val foundHoliday = it.manager.holidays.findHoliday(createdHoliday2.id)

        assertEquals("Updated holiday", foundHoliday.name)
        assertEquals(today.plusMonths(1).toString(), foundHoliday.date)
        assertEquals(CompensationType.DAY_OFF_WORK_ALLOWANCE, foundHoliday.compensationType)

        it.manager.holidays.assertUpdateFail(
            expectedStatus = 400,
            holidayId = createdHoliday2.id,
            holiday = createdHoliday2.copy(id = UUID.randomUUID())
        )


        it.manager.holidays.assertUpdateFail(
            expectedStatus = 400,
            holidayId = createdHoliday2.id,
            holiday = createdHoliday2.copy(date = createdHoliday1.date)
        )
    }

    @Test
    fun testDelete() = createTestBuilder().use {
        val today = LocalDate.now()
        val holiday = it.manager.holidays.create(
            holiday = Holiday(
                name = "Test holiday",
                date = today.toString(),
                compensationType = CompensationType.PUBLIC_HOLIDAY_ALLOWANCE
            )
        )

        assertEquals("Test holiday", holiday.name)
        assertEquals(today.toString(), holiday.date)
        assertEquals(CompensationType.PUBLIC_HOLIDAY_ALLOWANCE, holiday.compensationType)

        it.manager.holidays.deleteHoliday(holiday.id!!)

        it.manager.holidays.assertFindFail(
            expectedStatus = 404,
            holidayId = holiday.id
        )

        it.manager.holidays.assertDeleteFail(
            expectedStatus = 404,
            holidayId = holiday.id
        )

        it.manager.holidays.assertDeleteFail(
            expectedStatus = 404,
            holidayId = UUID.randomUUID()
        )
    }

    @Test
    fun testFind() = createTestBuilder().use {
        val today = LocalDate.now()
        val holiday = it.manager.holidays.create(
            holiday = Holiday(
                name = "Test holiday",
                date = today.toString(),
                compensationType = CompensationType.PUBLIC_HOLIDAY_ALLOWANCE
            )
        )

        assertEquals("Test holiday", holiday.name)
        assertEquals(today.toString(), holiday.date)
        assertEquals(CompensationType.PUBLIC_HOLIDAY_ALLOWANCE, holiday.compensationType)

        val foundHoliday = it.manager.holidays.findHoliday(holiday.id!!)

        assertEquals("Test holiday", foundHoliday.name)
        assertEquals(today.toString(), foundHoliday.date)
        assertEquals(CompensationType.PUBLIC_HOLIDAY_ALLOWANCE, foundHoliday.compensationType)

        it.manager.holidays.assertFindFail(
            expectedStatus = 404,
            holidayId = UUID.randomUUID()
        )
    }
}