package com.netsage.ai.domain

import com.netsage.ai.domain.ml.MlQualityClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MlQualityClassifierTest {

    private lateinit var classifier: MlQualityClassifier

    @Before
    fun setUp() {
        classifier = MlQualityClassifier()
    }

    @Test
    fun predict_whenHighPerformanceFeatures_predictsExcellent() {
        // [0: RTT=20ms (0.04), 1: DNS=12ms (0.06), 2: Sig=-52dBm (0.85), 3: Bars=4 (1.0), 4: Downlink (0.6), 5: NetType=0.2, 6: Stab=95% (0.95), 7: Metered=0.0]
        val goodFeatures = floatArrayOf(0.04f, 0.06f, 0.85f, 1.0f, 0.6f, 0.2f, 0.95f, 0.0f)

        val result = classifier.predict(goodFeatures)

        assertEquals("EXCELLENT", result.predictedLabel)
        assertEquals(2, result.predictedClass)
        assertTrue(result.confidence >= 0.60f)
        assertTrue("Inference latency should be positive, got ${result.inferenceLatencyUs}", result.inferenceLatencyUs >= 0)
    }

    @Test
    fun predict_whenDegradedLink_predictsPoor() {
        // [0: RTT=380ms (0.76), 1: DNS=160ms (0.80), 2: Sig=-114dBm (0.075), 3: Bars=1 (0.25), 4: Downlink (0.01), 5: NetType=0.6, 6: Stab=30% (0.3), 7: Metered=1.0]
        val poorFeatures = floatArrayOf(0.76f, 0.80f, 0.075f, 0.25f, 0.01f, 0.6f, 0.30f, 1.0f)

        val result = classifier.predict(poorFeatures)

        assertEquals("POOR", result.predictedLabel)
        assertEquals(0, result.predictedClass)
        assertTrue(result.confidence >= 0.70f)
    }
}

