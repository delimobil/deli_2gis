package ru.delimobil.deli_dgis.turf

object TurfConversion {

    @JvmStatic
    fun degreesToRadians(degrees: Double): Double {
        val radians = degrees % 360
        return radians * Math.PI / 180
    }

    @JvmStatic
    fun radiansToDegrees(radians: Double): Double {
        val degrees = radians % (2 * Math.PI)
        return degrees * 180 / Math.PI
    }

    @JvmStatic
    fun lengthToRadians(distance: Double, unit: TurfUnit): Double {
        return distance / unit.factor
    }
}