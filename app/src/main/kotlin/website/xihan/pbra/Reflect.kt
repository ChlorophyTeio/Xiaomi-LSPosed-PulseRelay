package website.xihan.pbra

import java.lang.reflect.Field

object Reflect {
    fun classOrNull(name: String, loader: ClassLoader): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()

    fun fieldOrNull(obj: Any, name: String): Any? = findField(obj.javaClass, name)?.let { field ->
        runCatching {
            field.isAccessible = true
            field.get(obj)
        }.getOrNull()
    }

    fun intField(obj: Any, name: String): Int? {
        val value = fieldOrNull(obj, name) ?: return null
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    fun longField(obj: Any, name: String): Long? {
        val value = fieldOrNull(obj, name) ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    fun stringField(obj: Any, name: String): String? = fieldOrNull(obj, name)?.toString()

    private fun findField(start: Class<*>, name: String): Field? {
        var clazz: Class<*>? = start
        while (clazz != null) {
            val current = clazz
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                clazz = current.superclass
            }
        }
        return null
    }
}
