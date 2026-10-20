package app.lart.mobile

import android.app.Application
import app.lart.mobile.data.Repository
import app.lart.mobile.data.Tmdb
import app.lart.mobile.ui.LartDisplay
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class LartApp : Application(), coil.ImageLoaderFactory {
    // Posters and backdrops stay on disk between launches: art at a given address never changes,
    // so the cache is used as is instead of asking each server whether it still may.
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .okHttpClient(app.lart.mobile.data.Net.client)
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(768L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .components { add(coil.decode.SvgDecoder.Factory()) }
        .build()

    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
        app.lart.mobile.ui.LartFonts.assets = assets
        app.lart.mobile.data.Curated.init(this)
        // The curated lists and last launch's Home rows are parsed now, off the main thread,
        // so the first Home frame doesn't wait on them.
        Thread({
            app.lart.mobile.ui.HomeCache.preload(this)
            app.lart.mobile.data.Curated.lists()
        }, "lart-warmup").apply { priority = Thread.NORM_PRIORITY - 1 }.start()
        // Look-and-feel settings reach every screen, the player's too, through LartDisplay.
        val main = MainScope()
        main.launch {
            repository.settings.collect {
                LartDisplay.settings = it
                Tmdb.languageOverride = it.metadataLanguage
                Tmdb.imageLanguage = it.imageLanguage
                Tmdb.titleLanguage = it.titleLanguage
                Tmdb.region = it.region
                Tmdb.favourRegion = it.favourRegion
                Tmdb.translateTitles = it.translateTitles
                Tmdb.translateOverviews = it.translateOverviews
            }
        }
        main.launch { repository.rpdbKey.collect { LartDisplay.rpdbKey = it } }
        main.launch { repository.myList.collect { list -> LartDisplay.myListIds = list.map { it.id }.toSet() } }
        main.launch {
            // Watched: the Stremio library's, plus films marked watched on their page.
            kotlinx.coroutines.flow.combine(repository.library, repository.shelves) { items, shelves ->
                items.filter { !it.removed && ((it.state?.timesWatched ?: 0) > 0 || (it.state?.flaggedWatched ?: 0) > 0) }.map { it.id }.toSet() + shelves.watched
            }.collect { LartDisplay.watchedIds = it }
        }
        main.launch { repository.providerKeys.collect { app.lart.mobile.data.SkipSegments.theIntroDbKey = it[app.lart.mobile.data.ProviderKey.THEINTRODB] } }
        main.launch { repository.providerKeys.collect { LartDisplay.customPosters = it[app.lart.mobile.data.ProviderKey.POSTERS] } }
    }
}
