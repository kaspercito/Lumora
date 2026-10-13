package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType

/** Nombre traducido (según el idioma de la app) de cada tipo de medio. */
fun TvType.localizedName(context: Context): String = context.getString(
    when (this) {
        TvType.Movie -> R.string.movies
        TvType.AnimeMovie -> R.string.anime_movies
        TvType.TvSeries -> R.string.tv_series
        TvType.Cartoon -> R.string.cartoons
        TvType.Anime -> R.string.anime
        TvType.OVA -> R.string.ova
        TvType.Torrent -> R.string.torrent
        TvType.Documentary -> R.string.documentaries
        TvType.AsianDrama -> R.string.asian_drama
        TvType.Live -> R.string.livestreams
        TvType.NSFW -> R.string.nsfw
        TvType.Others -> R.string.others
        TvType.Music -> R.string.music_singular
        TvType.AudioBook -> R.string.audio_book_singular
        TvType.CustomMedia -> R.string.custom_media_singular
        TvType.Audio -> R.string.audio_singular
        TvType.Podcast -> R.string.podcast_singular
        TvType.Video -> R.string.video_singular
    }
)
