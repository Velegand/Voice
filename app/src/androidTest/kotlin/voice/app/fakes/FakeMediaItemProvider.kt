package voice.app.fakes

import androidx.media3.common.MediaItem
import voice.data.Book
import voice.playback.session.MediaItemProvider
import javax.inject.Inject

class FakeMediaItemProvider @Inject constructor() : MediaItemProvider {
    override suspend fun mediaItem(book: Book): MediaItem {
        // This needs to create a MediaItem that FakeMediaSource can understand
        // or that PlaybackService can use with FakeMediaSource.
        // Typically, FakeMediaSource uses the URI or media ID.
        return MediaItem.Builder()
            .setMediaId(book.id.value) // Use book ID as mediaId
            .setUri("fake:///${book.id.value}") // A fake URI for FakeMediaSource
            .build()
    }
    override suspend fun mediaItems(books: List<Book>): List<MediaItem> {
        return books.map { mediaItem(it) }
    }
}
