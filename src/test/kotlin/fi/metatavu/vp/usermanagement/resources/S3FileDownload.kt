package fi.metatavu.vp.usermanagement.resources

import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.ConfigProvider
import org.eclipse.microprofile.config.inject.ConfigProperty
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.net.URI

class S3FileDownload {
    fun downloadFile(filePath: String): String {
        val s3Client = S3Client.builder()
            .endpointOverride(URI.create(ConfigProvider.getConfig().getValue("quarkus.s3.endpoint-override", String::class.java)))
            .region(Region.of(ApiTestSettings.S3_REGION))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        ApiTestSettings.AWS_ACCESS_KEY_ID,
                        ApiTestSettings.AWS_SECRET_ACCESS_KEY
                    )
                )
            )
            .build()

        val s3Object = s3Client.getObject(GetObjectRequest.builder().bucket(ApiTestSettings.S3_BUCKET).key(filePath).build())
        return String(s3Object.readAllBytes(), Charsets.UTF_8)
    }
}