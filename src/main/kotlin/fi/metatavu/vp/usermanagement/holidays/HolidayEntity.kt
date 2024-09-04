package fi.metatavu.vp.usermanagement.holidays

import fi.metatavu.vp.usermanagement.model.CompensationType
import fi.metatavu.vp.usermanagement.persistence.Metadata
import jakarta.persistence.*
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "holiday")
class HolidayEntity: Metadata() {

    @Id
    lateinit var id: UUID

    @Column(nullable = false)
    lateinit var name: String

    @Column(nullable = false, name = "holidaydate", unique = true)
    lateinit var date: LocalDate

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    lateinit var compensationType: CompensationType

    override lateinit var creatorId: UUID

    override lateinit var lastModifierId: UUID

}