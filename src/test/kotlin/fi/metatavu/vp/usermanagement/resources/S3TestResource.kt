package fi.metatavu.vp.usermanagement.resources

import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest

class S3TestResource: QuarkusTestResourceLifecycleManager {
    private lateinit var s3Container: LocalStackContainer

    override fun start(): MutableMap<String, String> {

        s3Container = LocalStackContainer(DockerImageName.parse("localstack/localstack:4.3.0"))
            .withServices(LocalStackContainer.Service.S3)
            .withEnv("AWS_ACCESS_KEY_ID", ApiTestSettings.AWS_ACCESS_KEY_ID)
            .withEnv("AWS_SECRET_ACCESS_KEY", ApiTestSettings.AWS_SECRET_ACCESS_KEY)

        s3Container.start()

        val s3Client = S3Client.builder()
            .endpointOverride(s3Container.getEndpointOverride(LocalStackContainer.Service.S3))
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

        s3Client.createBucket(
            CreateBucketRequest.builder()
                .bucket(ApiTestSettings.S3_BUCKET)
                .build()
        )

        s3Client.close()

        val config: MutableMap<String, String> = HashMap()
        config["vp.usermanagement.payrollexports.s3.bucket"] = ApiTestSettings.S3_BUCKET
        config["vp.usermanagement.payrollexports.s3.folderpath"] = ApiTestSettings.S3_FOLDER_PATH
        config["quarkus.s3.aws.region"] = ApiTestSettings.S3_REGION
        config["quarkus.s3.aws.credentials.static-provider.access-key-id"] = ApiTestSettings.AWS_ACCESS_KEY_ID
        config["quarkus.s3.aws.credentials.static-provider.secret-access-key"] = ApiTestSettings.AWS_SECRET_ACCESS_KEY
        config["quarkus.s3.endpoint-override"] = s3Container.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        config["quarkus.s3.credentials.type"] = "static"
        return config
    }

    override fun stop() {
        s3Container.stop()
    }
}