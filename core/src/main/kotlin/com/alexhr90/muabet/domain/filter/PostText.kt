package com.alexhr90.muabet.domain.filter

import com.alexhr90.muabet.domain.model.Block

/** A post's blocks as plain text: for keyword mutes, copying a post and reading it aloud. */
object PostText {
    fun plain(blocks: List<Block>, includeQuotes: Boolean = true): String = buildString { append(blocks, includeQuotes) }.trim()

    private fun StringBuilder.append(blocks: List<Block>, includeQuotes: Boolean) {
        blocks.forEach { b ->
            when (b) {
                is Block.Paragraph -> appendLine(b.runs.joinToString("") { it.text }.trim())
                is Block.Quote -> if (includeQuotes) append(b.blocks, true)
                is Block.Spoiler -> append(b.blocks, includeQuotes)
                is Block.ListBlock -> b.items.forEach { append(it, includeQuotes) }
                is Block.Code -> appendLine(b.text)
                is Block.Link -> appendLine(b.title ?: b.url)
                is Block.Embed -> appendLine(b.url)
                is Block.Table -> b.rows.forEach { row -> row.forEach { append(it.blocks, includeQuotes) } }
                is Block.Box -> {
                    b.title?.let { appendLine(it) }
                    append(b.blocks, includeQuotes)
                }
                is Block.File -> appendLine(b.name)
                is Block.Image, is Block.Unknown -> Unit
            }
        }
    }
}
