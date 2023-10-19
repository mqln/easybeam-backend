package com.pollywog.teams

interface InviteIdProviding {
    fun id(teamId: String, inviteId: String): String
}

class FirestoreInviteIdProvider : InviteIdProviding {
    override fun id(teamId: String, inviteId: String): String = "teams/${teamId}/invites/${inviteId}"
}