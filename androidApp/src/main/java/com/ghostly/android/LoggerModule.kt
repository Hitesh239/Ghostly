package com.ghostly.android

import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.Logger
import org.koin.dsl.module

val loggerModule = module {
    single<Logger> { Logger.DEFAULT }
}