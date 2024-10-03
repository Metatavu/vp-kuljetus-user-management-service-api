package fi.metatavu.vp.usermanagement.workshifts

import fi.metatavu.vp.usermanagement.model.AbsenceType
import fi.metatavu.vp.usermanagement.model.PerDiemAllowanceType
import jakarta.persistence.*
import java.time.LocalDate
import java.util.*

/**
 * Entity for employee work shift
 */
@Entity
@Table(name = "workshift")
class WorkShiftEntity {

    @Id
    lateinit var id: UUID

    @Column
    lateinit var employeeId: UUID

    @Column
    lateinit var date: LocalDate

    @Column
    var approved: Boolean = false

    @Enumerated(EnumType.STRING)
    var absence: AbsenceType? = null

    @Enumerated(EnumType.STRING)
    var perDiemAllowance: PerDiemAllowanceType? = null
}