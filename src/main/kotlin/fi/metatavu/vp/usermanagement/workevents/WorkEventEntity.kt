package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
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

    @Column
    lateinit var employeeId: UUID

    @Column
    lateinit var time: OffsetDateTime

    @Enumerated(EnumType.STRING)
    lateinit var workEventType: WorkEventType

    @ManyToOne
    lateinit var workShift: WorkShiftEntity

    @Column
    var truckId: UUID? = null

}