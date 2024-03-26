package com.pollywog.prompts

interface UsageReporter {
    fun reportUsage(subscriptionItemId: String, overage: Long)
}