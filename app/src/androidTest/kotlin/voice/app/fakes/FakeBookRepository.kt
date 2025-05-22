package voice.app.fakes

import voice.common.BookId
import voice.data.Book
import voice.data.MediaFile
import voice.data.Progress
import voice.data.Track
import voice.data.repo.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import voice.data.ChapterId
import java.io.File // Import for File
import javax.inject.Inject // Added missing Inject import
import kotlin.time.Duration // Import for Duration
import kotlin.time.Duration.Companion.seconds


// A very simple fake book for testing
val TEST_BOOK_ID = BookId("test-book-id")
val TEST_CHAPTER_ID = ChapterId("test-chapter-id")
val FAKE_MEDIA_FILE_PATH = "/fake/path/to/audio.mp3" // Path FakeMediaSource might expect or ignore

val TEST_BOOK = Book(
    id = TEST_BOOK_ID,
    title = "Test Book",
    chapters = listOf(
        Track(
            id = TEST_CHAPTER_ID,
            bookId = TEST_BOOK_ID,
            title = "Chapter 1",
            artist = "Test Author",
            album = "Test Book",
            track = 1,
            mediaFile = MediaFile(File(FAKE_MEDIA_FILE_PATH), Duration.ZERO), // Duration might be updated by FakeMediaSource
            lastPlayed = null,
            progress = Progress(0L, TEST_CHAPTER_ID)
        )
    ),
    currentChapterId = TEST_CHAPTER_ID,
    lastPlayed = null,
    totalTime = 0.seconds, // Will be determined by media
    rating = 0,
    totalBytes = 0L,
    folder = File("/fake/folder")
)

class FakeBookRepository @Inject constructor() : BookRepository {
    private val books = MutableStateFlow(mapOf(TEST_BOOK_ID to TEST_BOOK))

    override suspend fun get(id: BookId): Book? = books.value[id]
    override suspend fun save(book: Book) {
        books.value = books.value + (book.id to book)
    }
    // Implement other methods with dummy/empty implementations or throw NotImplementedError
    // if they are not expected to be called by PlayerController in this test context.
    override suspend fun delete(id: BookId) { TODO("Not yet implemented") }
    override suspend fun markAsRead(id: BookId) { TODO("Not yet implemented") }
    override suspend fun markAsUnread(id: BookId) { TODO("Not yet implemented") }
    override fun observe(): Flow<List<Book>> = books.map { it.values.toList() }
    override fun observe(id: BookId): Flow<Book?> = books.map { it[id] }
    override suspend fun updateCover(id: BookId, path: String?) { TODO("Not yet implemented") }
    override suspend fun updateFolder(id: BookId, path: File) { TODO("Not yet implemented") }
    override suspend fun updateLastPlayed(id: BookId, chapterId: ChapterId?) { TODO("Not yet implemented") }
    override suspend fun updateProgress(id: BookId, chapterId: ChapterId, position: Long) { TODO("Not yet implemented") }
    override suspend fun updateRating(id: BookId, rating: Int) { TODO("Not yet implemented") }
    override suspend fun updateChapters(id: BookId, chapters: List<Track>) { TODO("Not yet implemented") }
    override suspend fun getAll(): List<Book> = books.value.values.toList()
    override suspend fun get(ids: Set<BookId>): List<Book> = books.value.filterKeys { it in ids }.values.toList()
    override suspend fun getByFolder(folder: File): Book? { TODO("Not yet implemented") }
    override suspend fun getByMediaFile(mediaFile: File): Book? { TODO("Not yet implemented") }
    override suspend fun getChapterCount(id: BookId): Int? { TODO("Not yet implemented") }
    override suspend fun getChapter(id: ChapterId): Track? { TODO("Not yet implemented") }
    override fun observeAllBookIds(): Flow<Set<BookId>> = emptyFlow()
    override suspend fun getAllBookIds(): Set<BookId> = emptySet()
}
