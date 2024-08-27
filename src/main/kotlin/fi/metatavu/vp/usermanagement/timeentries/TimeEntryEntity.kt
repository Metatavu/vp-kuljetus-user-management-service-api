package fi.metatavu.vp.usermanagement.timeentries

import fi.metatavu.vp.api.model.WorkEventType
import jakarta.persistence.*
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

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    lateinit var workEventType: WorkEventType

    @Column
    var endTime: OffsetDateTime? = null

}