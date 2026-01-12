package org.example

import org.example.spsc.SpscWorkerService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import kotlin.test.assertTrue

@SpringBootTest
@ExtendWith(OutputCaptureExtension::class)
class AppTest {

    @Autowired
    lateinit var spscWorkerService: SpscWorkerService

    @Test
    fun `Spsc worker service bean is created and runs successfully`(output: CapturedOutput) {
        // Verify WorkerService bean is created
        assertNotNull(spscWorkerService, "SpscWorkerService should be autowired")

        // Verify expected log messages were written during application startup
        val logOutput = output.toString()
        assertTrue(
            logOutput.contains("Starting SPSC event processing..."),
            "Should log 'Worker service started'",
        )
        assertTrue(
            logOutput.contains("SPSC coordinator started successfully"),
            "Should log 'Worker service initialization complete'",
        )
    }
}
