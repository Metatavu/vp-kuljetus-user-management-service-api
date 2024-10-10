package fi.metatavu.vp.usermanagement.clientapps

import fi.metatavu.vp.usermanagement.model.ClientAppMetadata
import fi.metatavu.vp.usermanagement.model.ClientAppStatus
import jakarta.persistence.*
import jakarta.validation.constraints.NotEmpty
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Entity for client apps
 */
@Entity
@Table(name ="clientapp")
class ClientAppEntity {

    @Id
    lateinit var id: UUID

    @Column(nullable = false)
    @NotEmpty
    lateinit var deviceId: String

    @Column
    var name: String? = null

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    lateinit var status: ClientAppStatus

    @Column
    @Enumerated(EnumType.STRING)
    var deviceOs: ClientAppMetadata.DeviceOS? = null

    @Column
    var deviceOsVersion: String? = null

    @Column
    var appVersion: String? = null

    @Column
    var lastLoginAt: OffsetDateTime? = null

    @Column(nullable = false)
    lateinit var createdAt: OffsetDateTime

    @Column(nullable = false)
    lateinit var modifiedAt: OffsetDateTime

    @Column
    var lastModifierId: UUID? = null

    @PrePersist
    fun prePersist() {
        val odtNow = OffsetDateTime.now()
        createdAt = odtNow
        modifiedAt = odtNow
    }

    @PreUpdate
    fun preUpdate() {
        modifiedAt = OffsetDateTime.now()
    }
}