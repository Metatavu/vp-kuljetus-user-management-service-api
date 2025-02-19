package fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets

import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing a set of changes for the work shifts changelog
 */
@Entity
@Table(name = "workshiftchangeset")
class WorkShiftChangeSetEntity {
    @Id
    lateinit var id: UUID

    @Column
    lateinit var creatorId: UUID

    @ManyToOne
    lateinit var workShift: WorkShiftEntity

    @Column
    var createdAt: OffsetDateTime? = null

    @PrePersist
    fun onCreate() {
        createdAt = OffsetDateTime.now()
    }
}