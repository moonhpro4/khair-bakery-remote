package com.khair.bakery.remote

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File

/**
 * The picture of the printed End-of-Break report travels from the computer in small pieces ("zreport.chunk").
 * The pieces are kept on disk until all of them arrived, then the picture is saved and can be opened any time.
 */
object ZReports {
    private val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")
    private const val KEEP = 30

    private fun dir(ctx: Context, shopId: String): File = File(ctx.filesDir, "zreports/$shopId").apply { mkdirs() }
    private fun partDir(ctx: Context, shopId: String, id: String): File =
        File(ctx.cacheDir, "zparts/$shopId/$id").apply { mkdirs() }

    /** Stores one piece. Returns the finished picture's report id when this piece completed it, otherwise null. */
    fun addChunk(ctx: Context, shop: Shop, p: JSONObject): String? {
        val id = p.optString("id")
        val n = p.optInt("n", 0)
        val i = p.optInt("i", -1)
        if (!SAFE_ID.matches(id) || n !in 1..400 || i !in 0 until n) return null
        val out = dir(ctx, shop.id)
        if (out.listFiles()?.any { it.nameWithoutExtension == id } == true) return null   // already have it

        val parts = partDir(ctx, shop.id, id)
        File(parts, "$i.part").writeText(p.optString("data"))
        val pieces = (0 until n).map { File(parts, "$it.part") }
        if (pieces.any { !it.exists() }) return null

        val b64 = StringBuilder()
        for (f in pieces) b64.append(f.readText())
        val bytes = Base64.decode(b64.toString(), Base64.DEFAULT)
        val ext = if (p.optString("ext") == "jpg") "jpg" else "png"
        val tmp = File(out, "$id.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(File(out, "$id.$ext"))
        parts.deleteRecursively()
        prune(out)
        return id
    }

    private fun prune(out: File) {
        val files = out.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.sortedByDescending { it.lastModified() }
            ?: return
        for (f in files.drop(KEEP)) f.delete()
    }

    fun find(ctx: Context, shopId: String, id: String): File? =
        if (!SAFE_ID.matches(id)) null else dir(ctx, shopId).listFiles()?.firstOrNull { it.nameWithoutExtension == id && it.isFile }

    fun latest(ctx: Context, shopId: String): File? =
        dir(ctx, shopId).listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.maxByOrNull { it.lastModified() }
}
