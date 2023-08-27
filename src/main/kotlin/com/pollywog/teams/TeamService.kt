package com.pollywog.teams

import com.pollywog.common.Repository

class TeamService (
    private val teamRepository: Repository<Team>
) {
    suspend fun getTeam(id: String) = teamRepository.get(id)
}