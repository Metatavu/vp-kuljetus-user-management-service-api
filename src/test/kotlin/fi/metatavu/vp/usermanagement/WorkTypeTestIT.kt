package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.WorkType
import fi.metatavu.vp.test.client.models.WorkTypeCategory
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Work Types API tests
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class WorkTypeTestIT : AbstractFunctionalTest() {

    @Test
    fun testList() = createTestBuilder().use {
        it.manager.workTypes.createWorkType(
            WorkType(
                name = "test",
                category = WorkTypeCategory.OFFICE
            )
        )
        it.manager.workTypes.createWorkType(
            WorkType(
                name = "test",
                category = WorkTypeCategory.DRIVER
            )
        )

        val workTypes = it.manager.workTypes.listWorkTypes()
        assertEquals(2, workTypes.size)

        val workTypesOffice = it.manager.workTypes.listWorkTypes(WorkTypeCategory.OFFICE)
        assertEquals(1, workTypesOffice.size)
    }

    @Test
    fun testCreate() = createTestBuilder().use { tb ->
        val data = WorkType(
            name = "test",
            category = WorkTypeCategory.OFFICE
        )
        val created = tb.manager.workTypes.createWorkType(data)
        assertNotNull(created.id)
        assertEquals(data.name, created.name)
        assertEquals(data.category, created.category)

        //duplicates
        tb.manager.workTypes.assertCreateFail(data, 409)

        //access rights
        tb.driver1.workTypes.assertCreateFail(data, 403)
    }

    @Test
    fun testFind() = createTestBuilder().use {
        val created = it.manager.workTypes.createWorkType(
            WorkType(
                name = "test",
                category = WorkTypeCategory.OFFICE
            )
        )
        val found = it.manager.workTypes.findWorkType(created.id!!)
        assertEquals(created.id, found.id)
        assertEquals(created.name, found.name)
        assertEquals(created.category, found.category)

        //access rights
        it.driver1.workTypes.assertFindFail(found.id!!, 403)
    }

    @Test
    fun testDelete() = createTestBuilder().use {
        val created = it.manager.workTypes.createWorkType(
            WorkType(
                name = "test",
                category = WorkTypeCategory.OFFICE
            )
        )
        //access rights
        it.driver1.workTypes.assertDeleteFail(created.id!!, 403)

        it.manager.workTypes.deleteWorkType(created.id)
        it.manager.workTypes.assertFindFail(created.id, 404)
    }


}