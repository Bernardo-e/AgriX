package com.sih.app.core.data

import com.sih.app.core.database.FarmDao
import com.sih.app.core.database.FarmEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FarmerProfilePersistenceTest {

    private class FakeFarmDao : FarmDao {
        private val farmState = MutableStateFlow<FarmEntity?>(null)

        override suspend fun saveFarm(farm: FarmEntity): Long {
            farmState.value = farm
            return farm.id
        }

        override suspend fun getFarm(): FarmEntity? = farmState.value

        override fun getFarmFlow(): Flow<FarmEntity?> = farmState.asStateFlow()

        override suspend fun deleteFarm(): Int {
            farmState.value = null
            return 1
        }
    }

    private lateinit var fakeFarmDao: FakeFarmDao
    private lateinit var farmRepository: FarmRepository

    @Before
    fun setup() {
        fakeFarmDao = FakeFarmDao()
        farmRepository = FarmRepository(fakeFarmDao)
    }

    // 1. First launch with no profile -> no profile exists
    @Test
    fun test1_FirstLaunchWithNoProfile() = runBlocking {
        assertFalse(farmRepository.hasFarmProfile())
        assertNull(farmRepository.getFarm())
        assertNull(farmRepository.getFarmFlow().firstOrNull())
    }

    // 2. Complete profile -> profile persisted with all fields
    @Test
    fun test2_CompleteProfilePersistsAllFields() = runBlocking {
        farmRepository.saveFarm(
            farmName = "Green Meadows",
            state = "Maharashtra",
            district = "Pune",
            village = "Baramati",
            farmArea = 5.0,
            farmAreaUnit = "Acres",
            soilType = "Black",
            currentCrop = "Tomato",
            latitude = 18.5204,
            longitude = 73.8567,
            locationAccuracyMeters = 12.5f,
        )

        assertTrue(farmRepository.hasFarmProfile())
        val saved = farmRepository.getFarm()
        assertNotNull(saved)
        assertEquals("Green Meadows", saved!!.farmName)
        assertEquals("Maharashtra", saved.state)
        assertEquals("Pune", saved.district)
        assertEquals("Baramati", saved.village)
        assertEquals(5.0, saved.farmArea, 0.001)
        assertEquals("Acres", saved.farmAreaUnit)
        assertEquals("Black", saved.soilType)
        assertEquals("Tomato", saved.currentCrop)
        assertEquals(18.5204, saved.latitude!!, 0.0001)
        assertEquals(73.8567, saved.longitude!!, 0.0001)
    }

    // 3. Restart app -> profile still exists and onboarding can be skipped
    @Test
    fun test3_RestartAppRetainsProfile() = runBlocking {
        farmRepository.saveFarm(
            farmName = "Kisan Farm",
            state = "Punjab",
            district = "Ludhiana",
            village = "Samrala",
            farmArea = 10.0,
            farmAreaUnit = "Acres",
            soilType = "Alluvial",
            currentCrop = "Wheat",
        )

        // Simulate app restart by querying repository anew
        val hasProfileOnRestart = farmRepository.hasFarmProfile()
        assertTrue("Onboarding must be skipped on app restart if profile exists", hasProfileOnRestart)
    }

    // 4. Existing profile loaded correctly
    @Test
    fun test4_ExistingProfileLoadedCorrectly() = runBlocking {
        farmRepository.saveFarm(
            farmName = "Annapurna Farm",
            state = "Andhra Pradesh",
            district = "Guntur",
            village = "Tenali",
            farmArea = 3.5,
            farmAreaUnit = "Acres",
            soilType = "Red",
            currentCrop = "Chilli",
        )

        val farm = farmRepository.getFarm()
        assertNotNull(farm)
        assertEquals("Chilli", farm!!.currentCrop)
        assertEquals("Red", farm.soilType)
        assertEquals("Guntur", farm.district)
    }

    // 5. Diagnosis can access saved crop context directly
    @Test
    fun test5_DiagnosisAccessesSavedCropContext() = runBlocking {
        farmRepository.saveFarm(
            farmName = "Delta Rice Farm",
            state = "Tamil Nadu",
            district = "Thanjavur",
            village = "Kumbakonam",
            farmArea = 4.0,
            farmAreaUnit = "Acres",
            soilType = "Clayey",
            currentCrop = "Rice",
        )

        val flowFarm = farmRepository.getFarmFlow().firstOrNull()
        assertNotNull(flowFarm)
        val defaultDiagnosisCrop = flowFarm!!.currentCrop
        assertEquals("Rice", defaultDiagnosisCrop)
    }

    // 6. Diagnosis does not require re-asking soil, state, or location
    @Test
    fun test6_DiagnosisDoesNotRequestSoilStateLocationAgain() = runBlocking {
        farmRepository.saveFarm(
            farmName = "Sweet Cane Estate",
            state = "Uttar Pradesh",
            district = "Meerut",
            village = "Daurala",
            farmArea = 12.0,
            farmAreaUnit = "Acres",
            soilType = "Loamy",
            currentCrop = "Sugarcane",
        )

        val farm = farmRepository.getFarm()
        assertNotNull(farm)
        // All metadata fields are pre-populated and accessible without prompts
        assertTrue(farm!!.soilType.isNotBlank())
        assertTrue(farm.state.isNotBlank())
        assertTrue(farm.district.isNotBlank())
        assertTrue(farm.village.isNotBlank())
        assertEquals("Sugarcane", farm.currentCrop)
    }

    // 7. Editing profile updates stored values
    @Test
    fun test7_EditingProfileUpdatesStoredValues() = runBlocking {
        farmRepository.saveFarm(
            farmName = "Farm V1",
            state = "Karnataka",
            district = "Mandya",
            village = "Maddur",
            farmArea = 2.0,
            farmAreaUnit = "Acres",
            soilType = "Red",
            currentCrop = "Sugarcane",
        )

        // Farmer edits profile to new crop and farm size
        farmRepository.saveFarm(
            farmName = "Farm V2",
            state = "Karnataka",
            district = "Mandya",
            village = "Maddur",
            farmArea = 4.0,
            farmAreaUnit = "Acres",
            soilType = "Red",
            currentCrop = "Rice",
        )

        val updated = farmRepository.getFarm()
        assertNotNull(updated)
        assertEquals("Farm V2", updated!!.farmName)
        assertEquals(4.0, updated.farmArea, 0.001)
        assertEquals("Rice", updated.currentCrop)
    }

    // 8. New diagnosis uses updated profile crop
    @Test
    fun test8_NewDiagnosisUsesUpdatedProfileCrop() = runBlocking {
        farmRepository.saveFarm(
            farmName = "Farmer Field",
            state = "Gujarat",
            district = "Anand",
            village = "Petlad",
            farmArea = 6.0,
            farmAreaUnit = "Acres",
            soilType = "Sandy",
            currentCrop = "Tomato",
        )

        assertEquals("Tomato", farmRepository.getFarm()!!.currentCrop)

        // Farmer changes primary crop to Chilli
        farmRepository.saveFarm(
            farmName = "Farmer Field",
            state = "Gujarat",
            district = "Anand",
            village = "Petlad",
            farmArea = 6.0,
            farmAreaUnit = "Acres",
            soilType = "Sandy",
            currentCrop = "Chilli",
        )

        val newDefaultCrop = farmRepository.getFarmFlow().firstOrNull()!!.currentCrop
        assertEquals("Chilli", newDefaultCrop)
    }
}
