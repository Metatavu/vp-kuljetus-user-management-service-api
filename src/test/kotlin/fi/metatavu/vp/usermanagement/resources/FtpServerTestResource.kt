package fi.metatavu.vp.usermanagement.resources

import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.io.File

class FtpServerTestResource: QuarkusTestResourceLifecycleManager {
    private lateinit var ftpContainer: GenericContainer<*>

    override fun start(): MutableMap<String, String> {
        ftpContainer = GenericContainer(DockerImageName.parse("atmoz/sftp"))
            .withEnv("SFTP_USERS", "${ApiTestSettings.FTP_USER_NAME}:${ApiTestSettings.FTP_USER_PASSWORD}:1001")
            .withExposedPorts(22)
            .withFileSystemBind("src/test/resources/${ApiTestSettings.FTP_FOLDER}", "/home/${ApiTestSettings.FTP_USER_NAME}/${ApiTestSettings.FTP_FOLDER}", BindMode.READ_WRITE)

        ftpContainer.start()

        val config: MutableMap<String, String> = HashMap()
        config["vp.usermanagement.payrollexports.ftp.address"] = "${ftpContainer.host}:${ftpContainer.getMappedPort(22)}/${ApiTestSettings.FTP_FOLDER}"
        config["vp.usermanagement.payrollexports.ftp.username"] = ApiTestSettings.FTP_USER_NAME
        config["vp.usermanagement.payrollexports.ftp.password"] = ApiTestSettings.FTP_USER_PASSWORD

        return config
    }

    override fun stop() {
        val bindFolder = "src/test/resources/${ApiTestSettings.FTP_FOLDER}"
        File(bindFolder).deleteRecursively()
        ftpContainer.stop()
    }
}