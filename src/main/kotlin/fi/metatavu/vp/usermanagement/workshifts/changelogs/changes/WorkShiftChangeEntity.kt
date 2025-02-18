package fi.metatavu.vp.usermanagement.workshifts.changelogs.changes

import fi.metatavu.vp.usermanagement.workevents.WorkEventEntity
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursEntity
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets.WorkShiftChangeSetEntity
import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing a single change for the work shifts changelog
 */
@Entity
@Table(name = "workshiftchange")
class WorkShiftChangeEntity {
    @Id
    lateinit var id: UUID

    @Column
    lateinit var reason: String

    @Column
    var oldValue: String? = null

    @Column
    var newValue: String? = null

    @Column
    lateinit var creatorId: UUID

    @ManyToOne
    lateinit var workShiftChangeSet: WorkShiftChangeSetEntity

    @ManyToOne
    lateinit var workShift: WorkShiftEntity

    @ManyToOne
    var workShiftHours: WorkShiftHoursEntity? = null

    @ManyToOne
    var workEvent: WorkEventEntity? = null

    @Column
    var createdAt: OffsetDateTime? = null

    @PrePersist
    fun onCreate() {
        createdAt = OffsetDateTime.now()
    }
}