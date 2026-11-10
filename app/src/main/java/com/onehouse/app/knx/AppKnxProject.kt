package com.onehouse.app.knx

data class AppKnxProject(
    val projectName: String,
    val builderVersion: String,
    val minimumAppVersion: String,
    val interfaceMacAddress: String,
    val rooms: List<AppKnxRoom>
) {
    val devices: List<AppKnxObject>
        get() = rooms.flatMap { it.devices }

    val uniqueGroupAddresses: List<String>
        get() = devices
            .flatMap { it.groupAddresses }
            .distinct()
            .sortedWith(KnxAddressComparator)
}

data class AppKnxRoom(
    val name: String,
    val iconName: String,
    val devices: List<AppKnxObject>
)

data class AppKnxObject(
    val roomName: String,
    val name: String,
    val sourceType: Int,
    val category: AppKnxCategory,
    val isFavourite: Boolean,
    val readAddresses: List<String>,
    val writeAddresses: List<String>,
    val dataPointType: String?,
    val unit: String?,
    val values: List<String>,
    val iconNames: List<String>
) {
    val groupAddresses: List<String>
        get() = (readAddresses + writeAddresses).distinct()
}

enum class AppKnxCategory(val displayName: String) {
    LIGHT("Iluminación"),
    BLIND("Persiana"),
    CLIMATE("Climatización"),
    TEMPERATURE("Temperatura"),
    SENSOR("Sensor"),
    SCENE("Escena"),
    ALARM("Alarma"),
    SWITCH("Interruptor"),
    METER("Medición"),
    UNKNOWN("Otro")
}

internal object KnxAddressComparator : Comparator<String> {
    override fun compare(left: String, right: String): Int {
        val a = left.split('/').mapNotNull { part -> part.toIntOrNull() }
        val b = right.split('/').mapNotNull { part -> part.toIntOrNull() }
        for (index in 0 until minOf(a.size, b.size)) {
            val result = a[index].compareTo(b[index])
            if (result != 0) return result
        }
        return a.size.compareTo(b.size)
    }
}
