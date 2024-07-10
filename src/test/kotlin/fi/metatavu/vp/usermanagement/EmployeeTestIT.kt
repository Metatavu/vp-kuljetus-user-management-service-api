package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.Employee
import fi.metatavu.vp.test.client.models.EmployeeType
import fi.metatavu.vp.test.client.models.Office
import fi.metatavu.vp.test.client.models.SalaryGroup
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*

/**
 * Tests for Employee API
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class EmployeeTestIT : AbstractFunctionalTest() {

    @Test
    fun list() = createTestBuilder().use {
        val testEmployee = Employee(
            firstName = "Test",
            lastName = "Employee",
            type = EmployeeType.AH,
            office = Office.KOTKA,
            salaryGroup = SalaryGroup.DRIVER,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "001",
            regularWorkingHours = 8.0f,
            employeeNumber = "001"
        )
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "002"))
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "003"))
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "004", driverCardId = "002").copy(type = EmployeeType.AP, office = Office.RAUHA, salaryGroup = SalaryGroup.OFFICE))
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "005",firstName = "Timo", lastName = "Testaaja"))
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "006", firstName = "Mikko", lastName = "Testaaja"))
        it.manager.employees.createEmployee(testEmployee.copy(employeeNumber = "007", firstName = "Archived", lastName = "Archived", archivedAt = OffsetDateTime.now().toString()))

        val bySearch = it.manager.employees.listEmployees("Timo")
        assertEquals(1, bySearch.size)
        val bySearch2 = it.manager.employees.listEmployees("Testaaja")
        assertEquals(2, bySearch2.size)
        val bySearch3 = it.manager.employees.listEmployees("Test")
        assertEquals(5, bySearch3.size)

        val byType = it.manager.employees.listEmployees(type = EmployeeType.AH)
        assertEquals(4, byType.size)

        val byOffice = it.manager.employees.listEmployees(office = Office.KOTKA)
        assertEquals(4, byOffice.size)

        val bySalaryGroup = it.manager.employees.listEmployees(salaryGroup = SalaryGroup.OFFICE)
        assertEquals(1, bySalaryGroup.size)

        val byArchived = it.manager.employees.listEmployees(archived = true)
        assertEquals(1, byArchived.size)

        val byArchived2 = it.manager.employees.listEmployees()
        assertEquals(5, byArchived2.size)

        val paging = it.manager.employees.listEmployees(first = 0, max = 2)
        assertEquals(2, paging.size)
        val paging2 = it.manager.employees.listEmployees(first = 2, max = 2)
        assertEquals(2, paging2.size)
        val paging3 = it.manager.employees.listEmployees(first = 4, max = 2)
        assertEquals(1, paging3.size)

        val bySalaryPaging = it.manager.employees.listEmployees(salaryGroup = SalaryGroup.OFFICE, max = 1, first = 1)
        val bySalaryPaging2 = it.manager.employees.listEmployees(salaryGroup = SalaryGroup.OFFICE, max = 1, first = 0)
        assertEquals(0, bySalaryPaging.size)
        assertEquals(1, bySalaryPaging2.size)
    }

    @Test
    fun create() = createTestBuilder().use {
        val testEmployee = Employee(
            firstName = "Test",
            lastName = "Employee",
            type = EmployeeType.AH,
            office = Office.KOTKA,
            salaryGroup = SalaryGroup.DRIVER,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "001",
            regularWorkingHours = 8.0f,
            employeeNumber = "001",
            phoneNumber = "123456789",
            email = "text@example.com"
        )
        val created = it.manager.employees.createEmployee(testEmployee)
        assertNotNull(created.id)
        assertEquals(testEmployee.firstName, created.firstName)
        assertEquals(testEmployee.lastName, created.lastName)
        assertEquals(testEmployee.type, created.type)
        assertEquals(testEmployee.office, created.office)
        assertEquals(testEmployee.salaryGroup, created.salaryGroup)
        assertEquals(
            OffsetDateTime.parse(testEmployee.driverCardLastReadOut!!).toEpochSecond(),
            OffsetDateTime.parse(created.driverCardLastReadOut!!).toEpochSecond()
        )
        assertEquals(testEmployee.driverCardId, created.driverCardId)
        assertEquals(testEmployee.regularWorkingHours, created.regularWorkingHours)
        assertEquals(testEmployee.employeeNumber, created.employeeNumber)
        assertEquals(testEmployee.phoneNumber, created.phoneNumber)
        assertEquals(testEmployee.email, created.email)
    }

    @Test
    fun findFail() = createTestBuilder().use {
        it.manager.employees.assertFindServerFail(UUID.fromString("95dd89a2-da9a-4ce4-979d-8897b7603b2e"), 500)
    }

    @Test
    fun find() = createTestBuilder().use {
        val created = it.manager.employees.createEmployee(Employee(
            firstName = "Test",
            lastName = "Employee",
            type = EmployeeType.AH,
            office = Office.KOTKA,
            salaryGroup = SalaryGroup.DRIVER,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "001",
            regularWorkingHours = 8.0f,
            employeeNumber = "001",
            phoneNumber = "123456789",
            email = "text@example.com"
        ))

        val found = it.manager.employees.findEmployee(created.id!!)
        assertEquals(created.id, found.id)
        assertEquals(created.firstName, found.firstName)
        assertEquals(created.lastName, found.lastName)
        assertEquals(created.type, found.type)
        assertEquals(created.office, found.office)
        assertEquals(created.salaryGroup, found.salaryGroup)
        assertEquals(
            OffsetDateTime.parse(created.driverCardLastReadOut!!).toEpochSecond(),
            OffsetDateTime.parse(found.driverCardLastReadOut!!).toEpochSecond()
        )
        assertEquals(created.driverCardId, found.driverCardId)
        assertEquals(created.regularWorkingHours, found.regularWorkingHours)
    }

    @Test
    fun update() = createTestBuilder().use {
        val created = it.manager.employees.createEmployee(Employee(
            firstName = "Test",
            lastName = "Employee",
            type = EmployeeType.AH,
            office = Office.KOTKA,
            salaryGroup = SalaryGroup.DRIVER,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "001",
            regularWorkingHours = 8.0f,
            employeeNumber = "001"
        ))

        val updateData = created.copy(
            firstName = "Updated",
            lastName = "Updated",
            type = EmployeeType.AP,
            office = Office.RAUHA,
            salaryGroup = SalaryGroup.OFFICE,
            driverCardLastReadOut = OffsetDateTime.now().toString(),
            driverCardId = "002",
            regularWorkingHours = 7.5f,
            phoneNumber = "123456789",
            email = "text@example.com"
        )

        val updated = it.manager.employees.updateEmployee(created.id!!, updateData)

        assertEquals(created.id, updated.id)
        assertEquals(updateData.firstName, updated.firstName)
        assertEquals(updateData.lastName, updated.lastName)
        assertEquals(updateData.type, updated.type)
        assertEquals(updateData.office, updated.office)
        assertEquals(updateData.salaryGroup, updated.salaryGroup)
        assertEquals(
            OffsetDateTime.parse(updateData.driverCardLastReadOut!!).toEpochSecond(),
            OffsetDateTime.parse(updated.driverCardLastReadOut!!).toEpochSecond()
        )
        assertEquals(updateData.driverCardId, updated.driverCardId)
        assertEquals(updateData.regularWorkingHours, updated.regularWorkingHours)
        assertEquals(updateData.employeeNumber, updated.employeeNumber)
        assertEquals(updateData.phoneNumber, updated.phoneNumber)
        assertEquals(updateData.email, updated.email)

        // Test that the unique usernames are still generated no matter the first+last name updates
        assertNotNull(it.manager.employees.createEmployee(created.copy(employeeNumber = "003")))
        assertNotNull(it.manager.employees.updateEmployee(created.id, created.copy(firstName = "updated1", lastName = "updated1")))
        assertNotNull(it.manager.employees.createEmployee(created.copy(employeeNumber = "004")))
        assertNotNull(it.manager.employees.createEmployee(created.copy(employeeNumber = "005", firstName = "updated1", lastName = "updated1")))
        assertEquals(4, it.manager.employees.listEmployees().size)

        // Tests that updates/creations of already existing employee numbers is not allowed
        it.manager.employees.assertCreateFail(created, 400)
        val anotherCreatedUser = it.manager.employees.createEmployee(created.copy(employeeNumber = "006"))
        it.manager.employees.assertUpdateFail(anotherCreatedUser.id!!, anotherCreatedUser.copy(employeeNumber = "003"),400)

    }

}