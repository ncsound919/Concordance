package com.example

import com.example.concordance.integration.rotation.ApiRotationManager
import com.example.concordance.integration.rotation.ProviderCategory
import com.example.concordance.integration.rotation.RotationStrategy
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiRotationManagerTest {
    @Test
    fun exercise() {
        ApiRotationManager.setStrategy(RotationStrategy.ROUND_ROBIN)
        ApiRotationManager.setFailureThreshold(2)
        ApiRotationManager.setCooldownMs(1000)
        val p = ApiRotationManager.getNextProvider(ProviderCategory.LITERATURE)
        ApiRotationManager.recordSuccess(p.id, 50)
        ApiRotationManager.recordFailure(p.id, false)
        ApiRotationManager.toggleProviderActive(p.id)
        ApiRotationManager.cooldownRemainingMs(p.id)
        ApiRotationManager.resetStats()
        assertTrue(ApiRotationManager.providers.value.isNotEmpty())
    }
}
