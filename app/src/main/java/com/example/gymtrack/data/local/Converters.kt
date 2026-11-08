package com.example.gymtrack.data.local

import androidx.room.TypeConverter
import com.example.gymtrack.data.local.entities.UnitType

class Converters {
    @TypeConverter
    fun fromUnitType(type: UnitType): String = type.name

    @TypeConverter
    fun toUnitType(value: String): UnitType = UnitType.valueOf(value)
}
