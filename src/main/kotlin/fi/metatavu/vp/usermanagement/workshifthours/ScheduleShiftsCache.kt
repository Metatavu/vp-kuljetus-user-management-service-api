import jakarta.enterprise.context.ApplicationScoped
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Cache for scheduled shifts
 */
@ApplicationScoped
class ScheduleShiftsCache {

    private val scheduledShiftsCache = ConcurrentLinkedDeque<UUID>()

    /**
     * Takes a number of shifts from the cache
     *
     * @param num number of shifts to take
     * @return list of shift ids
     */
    suspend fun take(num: Int): List<UUID> {
        val selected = mutableListOf<UUID>()
        repeat(num) {
            scheduledShiftsCache.pollFirst()?.let { selected.add(it) }
        }
        return selected
    }

    /**
     * Adds a shift to the cache
     *
     * @param shiftId shift id
     */
    suspend fun addShift(shiftId: UUID) {
        if (!scheduledShiftsCache.contains(shiftId)) {
            scheduledShiftsCache.addLast(shiftId)
        }
    }

    /**
     * Removes a shift from the cache
     *
     * @param shiftId shift id
     */
    suspend fun removeShift(shiftId: UUID) {
        scheduledShiftsCache.remove(shiftId)
    }
}