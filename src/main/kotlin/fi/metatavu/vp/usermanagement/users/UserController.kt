package fi.metatavu.vp.usermanagement.users

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.model.Employee
import fi.metatavu.vp.usermanagement.model.EmployeeType
import fi.metatavu.vp.usermanagement.model.Office
import fi.metatavu.vp.usermanagement.model.SalaryGroup
import fi.metatavu.vp.usermanagement.keycloak.KeycloakAdminClient
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

/**
 * manages users
 */
@ApplicationScoped
class UserController {

    @Inject
    lateinit var keycloakAdminClient: KeycloakAdminClient

    /**
     * Finds a user by id
     *
     * @param id id
     * @param role user role
     * @return found user or null if not found
     */
    suspend fun find(id: UUID, role: String? = null): UserRepresentation? {
        val user = keycloakAdminClient.findUserById(id)
        if (role != null && user?.realmRoles?.contains(role) == false) {
            return null
        }

        return user
    }

    /**
     * Lists employees with filters
     *
     * @param search search string
     * @param salaryGroup salary group
     * @param type employee type
     * @param office office
     * @param archived archived status
     * @param first first result
     * @param max max results
     * @return employees and total count
     */
    suspend fun listEmployees(
        search: String?,
        salaryGroup: SalaryGroup?,
        type: EmployeeType?,
        office: Office?,
        archived: Boolean?,
        first: Int,
        max: Int
    ): Pair<List<UserRepresentation>, Int> {
        val query = StringBuilder()
        if (salaryGroup != null) {
            if (query.isNotEmpty()) query.append(" ")
            query.append("$SALARY_GROUP_ATTRIBUTE:${salaryGroup.value}")
        }
        if (type != null) {
            if (query.isNotEmpty()) query.append(" ")
            query.append("$EMPLOYEE_TYPE_ATTRIBUTE:${type.value}")
        }
        if (office != null) {
            if (query.isNotEmpty()) query.append(" ")
            query.append("$OFFICE_ATTRIBUTE:${office.value}")
        }

        // List users according to the query
        val filteredUsersAllRolesNoPaging = keycloakAdminClient.getUsersApi().realmUsersGet(
            realm = keycloakAdminClient.getRealm(),
            search = search,
            q = query.toString(),
            enabled = archived != true
        )

        // List all users of the role
        val allEmployeeUsers = keycloakAdminClient.listUsersOfRole(
            role = AbstractApi.EMPLOYEE_ROLE,
        ).toList()
        val filteredEmployeeUsersAll = allEmployeeUsers
            .filter { user -> filteredUsersAllRolesNoPaging.any { it.id == user.id } }

        // do paging
        val maxIndex = if (filteredEmployeeUsersAll.size < first + max) {
            filteredEmployeeUsersAll.size
        } else {
            first + max
        }
        if (maxIndex < first) return emptyList<UserRepresentation>() to 0
        val pagedFilteredUsers = filteredEmployeeUsersAll.subList(first, maxIndex)

        return pagedFilteredUsers to filteredEmployeeUsersAll.size
    }

    /**
     * Lists drivers
     *
     * @param driverCardId driver card id
     * @param archived archived status
     * @param first first result
     * @param max max results
     * @return drivers and total count
     */
    suspend fun listDrivers(
        driverCardId: String?,
        archived: Boolean?,
        first: Int?,
        max: Int?
    ): Pair<List<UserRepresentation>, Int> {
        if (driverCardId != null) {
            // no need for paging when driver card id is filtered because 1 result is expected
            keycloakAdminClient.findUserByDriverCardId(driverCardId).let {
                val users = if (archived != null) {
                    it.filter { user -> user.enabled != archived }
                } else it.toList()
                return users to users.size
            }
        }

        val allRoleUsers = keycloakAdminClient.listUsersOfRole(
            role = AbstractApi.DRIVER_ROLE,
        ).toList()

        val pagedUsers = if (first != null || max != null) {
            val firstResult = first ?: 0
            val maxResults = max ?: 10
            val maxIndex = if (allRoleUsers.size < firstResult + maxResults) {
                allRoleUsers.size
            } else {
                maxResults + firstResult
            }
            if (maxIndex < firstResult) return emptyList<UserRepresentation>() to 0
            allRoleUsers.subList(firstResult, maxIndex)
        } else {
            allRoleUsers
        }

        return if (archived == true) {
            pagedUsers.filter { it.enabled == false } to allRoleUsers.filter { it.enabled == false }.size
        } else {
            pagedUsers.filter { it.enabled == true } to allRoleUsers.filter { it.enabled == true }.size
        }
    }

    /**
     * Creates an employee
     *
     * @param employee employee
     * @return created employee or null if failed
     */
    suspend fun createEmployee(employee: Employee): UserRepresentation? {
        val sameNamedUsers = keycloakAdminClient.getUsersApi().realmUsersGet(
            realm = keycloakAdminClient.getRealm(),
            search = "${employee.firstName}${employee.lastName}",
            exact = false
        )

        val selectedUsername = generateUniqueUsername(employee.firstName, employee.lastName, sameNamedUsers)

        // Construct attributes
        val originalAttributes = buildAttributes(employee)
        if (employee.office != Office.KOTKA) originalAttributes[SALARY_GROUP_ATTRIBUTE] =
            arrayOf(SalaryGroup.DRIVER.value)

        val userRepresentation = UserRepresentation(
            firstName = employee.firstName,
            lastName = employee.lastName,
            username = selectedUsername,
            enabled = employee.archivedAt == null,
            attributes = buildAttributes(employee),
            email = employee.email
        )
        try {
            keycloakAdminClient.getUsersApi().realmUsersPost(
                realm = keycloakAdminClient.getRealm(),
                userRepresentation = userRepresentation
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        val keycloakUser = keycloakAdminClient.getUsersApi().realmUsersGet(
            realm = keycloakAdminClient.getRealm(),
            username = selectedUsername,
            briefRepresentation = false
        ).firstOrNull()
        assignRole(keycloakUser!!, AbstractApi.EMPLOYEE_ROLE)
        return keycloakUser
    }

    /**
     * Updates an employee
     *
     * @param found found employee
     * @param employee employee
     * @return updated employee or null if failed
     */
    suspend fun updateEmployee(found: UserRepresentation, employee: Employee): UserRepresentation? {
        val updatedUserRepresentation = found.copy(
            attributes = buildAttributes(employee),
            firstName = employee.firstName,
            lastName = employee.lastName,
            enabled = employee.archivedAt == null,
            email = employee.email
        )

        try {
            keycloakAdminClient.getUserApi().realmUsersIdPut(
                realm = keycloakAdminClient.getRealm(),
                id = found.id.toString(),
                userRepresentation = updatedUserRepresentation
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return keycloakAdminClient.getUserApi().realmUsersIdGet(
            realm = keycloakAdminClient.getRealm(),
            id = found.id.toString()
        )
    }

    /**
     * Update user role
     *
     * @param userRepresentation user representation
     * @param role role to assign
     */
    suspend fun assignRole(userRepresentation: UserRepresentation, role: String) {
        val userRoles = keycloakAdminClient.getRoleContainerApi().realmRolesRoleNameGet(
            roleName = role,
            realm = keycloakAdminClient.getRealm()
        )

        keycloakAdminClient.getRoleMapperApi().realmUsersIdRoleMappingsRealmPost(
            id = userRepresentation.id.toString(),
            realm = keycloakAdminClient.getRealm(),
            roleRepresentation = arrayOf(userRoles)
        )
    }

    /**
     * Deletes an employee
     *
     * @param id employee id
     */
    suspend fun deleteEmployee(id: UUID) {
        try {
            keycloakAdminClient.getUserApi().realmUsersIdDelete(
                realm = keycloakAdminClient.getRealm(),
                id = id.toString()
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    /**
     * Finds employees with the same employee number
     *
     * @param employeeNumber employee number
     * @return employees with the same employee number
     */
    suspend fun findEmployeeNumberDuplicate(employeeNumber: String): Array<UserRepresentation> {
        val users = keycloakAdminClient.getUsersApi().realmUsersGet(
            realm = keycloakAdminClient.getRealm(),
            q = "$EMPLOYEE_NUMBER_ATTRIBUTE:$employeeNumber"
        )
        return users
    }

    /**
     * Builds user attributes
     *
     * @param employee employee
     * @return user attributes
     */
    private fun buildAttributes(employee: Employee): MutableMap<String, Array<String>> {
        val attributes = mutableMapOf<String, Array<String>>()
        attributes[EMPLOYEE_TYPE_ATTRIBUTE] = arrayOf(employee.type.value)
        attributes[SALARY_GROUP_ATTRIBUTE] = arrayOf(employee.salaryGroup.value)
        attributes[OFFICE_ATTRIBUTE] = arrayOf(employee.office.value)
        attributes[EMPLOYEE_NUMBER_ATTRIBUTE] = arrayOf(employee.employeeNumber)
        if (employee.driverCardId != null) {
            attributes[DRIVER_CARD_ID_ATTRIBUTE] = arrayOf(employee.driverCardId)
        } else {
            attributes[DRIVER_CARD_ID_ATTRIBUTE] = emptyArray()
        }
        if (employee.regularWorkingHours != null) {
            attributes[REGULAR_WORKING_HOURS_ATTRIBUTE] = arrayOf(employee.regularWorkingHours.toString())
        } else {
            attributes[REGULAR_WORKING_HOURS_ATTRIBUTE] = emptyArray()
        }
        if (employee.archivedAt != null) {
            attributes[ARCHIVED_AT_ATTRIBUTE] = arrayOf(employee.archivedAt.toString())
        } else {
            attributes[ARCHIVED_AT_ATTRIBUTE] = emptyArray()
        }
        if (employee.driverCardLastReadOut != null) {
            attributes[LAST_READ_OUT_ATTRIBUTE] = arrayOf(employee.driverCardLastReadOut.toString())
        } else {
            attributes[LAST_READ_OUT_ATTRIBUTE] = emptyArray()
        }
        if (employee.phoneNumber != null) {
            attributes[PHONE_NUMBER_ATTRIBUTE] = arrayOf(employee.phoneNumber)
        } else {
            attributes[PHONE_NUMBER_ATTRIBUTE] = emptyArray()
        }
        if (employee.pinCode != null) {
            attributes[PIN_CODE_ATTRIBUTE] = arrayOf(employee.pinCode)
        } else {
            attributes[PIN_CODE_ATTRIBUTE] = emptyArray()
        }
        return attributes
    }

    /**
     * Generates a unique username
     *
     * @param firstName first name
     * @param lastName last name
     * @param sameNamedUsers users with the same name
     * @return unique username
     */
    private fun generateUniqueUsername(firstName: String, lastName: String, sameNamedUsers: Array<UserRepresentation>): String {
        val baseUsername = firstName + lastName
        if (sameNamedUsers.isEmpty()) return baseUsername
        var uniqueUsername = baseUsername
        var counter = 1

        while (sameNamedUsers.any { it.username.equals(uniqueUsername, ignoreCase = true) }) {
            uniqueUsername = "$baseUsername$counter"
            counter++
        }

        return uniqueUsername
    }

    companion object {
        const val SALARY_GROUP_ATTRIBUTE = "salaryGroup"
        const val DRIVER_CARD_ID_ATTRIBUTE = "driverCardId"
        const val EMPLOYEE_TYPE_ATTRIBUTE = "employeeType"
        const val OFFICE_ATTRIBUTE = "office"
        const val REGULAR_WORKING_HOURS_ATTRIBUTE = "regularWorkingHours"
        const val ARCHIVED_AT_ATTRIBUTE = "archivedAt"
        const val LAST_READ_OUT_ATTRIBUTE = "lastReadOut"
        const val EMPLOYEE_NUMBER_ATTRIBUTE = "employeeNumber"
        const val PHONE_NUMBER_ATTRIBUTE = "phoneNumber"
        const val PIN_CODE_ATTRIBUTE = "pinCode"
    }

}