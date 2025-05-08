package fi.metatavu.vp.usermanagement.payrollexports

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "payrollexport")
class PayrollExportEntity {

    @Id
    lateinit var id: UUID

    @Column
    lateinit var employeeId: UUID

    @Column
    lateinit var fileName: String

    @Column
    lateinit var creatorId: UUID

    @Column
    lateinit var exportedAt: OffsetDateTime
}