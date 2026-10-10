package com.ququla89.digitalhorror.feature

interface HorrorFeature {
    val isEnabled: Boolean

    fun enable()

    fun disable()
}
