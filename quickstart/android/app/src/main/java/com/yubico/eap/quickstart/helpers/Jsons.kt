package com.yubico.eap.quickstart.helpers

import org.json.JSONObject
import com.yubico.eap.quickstart.logging.YOLOLogger as Log

fun JSONObject.optNestedString(path: String): String? = try {
    if ("." in path) {
        val all = path.split(".")
        val first = all.first()

        if (has(first)) {
            val rest = all.drop(1).joinToString(separator = ".")
            getJSONObject(first).optNestedString(rest)
        } else {
            null
        }
    } else {
        optString(path)
    }
} catch (th: Throwable) {
    Log.e("JSONNested", "No $path in $this.", th)
    null
}
