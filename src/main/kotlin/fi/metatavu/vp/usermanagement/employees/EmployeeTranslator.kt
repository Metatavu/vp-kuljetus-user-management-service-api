package fi.metatavu.vp.usermanagement.employees

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.model.Employee
import fi.metatavu.vp.usermanagement.model.EmployeeType
import fi.metatavu.vp.usermanagement.model.Office
import fi.metatavu.vp.usermanagement.model.SalaryGroup
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
class EmployeeTranslator : AbstractTranslator<UserRepresentation, Employee>() {

    override suspend fun translate(entity: UserRepresentation): Employee {
        val salaryGroup = SalaryGroup.valueOf(entity.attributes!![SALARY_GROUP_ATTRIBUTE]!!.first())
        val type = EmployeeType.valueOf(entity.attributes[EMPLOYEE_TYPE_ATTRIBUTE]!!.first())
        val office = Office.valueOf(entity.attributes[OFFICE_ATTRIBUTE]!!.first())

        return Employee(
            id = UUID.fromString(entity.id),
            firstName = entity.firstName ?: "",
            lastName = entity.lastName ?: "",
            employeeNumber = entity.attributes[EMPLOYEE_NUMBER_ATTRIBUTE]?.firstOrNull() ?: "",
            salaryGroup = salaryGroup,
            type = type,
            driverCardLastReadOut = entity.attributes[LAST_READ_OUT_ATTRIBUTE]?.get(0)?.let { OffsetDateTime.parse(it) },
            office = office,
            driverCardId = entity.attributes[DRIVER_CARD_ID_ATTRIBUTE]?.firstOrNull() ?: "",
            regularWorkingHours = entity.attributes[REGULAR_WORKING_HOURS_ATTRIBUTE]?.firstOrNull()?.toFloat(),
            archivedAt = entity.attributes[ARCHIVED_AT_ATTRIBUTE]?.get(0)?.let { OffsetDateTime.parse(it) },
            email = entity.email,
            phoneNumber = entity.attributes[PHONE_NUMBER_ATTRIBUTE]?.firstOrNull(),
        )
    }

}