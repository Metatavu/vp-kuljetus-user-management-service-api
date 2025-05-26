package fi.metatavu.vp.usermanagement.messaging

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.quarkus.runtime.annotations.RegisterForReflection
import java.time.OffsetDateTime

@RegisterForReflection
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes
(
    JsonSubTypes.Type(value = WorkEventDuplicateRemovalEvent::class, name = "WORK_EVENT_DUPLICATE_REMOVAL")
)
data class WorkEventDuplicateRemovalEvent(val type: String = "WORK_EVENT_DUPLICATE_REMOVAL")
