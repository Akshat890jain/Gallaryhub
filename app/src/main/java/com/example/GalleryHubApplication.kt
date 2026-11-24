package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.data.remote.RetrofitClient

/**
 * Configures a single high-throughput Coil ImageLoader shared across the entire app
 * and initializes the OkHttp disk cache at app launch for instant media loading and
 * silky-smooth 60/120fps scrolling.
 */
class GalleryHubApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.initializeHttpCache(cacheDir)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { RetrofitClient.okHttpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Use 25% of available app RAM for fast decoded thumbnails
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_media_disk_cache"))
                    .maxSizeBytes(180L * 1024L * 1024L) // 180 MB persistent thumbnail & photo disk cache
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false) // Keep gallery images snappy without redundant 304 round-trips
            .allowHardware(true)
            .crossfade(160)
            .build()
    }
}
