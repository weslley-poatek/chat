package br.com.weslleycampos.chat.core.database.daos

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import br.com.weslleycampos.chat.core.database.entities.PersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    /** Every persona by [PersonaEntity.position], the id breaking a tie. Emits again on every change. */
    @Query("SELECT * FROM personas ORDER BY position, id")
    fun observeAll(): Flow<List<PersonaEntity>>

    /**
     * Inserts the personas whose id is not in the table yet and leaves the others as they are, so
     * seeding the same personas again changes nothing.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(personas: List<PersonaEntity>)
}
