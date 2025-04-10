package fi.metatavu.vp.usermanagement.settings

/**
 * Settings implementation for test builder
 *
 * @author Jari Nykänen
 * @author Antti Leppä
 */
class ApiTestSettings {

    companion object {

        /**
         * Returns API service base path
         */
        val apiBasePath: String
            get() = "http://localhost:8081"

        const val DRIVER_APP_API_KEY = "driver-app-api-key"
        const val KEYCLOAK_API_KEY = "keycloak-api-key"
        const val CRON_API_KEY = "cron-api-key"

        const val FTP_USER_NAME = "test"
        const val FTP_USER_PASSWORD = "test"
        const val FTP_FOLDER = "payrollexports"

        const val S3_BUCKET = "payrollexports"
        const val S3_REGION = "us-east-1"
        const val S3_FOLDER_PATH = "test/"
        const val AWS_ACCESS_KEY_ID = "test"
        const val AWS_SECRET_ACCESS_KEY = "test"
    }
}