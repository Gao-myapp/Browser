package com.myapp.browser

import java.util.LinkedHashMap

// This class is used to manage the processed request.
// If don't use this, it may cause the processed request array very long.
class ProcessedRequestCache(private val mMaxSize: Int = 200) {
    @Volatile
    private var mCache = object : LinkedHashMap<String, Long>(mMaxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean {
            return size > mMaxSize
        }
    }

    @Synchronized
    fun contains(url: String): Boolean = mCache.containsKey(url)

    @Synchronized
    fun add(url: String): Boolean {
        return if (mCache.containsKey(url)) {
            false
        } else {
            mCache[url] = System.currentTimeMillis()
            true
        }
    }

    @Synchronized
    fun clear() {
        mCache.clear()
    }
}