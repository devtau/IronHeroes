package com.devtau.ironHeroes.ui.activities.trainingDetails

import com.devtau.ironHeroes.R
import com.devtau.ironHeroes.data.DataLayer
import com.devtau.ironHeroes.data.model.*
import com.devtau.ironHeroes.rest.NetworkLayer
import com.devtau.ironHeroes.ui.DBSubscriber
import com.devtau.ironHeroes.util.AppUtils
import com.devtau.ironHeroes.util.Logger
import com.devtau.ironHeroes.util.PreferencesManager
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import java.util.*

class TrainingDetailsPresenterImpl(
    private val view: TrainingDetailsView,
    private val dataLayer: DataLayer,
    private val networkLayer: NetworkLayer,
    private val prefs: PreferencesManager,
    private var trainingId: Long?
): DBSubscriber(), TrainingDetailsPresenter {

    private var training: Training? = null
    private var champions: List<Hero>? = null
    private var heroes: List<Hero>? = null
    private var exercises: List<Exercise>? = null
    private var exercisesInTraining: List<ExerciseInTraining>? = null


    //<editor-fold desc="Presenter overrides">
    override fun restartLoaders() {
        disposeOnStop(dataLayer.getExercises(Consumer {
            exercises = it
            publishDataToView()
        }))
        disposeOnStop(dataLayer.getChampions(Consumer {
            champions = it
            publishDataToView()
        }))
        disposeOnStop(dataLayer.getHeroes(Consumer {
            heroes = it
            publishDataToView()
        }))

        val trainingId = trainingId
        if (trainingId != null) {
            disposeOnStop(dataLayer.getExercisesInTraining(trainingId, Consumer {
                if (isOnlyOrderOfListChanged(exercisesInTraining, it)) return@Consumer
                exercisesInTraining = it
                publishDataToView()
            }))
            dataLayer.getTrainingByIdAndClose(trainingId, Consumer {
                training = it
                publishDataToView()
            })
        }
    }

    override fun updateTrainingData(championIndex: Int, heroIndex: Int, date: Calendar?) {
        val championId = champions?.get(championIndex)?.id
        val heroId = heroes?.get(heroIndex)?.id
        val trainingDate = date?.timeInMillis ?: AppUtils.getRoundDate().timeInMillis
        val allPartsPresent = Training.allObligatoryPartsPresent(championId, heroId, trainingDate)
        val someFieldsChanged = training?.someFieldsChanged(championId, heroId, trainingDate) ?: true
        Logger.d(LOG_TAG, "updateTrainingData. allPartsPresent=$allPartsPresent, someFieldsChanged=$someFieldsChanged")
        if (allPartsPresent && someFieldsChanged) {
            training = Training(trainingId, championId!!, heroId!!, trainingDate)
            dataLayer.updateTraining(training, Consumer {
                trainingId = it
                if (training?.id == null) {
                    training?.id = trainingId
                    disposeOnStop(dataLayer.getExercisesInTraining(trainingId!!, Consumer { exercises ->
                        exercisesInTraining = exercises
                        publishDataToView()
                    }))
                }
            })
        }
    }

    override fun dateDialogRequested(tempDate: Calendar?) {
        val cal = tempDate?.timeInMillis ?: Calendar.getInstance().timeInMillis
        val nowMinusCentury = Calendar.getInstance()
        nowMinusCentury.add(Calendar.YEAR, -100)

        val nowPlusTwoDays = Calendar.getInstance()
        nowPlusTwoDays.add(Calendar.DAY_OF_MONTH, 2)

        val date = Calendar.getInstance()
        if (tempDate != null) date.timeInMillis = training?.date ?: cal

        view.showDateDialog(date, nowMinusCentury, nowPlusTwoDays)
    }

    override fun onBackPressed(action: Action) {
        if (training == null) {
            view.showMsg(R.string.training_not_saved, action)
        } else {
            action.run()
        }
    }

    override fun deleteTraining() {
        view.showMsg(R.string.confirm_delete, Action {
            dataLayer.deleteTrainings(listOf(training))
            view.closeScreen()
        })
    }

    override fun provideExercises(): List<ExerciseInTraining>? = training?.exercises
    override fun provideTraining() = training

    override fun onExerciseMoved(fromPosition: Int, toPosition: Int) {
        val exercises = training?.exercises as ArrayList<ExerciseInTraining>?
        if (exercises == null || exercises.isEmpty()) return
        val item = exercises.removeAt(fromPosition)
        exercises.add(toPosition, item)

        for (i in exercises.indices) exercises[i].position = i
        dataLayer.updateExercisesInTraining(exercises)
    }

    override fun addExerciseClicked() = view.showNewExerciseDialog(getNextExercisePosition())
    //</editor-fold>


    //<editor-fold desc="Private methods">
    private fun getSpinnerStrings(list: List<Hero>?): List<String> {
        val spinnerStrings = ArrayList<String>()
        if (list != null) for (next in list) spinnerStrings.add(next.getName())
        return spinnerStrings
    }

    private fun getSelectedItemIndex(list: List<Hero>?, selectedId: Long?): Int {
        var index = 0
        if (list != null) for (i in list.indices)
            if (list[i].id == selectedId) index = i
        return index
    }

    private fun publishDataToView() {
        val training = training
        if (AppUtils.isEmpty(exercises) || AppUtils.isEmpty(champions) || AppUtils.isEmpty(heroes)
            || (trainingId != null && (training == null || exercisesInTraining == null))) return

        val championId = training?.championId ?: prefs.favoriteChampionId
        val heroId = training?.heroId ?: prefs.favoriteHeroId
        val championIndex = getSelectedItemIndex(champions, championId)
        val heroIndex = getSelectedItemIndex(heroes, heroId)
        view.showChampions(getSpinnerStrings(champions), championIndex)
        view.showHeroes(getSpinnerStrings(heroes), heroIndex)

        if (trainingId == null) {
            view.showScreenTitle(true)
            view.showTrainingDate(AppUtils.getRoundDate())
            view.showDeleteTrainingBtn(false)
            updateTrainingData(championIndex, heroIndex, null)
        } else {
            val exercises = exercises
            val exercisesInTraining = exercisesInTraining
            if (exercisesInTraining != null && exercises != null)
                for (nextTraining in exercisesInTraining)
                    for (nextExercise in exercises)
                        if (nextTraining.exerciseId == nextExercise.id)
                            nextTraining.exercise = nextExercise
            training?.exercises = exercisesInTraining
            view.showExercises(training!!.exercises)

            view.showScreenTitle(false)
            view.showTrainingDate(training.getDateCal())
            view.showDeleteTrainingBtn(true)
        }

        Logger.d(LOG_TAG, "publishDataToView. " +
                "training=$training, " +
                "champions size=${champions?.size}, " +
                "heroes size=${heroes?.size}")
    }

    private fun isOnlyOrderOfListChanged(oldList: List<ExerciseInTraining>?, newList: List<ExerciseInTraining>?): Boolean {
        when {
            oldList == null || newList == null -> return false
            oldList.size != newList.size -> return false
            else -> {
                for (nextOld in oldList) {
                    var found = false
                    for (nextNew in newList) if (nextNew.id == nextOld.id) found = true
                    if (!found) return false
                }
                return true
            }
        }
    }

    private fun getNextExercisePosition(): Int {
        val exercises = training?.exercises
        return if (exercises == null || exercises.isEmpty()) 0
        else {
            var maxPosition = 0
            for (next in exercises) if (next.position > maxPosition) maxPosition = next.position
            maxPosition + 1
        }
    }
    //</editor-fold>


    companion object {
        private const val LOG_TAG = "TrainingDetailsPresenterImpl"
    }
}