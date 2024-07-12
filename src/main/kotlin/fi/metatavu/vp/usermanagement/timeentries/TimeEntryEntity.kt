package fi.metatavu.vp.usermanagement.timeentries

import fi.metatavu.vp.usermanagement.worktypes.WorkTypeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity for time entries
 */
@Entity
@Table(name = "timeentry")
class TimeEntryEntity {

    @Id
    lateinit var id: UUID

    @Column(nullable = false)
    lateinit var employeeId: UUID

    @Column(nullable = false)
    lateinit var startTime: OffsetDateTime

    @ManyToOne
    lateinit var workType: WorkTypeEntity

    @Column
    var endTime: OffsetDateTime? = null

}