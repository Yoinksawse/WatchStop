package com.example.watchstop.model

import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

@Serializable
data class GeofenceArea(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    @Serializable(with = LatLngSerializer::class)
    val center: LatLng = LatLng(0.0, 0.0),
    val typeId: Int = 0, //1 for circle, 2 for polygon
    val radius: Double = 0.0,
    val points: List<@Serializable(with = LatLngSerializer::class) LatLng> = emptyList(),
    var geoAlarmId: String? = null
)

object LatLngSerializer : KSerializer<LatLng> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LatLng", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LatLng) {
        encoder.encodeString("${value.latitude},${value.longitude}")
    }
    override fun deserialize(decoder: Decoder): LatLng {
        val (lat, lng) = decoder.decodeString().split(",").map { it.toDouble() }
        return LatLng(lat, lng)
    }
}
