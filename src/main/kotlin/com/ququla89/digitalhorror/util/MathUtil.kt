package com.ququla89.digitalhorror.util

import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.cos

object MathUtil {
    fun behindLocation(player: Player, distance: Double, verticalOffset: Double = 0.0): Location {
        val horizontalDirection = player.eyeLocation.direction.setY(0.0).normalizedOrZero()
        return player.location.clone().subtract(horizontalDirection.multiply(distance)).add(0.0, verticalOffset, 0.0)
    }

    fun isWithinFieldOfView(eyeLocation: Location, targetLocation: Location, fieldOfViewDegrees: Double): Boolean {
        if (eyeLocation.world !== targetLocation.world) return false

        val toTarget = targetLocation.toVector().subtract(eyeLocation.toVector()).normalizedOrZero()
        if (toTarget.lengthSquared() == 0.0) return true

        val lookDirection = eyeLocation.direction.normalizedOrZero()
        val threshold = cos(Math.toRadians(fieldOfViewDegrees / 2.0))
        return lookDirection.dot(toTarget) >= threshold
    }

    fun hasClearSight(eyeLocation: Location, targetLocation: Location): Boolean {
        val world = eyeLocation.world ?: return false
        if (world !== targetLocation.world) return false

        val delta = targetLocation.toVector().subtract(eyeLocation.toVector())
        val distance = delta.length()
        if (distance <= 0.01) return true

        val obstruction =
            world.rayTraceBlocks(
                eyeLocation,
                delta.normalize(),
                distance,
                FluidCollisionMode.NEVER,
                true,
            )
        return obstruction == null
    }

    fun randomHorizontalOffset(minDistance: Double, maxDistance: Double): Vector {
        val angle = kotlin.random.Random.nextDouble(0.0, Math.PI * 2.0)
        val distance = kotlin.random.Random.nextDouble(minDistance, maxDistance.coerceAtLeast(minDistance + 0.01))
        return Vector(kotlin.math.cos(angle) * distance, 0.0, kotlin.math.sin(angle) * distance)
    }
}
