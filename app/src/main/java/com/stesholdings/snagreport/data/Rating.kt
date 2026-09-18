package com.stesholdings.snagreport.data

/** Ordinal order (RED, AMBER, GREEN) doubles as the default severity sort order. */
enum class Rating(val label: String) {
    RED("Red"),
    AMBER("Amber"),
    GREEN("Green")
}
