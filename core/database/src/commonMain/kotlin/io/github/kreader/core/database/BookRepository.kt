package io.github.kreader.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface BookRepository {
    fun observeAllBooks(): Flow<List<Book>>
    fun observeBook(id: String): Flow<Book?>
    suspend fun getBook(id: String): Book?
    suspend fun upsertBook(book: Book)
    suspend fun updateProgress(bookId: String, progression: Float, locator: Locator, timestamp: Long)
    suspend fun deleteBook(id: String)
    
    fun observeBookmarks(bookId: String): Flow<List<Bookmark>>
    suspend fun saveBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(id: String)

    fun observeHighlights(bookId: String): Flow<List<Highlight>>
    suspend fun saveHighlight(highlight: Highlight)
    suspend fun deleteHighlight(id: String)
}

class SqlDelightBookRepository(
    private val database: KReaderDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : BookRepository {

    private val queries = database.kReaderDatabaseQueries

    override fun observeAllBooks(): Flow<List<Book>> {
        return queries.selectAllBooks()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { list -> list.map { it.toDomain(json) } }
    }

    override fun observeBook(id: String): Flow<Book?> {
        return queries.selectBookById(id)
            .asFlow()
            .mapToOneOrNull(ioDispatcher)
            .map { it?.toDomain(json) }
    }

    override suspend fun getBook(id: String): Book? = withContext(ioDispatcher) {
        queries.selectBookById(id).executeAsOneOrNull()?.toDomain(json)
    }

    override suspend fun upsertBook(book: Book): Unit = withContext(ioDispatcher) {
        queries.insertOrReplaceBook(
            id = book.id,
            title = book.title,
            author = book.author,
            description = book.description,
            coverPath = book.coverPath,
            format = book.format.name,
            sourceData = json.encodeToString(book.source),
            fileSizeBytes = book.fileSizeBytes,
            lastReadTimestamp = book.lastReadTimestamp,
            addedTimestamp = book.addedTimestamp,
            totalProgression = book.totalProgression.toDouble(),
            lastLocatorJson = book.lastLocator?.let { json.encodeToString(it) }
        )
    }

    override suspend fun updateProgress(
        bookId: String,
        progression: Float,
        locator: Locator,
        timestamp: Long
    ): Unit = withContext(ioDispatcher) {
        queries.updateBookProgress(
            totalProgression = progression.toDouble(),
            lastLocatorJson = json.encodeToString(locator),
            lastReadTimestamp = timestamp,
            id = bookId
        )
    }

    override suspend fun deleteBook(id: String): Unit = withContext(ioDispatcher) {
        queries.deleteBook(id)
    }

    override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> {
        return queries.selectBookmarksForBook(bookId)
            .asFlow()
            .mapToList(ioDispatcher)
            .map { list ->
                list.map { entity ->
                    Bookmark(
                        id = entity.id,
                        bookId = entity.bookId,
                        locator = json.decodeFromString(entity.locatorJson),
                        title = entity.title,
                        createdTimestamp = entity.createdTimestamp
                    )
                }
            }
    }

    override suspend fun saveBookmark(bookmark: Bookmark): Unit = withContext(ioDispatcher) {
        queries.insertBookmark(
            id = bookmark.id,
            bookId = bookmark.bookId,
            locatorJson = json.encodeToString(bookmark.locator),
            title = bookmark.title,
            createdTimestamp = bookmark.createdTimestamp
        )
    }

    override suspend fun deleteBookmark(id: String): Unit = withContext(ioDispatcher) {
        queries.deleteBookmark(id)
    }

    override fun observeHighlights(bookId: String): Flow<List<Highlight>> {
        return queries.selectHighlightsForBook(bookId)
            .asFlow()
            .mapToList(ioDispatcher)
            .map { list ->
                list.map { entity ->
                    Highlight(
                        id = entity.id,
                        bookId = entity.bookId,
                        locator = json.decodeFromString(entity.locatorJson),
                        colorHex = entity.colorHex,
                        annotation = entity.annotation,
                        createdTimestamp = entity.createdTimestamp,
                        modifiedTimestamp = entity.modifiedTimestamp
                    )
                }
            }
    }

    override suspend fun saveHighlight(highlight: Highlight): Unit = withContext(ioDispatcher) {
        queries.insertHighlight(
            id = highlight.id,
            bookId = highlight.bookId,
            locatorJson = json.encodeToString(highlight.locator),
            colorHex = highlight.colorHex,
            annotation = highlight.annotation,
            createdTimestamp = highlight.createdTimestamp,
            modifiedTimestamp = highlight.modifiedTimestamp
        )
    }

    override suspend fun deleteHighlight(id: String): Unit = withContext(ioDispatcher) {
        queries.deleteHighlight(id)
    }

    private fun BookEntity.toDomain(json: Json): Book {
        return Book(
            id = id,
            title = title,
            author = author,
            description = description,
            coverPath = coverPath,
            format = BookFormat.valueOf(format),
            source = json.decodeFromString(sourceData),
            fileSizeBytes = fileSizeBytes,
            lastReadTimestamp = lastReadTimestamp,
            addedTimestamp = addedTimestamp,
            totalProgression = totalProgression.toFloat(),
            lastLocator = lastLocatorJson?.let { json.decodeFromString<Locator>(it) }
        )
    }
}
