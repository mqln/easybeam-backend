package com.pollywog.teams

interface TeamRepoIdProvider {
    fun id(teamId: String): String
}

class FirestoreTeamRepoIdProvider: TeamRepoIdProvider {
    override fun id(teamId: String): String = "teams/$teamId"
}