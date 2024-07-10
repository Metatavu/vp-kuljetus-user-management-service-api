package fi.metatavu.vp.usermanagement.employees

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.api.model.Employee
import fi.metatavu.vp.api.model.EmployeeType
import fi.metatavu.vp.api.model.Office
import fi.metatavu.vp.api.model.SalaryGroup
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import fi.metatavu.vp.usermanagement.users.UserController.Companion.ARCHIVED_AT_ATTRIBUTE
import fi.metatavu.vp.usermanagement.users.UserController.Companion.DRIVER_CARD_ID_ATTRIBUTE
import fi.metatavu.vp.usermanagement.users.UserController.Companion.EMPLOYEE_NUMBER_ATTRIBUTE
import fi.metatavu.vp.usermanagement.users.UserController.Companion.EMPLOYEE_TYPE_ATTRIBUTE
import fi.metatavu.vp.usermanagement.users.UserController.Companion.LAST_READ_OUT_ATTRIBUTE
import fi.metatavu.vp.usermanagement.users.UserController.Companion.OFFICE_ATTRIBUTE
import fi.metatavu.vp.usermanagement.users.UserController.Companion.PHONE_NUMBER_ATTRIBUTE
import fi.metatavu.vp.usermanagement.users.UserController.Companion.REGULAR_WORKING_HOURS_ATTRIBUTE
import fi.metatavu.vp.usermanagement.users.UserController.Companion.SALARY_GROUP_ATTRIBUTE
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.*

/**
 * Translates user representation to employee entity
 */
@ApplicationScoped
class EmployeeTranslator : AbstractTranslator<UserRepresentation, Employee?>() {

    override suspend fun translate(entity: UserRepresentation): Employee? {
        val salaryGroup = SalaryGroup.decode(entity.attributes?.get(SALARY_GROUP_ATTRIBUTE)?.firstOrNull()) ?: return null
        val type = EmployeeType.decode(entity.attributes?.get(EMPLOYEE_TYPE_ATTRIBUTE)?.firstOrNull()) ?: return null
        val office = Office.decode(entity.attributes?.get(OFFICE_ATTRIBUTE)?.firstOrNull()) ?: return null

        return Employee(
            id = UUID.fromString(entity.id),
            firstName = entity.firstName ?: "",
            lastName = entity.lastName ?: "",
            employeeNumber = entity.attributes?.get(EMPLOYEE_NUMBER_ATTRIBUTE)?.firstOrNull() ?: "",
            salaryGroup = salaryGroup,
            type = type,
            driverCardLastReadOut = entity.attributes?.get(LAST_READ_OUT_ATTRIBUTE)?.get(0)?.let { OffsetDateTime.parse(it) },
            office = office,
            driverCardId = entity.attributes?.get(DRIVER_CARD_ID_ATTRIBUTE)?.firstOrNull() ?: "",
            regularWorkingHours = entity.attributes?.get(REGULAR_WORKING_HOURS_ATTRIBUTE)?.firstOrNull()?.toFloat(),
            archivedAt = entity.attributes?.get(ARCHIVED_AT_ATTRIBUTE)?.get(0)?.let { OffsetDateTime.parse(it) },
            email = entity.email,
            phoneNumber = entity.attributes?.get(PHONE_NUMBER_ATTRIBUTE)?.firstOrNull(),
        )
    }

}