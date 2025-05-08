package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.test.client.apis.PayrollExportsApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.models.PayrollExport
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import org.junit.Assert
import java.util.*

class PayrollExportTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    apiClient: ApiClient
) : ApiTestBuilderResource<PayrollExport, ApiClient>(testBuilder, apiClient) {

    override fun clean(t: PayrollExport) {}

    override fun getApi(): PayrollExportsApi {

        ApiClient.accessToken = accessTokenProvider?.accessToken
        return PayrollExportsApi(ApiTestSettings.apiBasePath)
    }

    /**
     * Lists payroll exports
     *
     * @param employeeId
     * @param exportedAfter
     * @param exportedBefore
     * @param first
     * @param max
     */
    fun listPayrollExports(
        employeeId: UUID? = null,
        exportedAfter: String? = null,
        exportedBefore: String? = null,
        first: Int? = 0,
        max: Int? = 10
    ): Array<PayrollExport> {
        return api.listPayrollExports(
            employeeId = employeeId,
            exportedAfter = exportedAfter,
            exportedBefore = exportedBefore,
            first = first,
            max = max
        )
    }

    /**
     * Creates payroll export
     *
     * @param payrollExport
     */
    fun createPayrollExport(payrollExport: PayrollExport): PayrollExport {
        return api.createPayrollExport(payrollExport)
    }

    /**
     * Finds payroll export
     *
     * @param id
     */
    fun findPayrollExport(id: UUID): PayrollExport {
        return api.findPayrollExport(id)
    }

    /**
     * Asserts payroll export creation fails with expected status
     *
     * @param payrollExport
     * @param expectedStatus
     */
    fun assertCreateFail(payrollExport: PayrollExport, expectedStatus: Int) {
        try {
            api.createPayrollExport(payrollExport)
            Assert.fail(String.format("Expected create to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Asserts payroll export find fails with expected status
     *
     * @param id
     * @param expectedStatus
     */
    fun assertFindFail(id: UUID, expectedStatus: Int) {
        try {
            api.findPayrollExport(id)
            Assert.fail(String.format("Expected find to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Asserts payroll export list fails with expected status
     *
     * @param employeeId
     * @param exportedAfter
     * @param exportedBefore
     * @param first
     * @param max
     * @param expectedStatus
     */
    fun assertListFail(
        employeeId: UUID? = null,
        exportedAfter: String? = null,
        exportedBefore: String? = null,
        first: Int? = 0,
        max: Int? = 10,
        expectedStatus: Int
    ) {
        try {
            api.listPayrollExports(
                employeeId = employeeId,
                exportedAfter = exportedAfter,
                exportedBefore = exportedBefore,
                first = first,
                max = max
            )
            Assert.fail(String.format("Expected list to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }
}