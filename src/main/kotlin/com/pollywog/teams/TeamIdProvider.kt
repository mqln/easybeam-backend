package com.pollywog.teams

interface TeamIdProvider {
    fun id(teamId: String): String
}

class FirestoreTeamIdProvider: TeamIdProvider {
    override fun id(teamId: String): String = "teams/$teamId"
}

class RedisTeamIdProvider: TeamIdProvider {
    override fun id(teamId: String): String = "teams:$teamId"
}

class FirestoreTeamSubscriptionIdProvider: TeamIdProvider {
    override fun id(teamId: String): String = "teamSubscriptions/$teamId"
}

class RedisTeamSubscriptionIdProvider: TeamIdProvider {
    override fun id(teamId: String): String = "teamSubscriptions:$teamId"
}

class FirestoreTeamSecretsIdProvider: TeamIdProvider {
    override fun id(teamId: String): String = "teamSecrets/$teamId"
}

class RedisTeamSecretsIdProvider: TeamIdProvider {
    override fun id(teamId: String): String = "teamSecrets:$teamId"
}