package com.stesholdings.snagreport.ui.list

enum class SortField(val label: String) {
    BUILDING("Building Name"),
    LEVEL("Level"),
    ROOM("Room / Location"),
    RATING("Rating"),
    DESCRIPTION("Description")
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

data class SortOrder(
    val field: SortField = SortField.BUILDING,
    val direction: SortDirection = SortDirection.ASCENDING
)
