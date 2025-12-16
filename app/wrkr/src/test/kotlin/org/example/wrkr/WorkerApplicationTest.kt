package org.example.wrkr

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import kotlin.test.assertNotNull

@SpringBootTest
class WorkerApplicationTest {

    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Test
    fun `context loads successfully`() {
        assertNotNull(applicationContext)
    }

    @Test
    fun `greeting task bean is created`() {
        val greetingTask = applicationContext.getBean(GreetingTask::class.java)
        assertNotNull(greetingTask)
    }
}
