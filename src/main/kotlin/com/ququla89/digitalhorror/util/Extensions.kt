package com.ququla89.digitalhorror.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.random.Random

private val legacyAmpersandSerializer = LegacyComponentSerializer.legacyAmpersand()

fun Double.rollChance(): Boolean = this > 0.0 && Random.nextDouble() < this.coerceIn(0.0, 1.0)

fun String.toHorrorComponent(player: Player): Component =
    legacyAmpersandSerializer.deserialize(replace("%player%", player.name))

fun Vector.normalizedOrZero(): Vector = if (lengthSquared() <= 1.0e-9) Vector() else clone().normalize()

fun Location.isNear(other: Location, distance: Double): Boolean =
    world === other.world && distanceSquared(other) <= distance * distance

fun <T> List<T>.randomOrNull(): T? = if (isEmpty()) null else random()
