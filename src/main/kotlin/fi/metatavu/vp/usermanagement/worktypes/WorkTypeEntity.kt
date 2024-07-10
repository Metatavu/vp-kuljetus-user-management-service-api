package fi.metatavu.vp.usermanagement.worktypes

import fi.metatavu.vp.api.model.WorkTypeCategory
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "work_type")
class WorkTypeEntity {

    @Id
    lateinit var id: UUID

    @Column(nullable = false)
    lateinit var name: String

    @Enumerated(EnumType.STRING)
    lateinit var category: WorkTypeCategory
}