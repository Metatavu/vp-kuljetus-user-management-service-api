package fi.metatavu.vp.usermanagement.persistence

import java.time.OffsetDateTime
import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.util.*

/**
 * Class containing shared general properties of entities
 */
@MappedSuperclass
abstract class Metadata {

  @Column(nullable = false)
  var createdAt: OffsetDateTime? = null

  @Column(nullable = false)
  var modifiedAt: OffsetDateTime? = null

  abstract var creatorId: UUID

  abstract var lastModifierId: UUID

  /**
   * JPA pre-persist event handler
   */
  @PrePersist
  fun onCreate() {
    val odtNow = OffsetDateTime.now()
    createdAt = odtNow
    modifiedAt = odtNow
  }

  /**
   * JPA pre-update event handler
   */
  @PreUpdate
  fun onUpdate() {
    modifiedAt = OffsetDateTime.now()
  }
}