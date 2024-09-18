package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.workshifts.EmployeeWorkShiftEntity
import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity for work events
 */
@Entity
@Table(name = "workevent")
class WorkEventEntity {

    @Id
    lateinit var id: UUID

    @Column(nullable = false)
    lateinit var employeeId: UUID

    @Column(nullable = false)
    lateinit var time: OffsetDateTime

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    lateinit var workEventType: WorkEventType

    @ManyToOne(optional = false)
    lateinit var workShift: EmployeeWorkShiftEntity

}