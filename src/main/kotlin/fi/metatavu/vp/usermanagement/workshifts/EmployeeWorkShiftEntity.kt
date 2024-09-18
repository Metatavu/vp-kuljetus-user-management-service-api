package fi.metatavu.vp.usermanagement.workshifts

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.*

/**
 * Entity for employee work shift
 */
@Entity
@Table(name = "workshift")
class EmployeeWorkShiftEntity {

    @Id
    lateinit var id: UUID

    @Column(nullable = false)
    lateinit var employeeId: UUID

    @Column(nullable = false)
    lateinit var date: LocalDate

    @Column(nullable = false)
    var approved: Boolean = false
}