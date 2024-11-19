package fi.metatavu.vp.usermanagement.workshifthours.workshifthourstasks

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

/**
 * Work shift recalculation task
 */
@Entity
@Table(name = "workshifttask")
class WorkShiftTaskEntity {

    @Id
    lateinit var id: UUID

    @Column(unique = true)
    lateinit var workShiftId: UUID

}