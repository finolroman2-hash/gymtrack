package com.example.gymtrack.backup

import com.example.gymtrack.data.local.dao.GymDao
import com.example.gymtrack.data.local.entities.ProfileEntity
import kotlinx.coroutines.flow.first

object ProfileRecovery {

    private suspend fun hasAccountData(dao: GymDao, userName: String): Boolean =
        dao.countSessionsForUser(userName) > 0 ||
            dao.countExercisesForUser(userName) > 0 ||
            dao.countMacrocyclesForUser(userName) > 0 ||
            dao.countForecastForUser(userName) > 0 ||
            dao.countTrainingCyclesForUser(userName) > 0

    /**
     * Registra perfiles cuyos datos siguen en la BD local pero ya no están en `profiles`.
     * Si solo quedan restos sin datos útiles, los purga en lugar de recrear la cuenta.
     */
    suspend fun syncMissingProfiles(dao: GymDao): List<String> {
        val existing = dao.observeProfiles().first().map { it.name }.toSet()
        val recovered = mutableListOf<String>()
        for (name in dao.listAllKnownUserNames()) {
            if (name.isBlank() || name in existing) continue
            if (!hasAccountData(dao, name)) {
                dao.purgeOrphanUserData(name)
                continue
            }
            dao.insertProfile(ProfileEntity(name = name))
            recovered.add(name)
        }
        return recovered
    }
}
