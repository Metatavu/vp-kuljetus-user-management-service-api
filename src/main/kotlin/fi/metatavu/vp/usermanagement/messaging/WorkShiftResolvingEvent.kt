package fi.metatavu.vp.usermanagement.messaging

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes
    (
    JsonSubTypes.Type(value = WorkShiftResolvingEvent::class, name = "WORK_SHIFT_RESOLVING")
)
data class WorkShiftResolvingEvent(val type: String = "WORK_SHIFT_RESOLVING")
