/*
 * Did I Take My Meds? is a FOSS app to keep track of medications
 * Did I Take My Meds? is designed to help prevent a user from skipping doses and/or overdosing
 *     Copyright (C) 2021  Noah Stanford <noahstandingford@gmail.com>
 *
 *     Did I Take My Meds? is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Did I Take My Meds? is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.corruptedark.diditakemymeds.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import dev.corruptedark.diditakemymeds.data.db.MedicationDB.Companion.MED_TABLE
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.joins.MedicationFull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

@Dao
interface MedicationDao {
    @Insert
    fun insertAll(vararg medications: Medication)

    @Update
    fun updateMedications(vararg medications: Medication)

    @Delete
    fun delete(medication: Medication)

    @Query("SELECT * FROM $MED_TABLE WHERE id = :medId LIMIT 1")
    fun get(medId: Long): Medication


    @Transaction
    @Query("SELECT * FROM $MED_TABLE WHERE id = :medId LIMIT 1")
    fun getFull(medId: Long): MedicationFull

    @Transaction
    @Query("SELECT * FROM $MED_TABLE WHERE id = :medId LIMIT 1")
    fun observeFull(medId: Long): Flow<MedicationFull>

    suspend fun observeFullDistinct(medId: Long) = observeFull(medId).distinctUntilChanged()

    @Query("SELECT * FROM $MED_TABLE")
    fun getAll(): LiveData<MutableList<Medication>>


    @Transaction
    @Query("SELECT * FROM $MED_TABLE")
    fun getAllFull(): LiveData<MutableList<MedicationFull>>

    @Query("SELECT * FROM $MED_TABLE")
    fun getAllRaw(): MutableList<Medication>


    @Transaction
    @Query("SELECT * FROM $MED_TABLE")
    fun getAllRawFull(): MutableList<MedicationFull>

    @Query("SELECT EXISTS(SELECT * FROM $MED_TABLE WHERE id = :medId)")
    fun medicationExists(medId: Long): Boolean
}