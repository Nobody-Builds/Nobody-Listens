package com.nobodybuilds.listens.data

import com.nobodybuilds.listens.shared.WearLibraryItem

data class WearLocalQueueState(
    val items: List<WearLibraryItem> = emptyList(),
    val currentIndex: Int = -1,
)
