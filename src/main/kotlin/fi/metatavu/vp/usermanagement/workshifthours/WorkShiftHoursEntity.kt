package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
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

    @ManyToOne
    lateinit var workShift: WorkShiftEntity

    @Enumerated(EnumType.STRING)
    lateinit var workType: WorkType

    @Column
    var actualHours: Float? = null

    @Column
    var calculatedHours: Float? = null

}