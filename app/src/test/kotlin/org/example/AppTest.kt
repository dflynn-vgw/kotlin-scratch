package org.example

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
    lateinit var workerService: WorkerService

    @Test
    fun `worker service bean is created and runs successfully`(output: CapturedOutput) {
        // Verify WorkerService bean is created
        assertNotNull(workerService, "WorkerService should be autowired")

        // Verify expected log messages were written during application startup
        val logOutput = output.toString()
        assertTrue(
            logOutput.contains("Worker service started"),
            "Should log 'Worker service started'",
        )
        assertTrue(
            logOutput.contains("Worker service initialization complete"),
            "Should log 'Worker service initialization complete'",
        )
    }
}
