package com.pollywog.common

import org.slf4j.LoggerFactory

interface Loggable {
    val logger: org.slf4j.Logger get() = LoggerFactory.getLogger(javaClass)
}