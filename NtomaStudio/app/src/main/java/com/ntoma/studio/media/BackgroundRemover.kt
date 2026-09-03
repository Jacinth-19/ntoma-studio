package com.ntoma.studio.media

import kotlin.math.abs

/**
 * Plain-background cutout for wardrobe capture (research doc §5.4, Whering-style value).
 *
 * Honest scope: estimates the background colour from the photo border, clears pixels close to
 * it, and keeps the LARGEST connected foreground region (so shadows/noise specks disappear).
 * Works well on plain studio-ish backgrounds — which is what the UI tells the user — and is
 * not a matting model; hair-thin detail will not survive, and that is fine for a closet.
 *
 * Pure on IntArray (ARGB) so it is unit-testable without Android bitmaps.
 */
object BackgroundRemover {

    /** Returns a copy of [pixels] with alpha=0 on background pixels. */
    fun remove(pixels: IntArray, width: Int, height: Int, tolerance: Int = 44): IntArray {
        if (pixels.size != width * height || width < 3 || height < 3) return pixels.copyOf()
        val bg = borderMedian(pixels, width, height)
        val bgR = (bg shr 16) and 0xFF
        val bgG = (bg shr 8) and 0xFF
        val bgB = bg and 0xFF

        // 1) mark foreground
        val fg = BooleanArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            fg[i] = abs(r - bgR) + abs(g - bgG) + abs(b - bgB) > tolerance * 3
        }
        // 2) largest connected component of foreground (4-neighbour BFS)
        val largest = largestComponent(fg, width, height)
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            out[i] = if (largest[i]) pixels[i] or (0xFF shl 24) else pixels[i] and 0x00FFFFFF
        }
        return out
    }

    private fun borderMedian(pixels: IntArray, w: Int, h: Int): Int {
        val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
        fun add(i: Int) {
            val p = pixels[i]
            rs.add((p shr 16) and 0xFF); gs.add((p shr 8) and 0xFF); bs.add(p and 0xFF)
        }
        for (x in 0 until w) { add(x); add((h - 1) * w + x) }
        for (y in 0 until h) { add(y * w); add(y * w + w - 1) }
        rs.sort(); gs.sort(); bs.sort()
        val m = rs.size / 2
        return (rs[m] shl 16) or (gs[m] shl 8) or bs[m]
    }

    private fun largestComponent(fg: BooleanArray, w: Int, h: Int): BooleanArray {
        val seen = BooleanArray(fg.size)
        val keep = BooleanArray(fg.size)
        val queue = IntArray(fg.size)
        var bestSize = 0
        var bestStart = -1
        var bestEnd = -1
        for (start in fg.indices) {
            if (!fg[start] || seen[start]) continue
            var head = 0; var tail = 0
            queue[tail++] = start
            seen[start] = true
            val compStart = start
            var size = 0
            var compEnd = start
            while (head < tail) {
                val i = queue[head++]
                size++
                compEnd = i
                val x = i % w; val y = i / w
                if (x > 0 && fg[i - 1] && !seen[i - 1]) { seen[i - 1] = true; queue[tail++] = i - 1 }
                if (x < w - 1 && fg[i + 1] && !seen[i + 1]) { seen[i + 1] = true; queue[tail++] = i + 1 }
                if (y > 0 && fg[i - w] && !seen[i - w]) { seen[i - w] = true; queue[tail++] = i - w }
                if (y < h - 1 && fg[i + w] && !seen[i + w]) { seen[i + w] = true; queue[tail++] = i + w }
            }
            if (size > bestSize) { bestSize = size; bestStart = compStart; bestEnd = compEnd }
        }
        // re-walk the winning component to mark it (cheap second BFS)
        if (bestStart >= 0) {
            val markSeen = BooleanArray(fg.size)
            var head = 0; var tail = 0
            queue[tail++] = bestStart
            markSeen[bestStart] = true
            while (head < tail) {
                val i = queue[head++]
                keep[i] = true
                val x = i % w; val y = i / w
                if (x > 0 && fg[i - 1] && !markSeen[i - 1]) { markSeen[i - 1] = true; queue[tail++] = i - 1 }
                if (x < w - 1 && fg[i + 1] && !markSeen[i + 1]) { markSeen[i + 1] = true; queue[tail++] = i + 1 }
                if (y > 0 && fg[i - w] && !markSeen[i - w]) { markSeen[i - w] = true; queue[tail++] = i - w }
                if (y < h - 1 && fg[i + w] && !markSeen[i + w]) { markSeen[i + w] = true; queue[tail++] = i + w }
            }
        }
        return keep
    }
}
