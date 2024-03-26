package com.pollywog.prompts

import com.stripe.model.UsageRecord
import com.stripe.net.RequestOptions
import com.stripe.param.UsageRecordCreateOnSubscriptionItemParams

class StripeUsageReporter(private val apiKey: String) : UsageReporter {

    override fun reportUsage(subscriptionItemId: String, overage: Long) {
        val currentTimestamp = System.currentTimeMillis() / 1000
        val params = UsageRecordCreateOnSubscriptionItemParams.builder().setQuantity(overage).setTimestamp(currentTimestamp)
            .setAction(UsageRecordCreateOnSubscriptionItemParams.Action.INCREMENT).build()
        val requestOptions = RequestOptions.builder().setApiKey(apiKey).build()

        UsageRecord.createOnSubscriptionItem(subscriptionItemId, params, requestOptions)
    }
}
