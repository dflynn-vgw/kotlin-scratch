package org.example.intg

import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

/**
 * R2DBC configuration for reactive database access.
 * Connection properties are configured via application.yml in the application modules.
 */
@Configuration
@EnableR2dbcRepositories
class R2dbcConfig
