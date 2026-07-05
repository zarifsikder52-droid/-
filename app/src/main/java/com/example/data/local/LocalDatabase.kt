package com.example.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_chats")
data class CachedChatUser(
    @PrimaryKey val uid: String,
    val fullname: String,
    val username: String,
    val profilePic: String?,
    val unread: Int
)

@Entity(tableName = "cached_messages")
data class CachedMessage(
    @PrimaryKey val id: Int,
    val conversationId: String,
    val sender: String,
    val text: String?,
    val type: String,
    val fileUrl: String?,
    val fileName: String?,
    val seen: Boolean,
    val time: Long
)

@Entity(tableName = "local_message_reactions")
data class LocalMessageReaction(
    @PrimaryKey val messageId: Int,
    val reaction: String
)

@Entity(tableName = "blocked_users")
data class BlockedUser(
    @PrimaryKey val uid: String,
    val fullname: String,
    val username: String,
    val profilePic: String?
)

@Entity(tableName = "cached_self_user")
data class CachedSelfUser(
    @PrimaryKey val uid: String,
    val fullname: String,
    val username: String,
    val email: String?,
    val phone: String?,
    val profilePic: String?
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM cached_self_user LIMIT 1")
    suspend fun getCachedSelfUser(): CachedSelfUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSelfUser(user: CachedSelfUser)

    @Query("DELETE FROM cached_self_user")
    suspend fun clearSelfUser()

    @Query("SELECT * FROM blocked_users")
    fun getBlockedUsers(): Flow<List<BlockedUser>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedUser(user: BlockedUser)

    @Query("DELETE FROM blocked_users WHERE uid = :uid")
    suspend fun deleteBlockedUser(uid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_users WHERE uid = :uid LIMIT 1)")
    suspend fun isUserBlocked(uid: String): Boolean

    @Query("SELECT uid FROM blocked_users")
    suspend fun getBlockedUserIds(): List<String>

    @Query("SELECT * FROM cached_chats WHERE uid NOT IN (SELECT uid FROM blocked_users) ORDER BY unread DESC, fullname ASC")
    fun getRecentChats(): Flow<List<CachedChatUser>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentChats(chats: List<CachedChatUser>)

    @Query("DELETE FROM cached_chats")
    suspend fun clearRecentChats()

    @Query("SELECT * FROM cached_messages WHERE conversationId = :conversationId ORDER BY time ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<CachedMessage>>

    @Query("SELECT * FROM cached_messages")
    fun getAllMessages(): Flow<List<CachedMessage>>

    @Query("SELECT * FROM local_message_reactions")
    fun getAllReactions(): Flow<List<LocalMessageReaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: LocalMessageReaction)

    @Query("DELETE FROM local_message_reactions WHERE messageId = :messageId")
    suspend fun deleteReaction(messageId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<CachedMessage>)

    @Query("DELETE FROM cached_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Int)

    @Query("DELETE FROM cached_messages WHERE id IN (:messageIds)")
    suspend fun deleteMessagesByIds(messageIds: List<Int>)

    @Query("DELETE FROM cached_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM cached_messages WHERE conversationId = :conversationId AND id > 0")
    suspend fun deletePositiveMessagesForConversation(conversationId: String)

    @Query("DELETE FROM cached_messages WHERE conversationId = :conversationId AND id NOT IN (:remoteIds) AND id > 0")
    suspend fun deleteMessagesNotInList(conversationId: String, remoteIds: List<Int>)

    @Transaction
    suspend fun syncConversationMessages(conversationId: String, remoteMessages: List<CachedMessage>) {
        if (remoteMessages.isEmpty()) {
            deletePositiveMessagesForConversation(conversationId)
        } else {
            val remoteIds = remoteMessages.map { it.id }
            deleteMessagesNotInList(conversationId, remoteIds)
        }
        insertMessages(remoteMessages)
    }

    @Query("DELETE FROM cached_messages")
    suspend fun clearAllMessages()
}

@Database(entities = [CachedChatUser::class, CachedMessage::class, LocalMessageReaction::class, BlockedUser::class, CachedSelfUser::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hostnibo_chat_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
