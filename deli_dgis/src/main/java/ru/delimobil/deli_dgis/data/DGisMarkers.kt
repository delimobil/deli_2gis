package ru.delimobil.deli_dgis.data

import android.graphics.Bitmap

data class DGisMarkers(
    val groupId: String,
    val options: List<Options>
) {

    data class Options(
        val id: String,
        val visible: Boolean = true,
        val position: DGisCoordinates,
        val image: Icon?,
        val verticalAnchor: VerticalAnchor = VerticalAnchor.Default,
        val horizontalAnchor: HorizontalAnchor = HorizontalAnchor.Default,
        val rotation: Float? = null,
        val alpha: Float? = null,
        val zIndex: Int? = null
    ) {

        data class Icon(
            val bitmap: Bitmap?
        )

        sealed class VerticalAnchor(open val value: Float) {
            object Default : VerticalAnchor(0.5f)
            object Top : VerticalAnchor(0f)
            object Bottom : VerticalAnchor(1f)
            data class Custom(override val value: Float = 0f) : VerticalAnchor(value)
        }

        sealed class HorizontalAnchor(open val value: Float) {
            object Default : HorizontalAnchor(0.5f)
            object Left : HorizontalAnchor(0f)
            object Right : HorizontalAnchor(1f)
            data class Custom(override val value: Float = 0f) : HorizontalAnchor(value)
        }
    }
}