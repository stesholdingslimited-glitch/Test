package com.stesholdings.snagreport.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromRating(rating: Rating): String = rating.name

    @TypeConverter
    fun toRating(value: String): Rating = Rating.valueOf(value)
}
