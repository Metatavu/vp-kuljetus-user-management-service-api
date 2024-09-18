package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.workshifts.EmployeeWorkShiftEntity
import jakarta.persistence.*
import java.util.*

/**
 * Entity for Work Shift Hours
 */
@Entity
@Table(name = "workshifthours")
class WorkShiftHoursEntity {

    @Id
    lateinit var id: UUID

    @ManyToOne(optional = false)
    lateinit var workShift: EmployeeWorkShiftEntity

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    lateinit var workEventType: WorkEventType

    @Column
    var actualHours: Float? = null

}