package com.pollywog.pipelines

interface PipelineIdProvider {
    fun id(teamId: String, pipelineId: String): String
}

class FirestorePipelineIdProvider: PipelineIdProvider {
    override fun id(teamId: String, pipelineId: String) = "teams/$teamId/pipelines/$pipelineId"
}

class RedisPipelineIdProvider: PipelineIdProvider {
    override fun id(teamId: String, pipelineId: String) = "teams:$teamId:pipelines:$pipelineId"
}