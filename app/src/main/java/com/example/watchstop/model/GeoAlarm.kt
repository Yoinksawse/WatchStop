package com.example.watchstop.model

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.watchstop.data.UserGeofencesDatabase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.util.UUID

@Serializable
data class GeoAlarm (
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var active: Boolean = true,
    var description: String = "Add Description",
    var geofenceId: String? = null,
    
    @Serializable(with = LocalDateSerializer::class)
    var specificDate: LocalDate? = null,
    @Serializable(with = DayOfWeekSerializer::class)
    var dayOfWeek: DayOfWeek? = null,
    @Serializable(with = LocalTimeSerializer::class)
    var startTime: LocalTime? = null,
    @Serializable(with = LocalTimeSerializer::class)
    var endTime: LocalTime? = null
) {
    fun getGeofence(): GeofenceArea? {
        return UserGeofencesDatabase.getGeofenceInstance(geofenceId)
    }
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    @RequiresApi(Build.VERSION_CODES.O)
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())
    @RequiresApi(Build.VERSION_CODES.O)
    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())
}

object DayOfWeekSerializer : KSerializer<DayOfWeek> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DayOfWeek", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: DayOfWeek) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): DayOfWeek = DayOfWeek.valueOf(decoder.decodeString())
}
