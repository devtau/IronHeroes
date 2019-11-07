package com.devtau.ironHeroes.data.dao

import androidx.room.*
import com.devtau.ironHeroes.data.model.ExerciseInTraining
import com.devtau.ironHeroes.data.relations.ExerciseInTrainingRelation
import io.reactivex.Completable
import io.reactivex.Flowable

@Dao
interface ExerciseInTrainingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(list: List<ExerciseInTraining?>): Completable

    @Query("SELECT * FROM ExercisesInTraining WHERE id = :id")
    fun getById(id: Long): Flowable<ExerciseInTrainingRelation>

    @Transaction
    @Query("SELECT * FROM ExercisesInTraining WHERE trainingId = :trainingId")
    fun getList(trainingId: Long): Flowable<List<ExerciseInTrainingRelation>>

    @Delete
    fun delete(list: List<ExerciseInTraining?>): Completable

    @Query("DELETE FROM ExercisesInTraining")
    fun delete(): Completable
}