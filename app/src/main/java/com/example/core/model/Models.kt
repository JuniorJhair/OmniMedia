package com.example.core.model

enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE
}

enum class ViewMode {
    GRID,
    COMPACT_GRID,
    LIST,
    COMPACT_LIST
}

enum class SortOption(val displayName: String) {
    NAME_ASC("Nombre A-Z"),
    NAME_DESC("Nombre Z-A"),
    ARTIST_ASC("Artista A-Z"),
    ARTIST_DESC("Artista Z-A"),
    ALBUM_ASC("Álbum A-Z"),
    ALBUM_DESC("Álbum Z-A"),
    DATE_DESC("Más reciente"),
    DATE_ASC("Más antiguo"),
    SIZE_DESC("Tamaño mayor"),
    SIZE_ASC("Tamaño menor"),
    DURATION_DESC("Duración mayor"),
    DURATION_ASC("Duración menor")
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class NavSection(val title: String) {
    HOME("Inicio"),
    VIDEOS("Videos"),
    MUSIC("Música"),
    IMAGES("Imágenes"),
    PLAYLISTS("Playlists"),
    VAULT("Bóveda"),
    HISTORY("Historial"),
    SETTINGS("Configuración"),
    STATS("Estadísticas")
}
